package com.gymmanagement.service;

import com.gymmanagement.exception.AiServiceException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * GeminiClient — the one class in this project that talks to Google's
 * Gemini API. Everything else (a future DocumentService, a future
 * BootcampRecommendationService) will depend on THIS class, not on
 * Gemini's REST API directly — same reasoning as why SqliteMemberRepository
 * is the only class touching raw JDBC. If Gemini's request format changes,
 * or the project ever swaps providers, this is the one place that changes.
 *
 * Uses Spring's RestClient (introduced in Spring Framework 6.1, bundled
 * with Spring Boot 3.2.5 — no new dependency needed in pom.xml at all,
 * unlike everything else we've added this project).
 *
 * API key follows the exact same pattern as DATABASE_URL: read from an
 * environment variable, fail loudly and immediately at startup if it's
 * missing, never hardcoded anywhere in source.
 *
 * GeminiRateLimiter.checkAllowed() is called as the FIRST line of ask(),
 * before the request is even built — if the limit's been hit, this
 * method returns without ever making an HTTP call, so no quota is spent
 * on requests that get rejected.
 */
@Service
public class GeminiClient implements AiChatClient {

    /**
     * Model name is deliberately NOT hardcoded. Multiple current reports
     * (Google's own developer forums, checked while debugging this exact
     * error) confirm Gemini models are being deprecated earlier than their
     * documented shutdown dates — one report described a model that had
     * worked fine for months suddenly returning 404 overnight, with no
     * changelog entry. That's not a one-time mistake to patch around,
     * it's ongoing instability in this specific area.
     *
     * GEMINI_MODEL is optional. If unset, falls back to "gemini-3-flash"
     * — the most consistently reported current default at time of writing.
     * If Google deprecates that too, fix it with one export command,
     * no code change, no redeploy needed:
     *
     *   export GEMINI_MODEL='whatever-model-currently-works'
     */
    private static final String DEFAULT_MODEL = "gemini-3-flash";
    private final String apiUrl;

    private final RestClient        restClient;
    private final String            apiKey;
    private final GeminiRateLimiter rateLimiter;

    public GeminiClient(GeminiRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "GEMINI_API_KEY environment variable is not set. " +
                "Get a free key at aistudio.google.com and export it before running.");
        }

        String model = System.getenv("GEMINI_MODEL");
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }
        this.apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
        System.out.println("[AI] Using Gemini model: " + model);

        this.restClient = RestClient.create();
    }

    /**
     * Sends a single prompt to Gemini and returns its text response.
     *
     * Deliberately takes and returns plain String today — no document
     * context, no chunking, no system instructions yet. This exists to
     * prove the connection itself works end to end before any of that
     * gets built on top of it.
     */
    public String ask(String prompt) {
        rateLimiter.checkAllowed();   // throws BEFORE any network call if over limit

        GeminiRequest request = new GeminiRequest(
            List.of(new GeminiContent("user", List.of(new GeminiPart(prompt))))
        );

        try {
            GeminiResponse response = restClient.post()
                .uri(apiUrl)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);

            if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
                throw new AiServiceException("Gemini returned an empty response.");
            }

            return response.candidates().get(0).content().parts().get(0).text();

        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            // Covers network failures, non-2xx responses, malformed JSON —
            // anything that means "the call to Gemini itself didn't work,"
            // wrapped in one project-specific exception type rather than
            // leaking Spring's internal HTTP client exceptions upward.
            //
            // Note what this catch block deliberately does NOT do: retry.
            // There is no automatic retry anywhere in this class. A failed
            // call fails once and stops — retrying automatically is exactly
            // the pattern that turns one bug into a burst of requests.
            //
            // The full raw detail (e.getMessage()) still goes to the server
            // log for real debugging — it just never reaches the end user
            // anymore. Gemini's own error bodies come through as raw JSON
            // with literal <EOL> tokens (a WebClient convention for
            // embedding a multi-line body in an exception message), which
            // is not something anyone using this app should ever have to
            // read. A transient overload (503/UNAVAILABLE — the actual
            // case that surfaced this) gets a clean, specific message;
            // anything else gets an honest, generic one.
            System.err.println("[AI] Gemini call failed: " + e.getMessage());

            String detail = e.getMessage() != null ? e.getMessage() : "";
            if (detail.contains("503") || detail.contains("UNAVAILABLE")) {
                throw new AiServiceException(
                    "The AI assistant is temporarily overloaded. Please try again in a moment.");
            }
            throw new AiServiceException(
                "Could not reach the AI assistant right now. Please try again.");
        }
    }

    // ── Gemini's JSON shape, as typed records ──────────────
    // Nested here (not in model/) because these describe Gemini's API
    // contract specifically, not a domain concept of this application —
    // same reasoning as MemberController's request DTOs staying local
    // to the controller that uses them.

    private record GeminiPart(String text) {}
    private record GeminiContent(String role, List<GeminiPart> parts) {}
    private record GeminiRequest(List<GeminiContent> contents) {}

    private record GeminiResponseContent(List<GeminiPart> parts) {}
    private record GeminiCandidate(GeminiResponseContent content) {}
    private record GeminiResponse(List<GeminiCandidate> candidates) {}
}