package com.gymmanagement.service;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * ToolChatModel — "one turn of a conversation with a model that is
 * allowed to ask for tools", as an interface.
 *
 * This is the seam that keeps AdminAssistantService testable. The
 * orchestration loop (ask → model requests a tool → run it → hand the
 * result back → model answers) is real logic worth testing, but it sits
 * on top of an HTTP call to Gemini. Hiding that call behind this
 * interface — the same move AiChatClient and AiEmbeddingClient already
 * make for the other AI features — means the loop can be driven in a
 * test by a scripted fake with no network and no API key.
 * GeminiToolChatModel is the one real implementation.
 *
 * The nested records mirror Gemini's own conversation shape (turns made
 * of parts; a part is text, a function call, or a function response).
 * They carry Jackson's NON_NULL rule because a part has exactly one of
 * those populated and Gemini rejects explicit nulls for the rest.
 */
public interface ToolChatModel {

    /**
     * Sends the whole conversation so far and returns the model's next
     * turn — either final text, or one or more function calls it wants
     * answered. Implementations count against the shared AI rate limit
     * and translate provider failures into AiServiceException.
     *
     * @param tools the functions the model may call this turn; empty
     *              means "answer in text, no tools available"
     */
    Turn generate(String systemInstruction, List<Turn> conversation, List<ToolSpec> tools);

    /** One function the model is allowed to call. {@code parameters} is a JSON-Schema-style object. */
    record ToolSpec(String name, String description, Map<String, Object> parameters) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FunctionCall(String name, Map<String, Object> args, String id) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FunctionResponse(String name, Map<String, Object> response, String id) {}

    /**
     * thoughtSignature is an opaque token Gemini 3 "thinking" models
     * attach to a function-call part. It must be echoed back unchanged
     * in the follow-up request or the call is rejected — the same
     * requirement BootcampToolCallingService documents. Keeping the
     * whole received Turn and re-sending it as-is satisfies that
     * without this code ever interpreting the token.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Part(String text, FunctionCall functionCall, FunctionResponse functionResponse, String thoughtSignature) {

        public static Part ofText(String text) {
            return new Part(text, null, null, null);
        }

        public static Part ofFunctionResponse(FunctionCall call, Map<String, Object> result) {
            return new Part(null, null, new FunctionResponse(call.name(), result, call.id()), null);
        }
    }

    record Turn(String role, List<Part> parts) {

        public static Turn user(String text) {
            return new Turn("user", List.of(Part.ofText(text)));
        }
    }
}
