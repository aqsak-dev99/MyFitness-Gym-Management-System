package com.gymmanagement.service;

import com.gymmanagement.exception.AiServiceException;
import com.gymmanagement.service.ToolChatModel.ToolSpec;
import com.gymmanagement.service.ToolChatModel.Turn;
import com.gymmanagement.service.ToolChatModel.Part;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * GeminiToolChatModel — the HTTP half of the Admin AI: one call to
 * Gemini's generateContent endpoint, with tool declarations and a system
 * instruction attached. It contains no loop and no business logic; it
 * sends a conversation and returns the model's next turn.
 * AdminAssistantService owns the loop.
 *
 * Follows GeminiClient and BootcampToolCallingService point for point:
 *   - same classic generateContent endpoint (one integration pattern for
 *     the whole project, not two);
 *   - same GEMINI_API_KEY / GEMINI_MODEL environment variables, failing
 *     loudly at startup if the key is missing;
 *   - GeminiRateLimiter.checkAllowed() before EVERY HTTP call, so a
 *     two- or three-step tool conversation spends real quota honestly;
 *   - no retries;
 *   - raw provider errors are logged for debugging and replaced with a
 *     short, clean message — a 503 never reaches the admin as JSON.
 */
@Service
public class GeminiToolChatModel implements ToolChatModel {

    private static final String DEFAULT_MODEL = "gemini-3.5-flash";

    private final String            apiKey;
    private final String            apiUrl;
    private final RestClient        restClient;
    private final GeminiRateLimiter rateLimiter;

    public GeminiToolChatModel(GeminiRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;

        this.apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "GEMINI_API_KEY environment variable is not set. " +
                "Get a free key at aistudio.google.com and export it before running.");
        }

        String configured = System.getenv("GEMINI_MODEL");
        String model = (configured == null || configured.isBlank()) ? DEFAULT_MODEL : configured;
        this.apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
        this.restClient = RestClient.create();
    }

    @Override
    public Turn generate(String systemInstruction, List<Turn> conversation, List<ToolSpec> tools) {
        rateLimiter.checkAllowed();   // throws BEFORE any network call if over the limit

        WireRequest request = new WireRequest(
            new WireSystemInstruction(List.of(Part.ofText(systemInstruction))),
            conversation,
            tools.isEmpty() ? null : List.of(new WireTool(tools.stream()
                .map(t -> new WireDeclaration(t.name(), t.description(), t.parameters()))
                .toList())));

        try {
            WireResponse response = restClient.post()
                .uri(apiUrl)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(WireResponse.class);

            if (response == null || response.candidates() == null || response.candidates().isEmpty()
                    || response.candidates().get(0).content() == null
                    || response.candidates().get(0).content().parts() == null
                    || response.candidates().get(0).content().parts().isEmpty()) {
                throw new AiServiceException("Gemini returned an empty response.");
            }
            return response.candidates().get(0).content();

        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            // Same handling as GeminiClient / BootcampToolCallingService: full
            // detail to the server log, a clean message to the user.
            System.err.println("[AI] Gemini admin tool-calling request failed: " + e.getMessage());

            String detail = e.getMessage() != null ? e.getMessage() : "";
            if (detail.contains("503") || detail.contains("UNAVAILABLE")) {
                throw new AiServiceException(
                    "The AI assistant is temporarily overloaded. Please try again in a moment.");
            }
            throw new AiServiceException(
                "Could not reach the AI assistant right now. Please try again.");
        }
    }

    // ── Gemini's request/response JSON, as typed records ──
    // Conversation turns reuse ToolChatModel's records directly; only the
    // envelope around them is Gemini-specific, so it stays private here.

    private record WireDeclaration(String name, String description, java.util.Map<String, Object> parameters) {}
    private record WireTool(List<WireDeclaration> functionDeclarations) {}
    private record WireSystemInstruction(List<Part> parts) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record WireRequest(WireSystemInstruction systemInstruction, List<Turn> contents, List<WireTool> tools) {}

    private record WireCandidate(Turn content) {}
    private record WireResponse(List<WireCandidate> candidates) {}
}
