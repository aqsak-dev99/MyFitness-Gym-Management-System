package com.gymmanagement.controller;

import com.gymmanagement.config.RequireOwnership;
import com.gymmanagement.service.AiChatClient;
import com.gymmanagement.service.BootcampRecommendationService;
import com.gymmanagement.service.BootcampToolCallingClient;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

/**
 * AiController — every general-purpose (non-document) AI endpoint lives
 * here. /ask proved the Gemini connection itself works; the bootcamp
 * recommendation endpoint reuses that same foundation with manually-
 * gathered context; /bootcamp-classes/ask is the new one — the model
 * decides for itself when it needs real data, via an actual tool call,
 * rather than everything being pre-stuffed into the prompt.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiChatClient                  geminiClient;
    private final BootcampRecommendationService  recommendationService;
    private final BootcampToolCallingClient      toolCallingService;

    public AiController(AiChatClient geminiClient,
                        BootcampRecommendationService recommendationService,
                        BootcampToolCallingClient toolCallingService) {
        this.geminiClient          = geminiClient;
        this.recommendationService = recommendationService;
        this.toolCallingService    = toolCallingService;
    }

    /**
     * Real, concrete gap found during Member QA: with no context at all,
     * a question like "how do I get a refund and contact my trainer"
     * got answered with generic advice about Udemy/Coursera refund
     * policies — Gemini has no way to know it's answering inside a gym
     * app unless it's told. This prefix is the fix: a short instruction
     * naming the actual app and asking it to say so plainly when a
     * question is outside that scope, rather than confidently answering
     * as if it were a different kind of product. Deliberately small —
     * this is General mode specifically; Documents and Bootcamp Classes
     * modes already ground their answers in real data (RAG / tool
     * calling), so they don't need this at all.
     */
    private static final String GENERAL_MODE_CONTEXT =
        "You are the AI assistant inside MyFitness, a gym management app. " +
        "Answer as if speaking to a member or admin of this specific gym — " +
        "not a generic assistant. If a question is clearly about something " +
        "unrelated to a gym (e.g. online courses, unrelated software), say " +
        "plainly that you're MyFitness's assistant and can't help with that, " +
        "rather than answering as if you were a different kind of app.\n\n" +
        "Question: ";

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        String answer = geminiClient.ask(GENERAL_MODE_CONTEXT + request.question());
        return new AskResponse(answer);
    }

    /**
     * @RequireOwnership("memberId") — a member can only get their own
     * recommendation; ADMIN can look up anyone's.
     */
    @GetMapping("/members/{memberId}/bootcamp-recommendation")
    @RequireOwnership("memberId")
    public RecommendationResponse getBootcampRecommendation(@PathVariable String memberId) {
        String recommendation = recommendationService.recommendBootcamp(memberId);
        return new RecommendationResponse(recommendation);
    }

    /**
     * The new tool-calling endpoint. No ownership/role restriction —
     * this only reads publicly-visible bootcamp class data, the same
     * information GET /api/bootcamp-classes already exposes to any
     * authenticated user; the difference here is HOW the answer gets
     * built, not what data is accessible.
     */
    @PostMapping("/bootcamp-classes/ask")
    public AskResponse askAboutBootcampClasses(@Valid @RequestBody AskRequest request) {
        String answer = toolCallingService.askAboutBootcampClasses(request.question());
        return new AskResponse(answer);
    }

    /**
     * @NotBlank here isn't just API hygiene — a blank question would
     * still consume one of the shared 5-requests-per-minute Gemini
     * quota slots for a call that could never produce anything useful.
     * Rejecting it before it reaches GeminiClient protects that budget.
     */
    public record AskRequest(@NotBlank String question) {}
    public record AskResponse(String answer) {}
    public record RecommendationResponse(String recommendation) {}
}