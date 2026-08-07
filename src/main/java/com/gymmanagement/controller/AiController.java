package com.gymmanagement.controller;

import com.gymmanagement.service.BootcampRecommendationService;
import com.gymmanagement.service.GeminiClient;

import org.springframework.web.bind.annotation.*;

/**
 * AiController — every general-purpose (non-document) AI endpoint lives
 * here. /ask proved the Gemini connection itself works; the bootcamp
 * recommendation endpoint is the final feature on the AI roadmap, built
 * on that same proven foundation.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final GeminiClient                 geminiClient;
    private final BootcampRecommendationService recommendationService;

    public AiController(GeminiClient geminiClient,
                        BootcampRecommendationService recommendationService) {
        this.geminiClient          = geminiClient;
        this.recommendationService = recommendationService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest request) {
        String answer = geminiClient.ask(request.question());
        return new AskResponse(answer);
    }

    /**
     * GET, not POST — this doesn't create or modify anything (a
     * recommendation isn't stored), it only reads existing member/class
     * data and asks Gemini to reason over it. That makes GET the more
     * semantically correct choice, even though it triggers an external
     * API call under the hood.
     */
    @GetMapping("/members/{memberId}/bootcamp-recommendation")
    public RecommendationResponse getBootcampRecommendation(@PathVariable String memberId) {
        String recommendation = recommendationService.recommendBootcamp(memberId);
        return new RecommendationResponse(recommendation);
    }

    public record AskRequest(String question) {}
    public record AskResponse(String answer) {}
    public record RecommendationResponse(String recommendation) {}
}