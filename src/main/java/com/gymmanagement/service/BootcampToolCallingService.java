package com.gymmanagement.service;

import com.gymmanagement.exception.AiServiceException;
import com.gymmanagement.model.BootcampClass;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BootcampToolCallingService — the actual "the model decides what to look
 * up" mechanism, as opposed to every other AI feature in this project,
 * which gathers data manually and stuffs it all into one prompt whether
 * the model needs it or not (see BootcampRecommendationService's own
 * comments on this exact distinction).
 *
 * Deliberately narrow scope: ONE tool (listBootcampClasses, no
 * arguments), ONE possible round-trip (ask → maybe a tool call → tool
 * result → final answer). Not a general tool-calling framework, not an
 * open-ended agentic loop — a real, working, honestly-bounded first
 * slice. BootcampRecommendationService itself is untouched; this is a
 * new, additive feature living alongside it, not a replacement.
 *
 * Uses the same classic generateContent endpoint GeminiClient already
 * calls, not the newer Interactions API — one integration pattern for
 * this whole project, not two.
 */
@Service
public class BootcampToolCallingService implements BootcampToolCallingClient {

    private static final String DEFAULT_MODEL = "gemini-3.5-flash";
    private static final String TOOL_NAME     = "listBootcampClasses";

    private final String            apiKey;
    private final String            apiUrl;
    private final RestClient        restClient;
    private final GeminiRateLimiter rateLimiter;
    private final MembershipService membershipService;

    public BootcampToolCallingService(GeminiRateLimiter rateLimiter, MembershipService membershipService) {
        this.rateLimiter       = rateLimiter;
        this.membershipService = membershipService;

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

    /**
     * Answers a question about bootcamp classes. The model decides
     * whether it needs real, live data — if it does, it calls the
     * listBootcampClasses tool; we execute it, hand the real result
     * back, and get a final, grounded answer. If the model can already
     * answer without the tool, it just does — no wasted round-trip.
     */
    public String askAboutBootcampClasses(String question) {
        ToolContent userMessage = new ToolContent("user", List.of(ToolPart.ofText(question)));

        ToolCandidate firstResponse = callGemini(List.of(userMessage));
        ToolPart firstPart = firstPartOf(firstResponse);

        if (firstPart.functionCall() == null) {
            // Model answered directly — no tool needed for this question.
            return firstPart.text();
        }

        // Model wants the tool. Execute it for real, using the exact
        // same MembershipService every other feature in this project
        // already depends on — no new data path, just a new caller.
        Map<String, Object> toolResult = executeListBootcampClasses();

        ToolContent modelCallMessage = new ToolContent("model", List.of(firstPart));
        ToolContent toolResultMessage = new ToolContent(
            "user", List.of(ToolPart.ofFunctionResponse(TOOL_NAME, toolResult)));

        ToolCandidate finalResponse = callGemini(List.of(userMessage, modelCallMessage, toolResultMessage));
        ToolPart finalPart = firstPartOf(finalResponse);

        if (finalPart.text() == null) {
            throw new AiServiceException("Gemini did not return a final text answer after the tool call.");
        }
        return finalPart.text();
    }

    private ToolCandidate callGemini(List<ToolContent> contents) {
        rateLimiter.checkAllowed();   // real quota — every actual HTTP call counts, not just the first

        ToolRequest request = new ToolRequest(contents, List.of(buildTool()));

        try {
            ToolGenerateResponse response = restClient.post()
                .uri(apiUrl)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ToolGenerateResponse.class);

            if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
                throw new AiServiceException("Gemini returned an empty response.");
            }
            return response.candidates().get(0);

        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            // Same fix as GeminiClient's identical catch block — raw Gemini
            // error bodies (literal JSON, <EOL> tokens) never reached the
            // user before this; now they're logged for real debugging and
            // replaced with a clean, honest message.
            System.err.println("[AI] Gemini tool-calling request failed: " + e.getMessage());

            String detail = e.getMessage() != null ? e.getMessage() : "";
            if (detail.contains("503") || detail.contains("UNAVAILABLE")) {
                throw new AiServiceException(
                    "The AI assistant is temporarily overloaded. Please try again in a moment.");
            }
            throw new AiServiceException(
                "Could not reach the AI assistant right now. Please try again.");
        }
    }

    private ToolPart firstPartOf(ToolCandidate candidate) {
        if (candidate.content() == null || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {
            throw new AiServiceException("Gemini's response had no content parts.");
        }
        return candidate.content().parts().get(0);
    }

    /** The actual function the model can call — real data, not a stub. */
    private Map<String, Object> executeListBootcampClasses() {
        List<BootcampClass> classes = membershipService.getAllBootcampClasses();

        List<Map<String, Object>> summaries = classes.stream()
            .map(bc -> {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("classId", bc.getClassId());
                summary.put("className", bc.getClassName());
                summary.put("schedule", bc.getSchedule());
                summary.put("maxCapacity", bc.getMaxCapacity());
                summary.put("currentEnrolments", bc.getCurrentEnrolments());
                summary.put("full", bc.isFull());
                return summary;
            })
            .toList();

        return Map.of("classes", summaries);
    }

    /** Wraps the single function declaration in the Tool object Gemini's API actually expects. */
    private Tool buildTool() {
        FunctionDeclaration declaration = new FunctionDeclaration(
            TOOL_NAME,
            "Returns the real, current list of bootcamp classes, including each " +
            "class's schedule, maximum capacity, current enrolment count, and " +
            "whether it is full. Call this whenever answering a question that " +
            "needs up-to-date information about specific classes rather than " +
            "general advice.",
            Map.of("type", "object", "properties", Map.of())   // no arguments needed
        );
        return new Tool(List.of(declaration));
    }

    // ── Gemini's tool-calling JSON shape, as typed records ──
    // A separate record set from GeminiClient's plain generateContent
    // records — tool-calling messages can contain a functionCall or
    // functionResponse part, which plain chat never needs.

    private record FunctionDeclaration(String name, String description, Map<String, Object> parameters) {}
    private record Tool(List<FunctionDeclaration> functionDeclarations) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record FunctionCall(String name, Map<String, Object> args) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record FunctionResponse(String name, Map<String, Object> response) {}

    /**
     * A "part" is one of text, functionCall, or functionResponse —
     * never more than one populated per instance. @JsonInclude on this
     * record omits whichever fields are null from the outgoing JSON,
     * rather than sending literal nulls Gemini doesn't expect.
     *
     * thoughtSignature is a genuinely required field for Gemini 3+
     * models, confirmed the hard way — a live 400 error on the first
     * real test run. Gemini 3's "thinking" models attach an encrypted
     * signature to a functionCall part representing their internal
     * reasoning state; that exact signature must be echoed back
     * unchanged in the follow-up request or the call is rejected. This
     * code already re-sends the whole received part object as-is
     * (rather than reconstructing a new one with only functionCall),
     * so adding this one field is the entire fix — Jackson captures it
     * on deserialization and re-sends it automatically.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ToolPart(String text, FunctionCall functionCall,
                            FunctionResponse functionResponse, String thoughtSignature) {
        static ToolPart ofText(String text) {
            return new ToolPart(text, null, null, null);
        }
        static ToolPart ofFunctionResponse(String name, Map<String, Object> result) {
            return new ToolPart(null, null, new FunctionResponse(name, result), null);
        }
    }

    private record ToolContent(String role, List<ToolPart> parts) {}
    private record ToolRequest(List<ToolContent> contents, List<Tool> tools) {}
    private record ToolCandidate(ToolContent content) {}
    private record ToolGenerateResponse(List<ToolCandidate> candidates) {}
}