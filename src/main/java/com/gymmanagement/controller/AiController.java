package com.gymmanagement.controller;

import com.gymmanagement.service.GeminiClient;

import org.springframework.web.bind.annotation.*;

/**
 * AiController — today, one endpoint whose only job is proving the
 * Gemini integration genuinely works end to end. No documents, no
 * chunking, no retrieval yet — those build on top of this once it's
 * confirmed solid, same as MemberController got proven before the
 * other three controllers were built.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final GeminiClient geminiClient;

    public AiController(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest request) {
        String answer = geminiClient.ask(request.question());
        return new AskResponse(answer);
    }

    public record AskRequest(String question) {}
    public record AskResponse(String answer) {}
}