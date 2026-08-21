package com.gymmanagement.controller;

import com.gymmanagement.config.RequireOwnership;
import com.gymmanagement.service.AiChatClient;
import com.gymmanagement.service.BootcampRecommendationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

/**
 * AiController — every general-purpose (non-document) AI endpoint lives
 * here. /ask proved the Gemini connection itself works; the bootcamp
 * recommendation endpoint is the final feature on the AI roadmap, built
 * on that same proven foundation.
 *
 * Depends on AiChatClient (interface), not the concrete GeminiClient —
 * this was actually a loose end from the earlier interface-extraction
 * work (BootcampRecommendationService already used the interface; this
 * class hadn't been updated to match), fixed here as necessary plumbing
 * for testing the ownership check below with a fake, not a live key.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiChatClient                  geminiClient;
    private final BootcampRecommendationService recommendationService;

    public AiController(AiChatClient geminiClient,
                        BootcampRecommendationService recommendationService) {
        this.geminiClient          = geminiClient;
        this.recommendationService = recommendationService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        String answer = geminiClient.ask(request.question());
        return new AskResponse(answer);
    }

    /**
     * GET, not POST — this doesn't create or modify anything (a
     * recommendation isn't stored), it only reads existing member/class
     * data and asks Gemini to reason over it. That makes GET the more
     * semantically correct choice, even though it triggers an external
     * API call under the hood.
     *
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
     * @NotBlank here isn't just API hygiene — a blank question would
     * still consume one of the shared 5-requests-per-minute Gemini
     * quota slots for a call that could never produce anything useful.
     * Rejecting it before it reaches GeminiClient protects that budget.
     */
    public record AskRequest(@NotBlank String question) {}
    public record AskResponse(String answer) {}
    public record RecommendationResponse(String recommendation) {}
}