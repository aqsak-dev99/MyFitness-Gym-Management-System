package com.gymmanagement.controller;

import com.gymmanagement.config.RequireRole;
import com.gymmanagement.model.Role;
import com.gymmanagement.service.AdminAssistantClient;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AdminAiController — the Admin AI's live-data endpoint.
 *
 * Kept as its own controller, rather than a new method on AiController,
 * so the member-facing AI endpoints (and AiController's constructor,
 * which other tests build) stay exactly as they were. Everything here is
 * admin-only by design.
 *
 * The flow behind this one endpoint:
 *   Admin → this controller → AdminAssistantService (model + tool loop)
 *         → AdminAiTools (allow-list) → AdminOperationsService
 *         → MemberService / MembershipService → repositories → PostgreSQL
 *         → structured result → model → plain-language answer.
 */
@RestController
@RequestMapping("/api/ai/admin")
public class AdminAiController {

    private final AdminAssistantClient assistant;

    public AdminAiController(AdminAssistantClient assistant) {
        this.assistant = assistant;
    }

    /**
     * ADMIN only, enforced by RoleAuthorizationInterceptor before this
     * method runs — the same annotation every other admin endpoint uses.
     * A MEMBER token gets 403; a missing token never gets past the JWT
     * filter. The answer uses the same {answer} shape as POST /api/ai/ask
     * so the frontend can treat both alike.
     *
     * The length cap is not cosmetic: the question goes into a model
     * prompt, and every call spends the shared Gemini request budget.
     */
    @PostMapping("/ask")
    @RequireRole(Role.ADMIN)
    public AiController.AskResponse ask(@Valid @RequestBody AdminAskRequest request) {
        return new AiController.AskResponse(assistant.askAdminAssistant(request.question()));
    }

    public record AdminAskRequest(@NotBlank @Size(max = 1000) String question) {}
}
