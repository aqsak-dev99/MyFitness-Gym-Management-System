package com.gymmanagement.controller;

import com.gymmanagement.model.Role;
import com.gymmanagement.model.User;
import com.gymmanagement.service.AuthService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — the HTTP-facing layer for registration and login.
 *
 * Scope worth being explicit about: this makes register/login reachable
 * over HTTP and proves the credentials genuinely work end-to-end (hashing,
 * verification, the identical-error-on-failure behaviour). It does NOT
 * make this a protected API — login returns a User, but there's no
 * session or token yet linking that login to subsequent requests, so
 * nothing currently stops an unauthenticated request from reaching any
 * other endpoint. Wiring requireRole() into an actual request pipeline
 * (a token, a filter checking it on every request) is a separate, bigger
 * feature — real authentication middleware — not something to fold into
 * today's scope.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public User register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(
            request.username(), request.password(), request.role(), request.linkedMemberId()
        );
    }

    @PostMapping("/login")
    public User login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    // ── request DTOs ──────────────────────────────────────

    /**
     * @Size(min = 6) on password is a genuine, deliberate product
     * decision, not just mirroring an existing check — nothing in
     * AuthService currently enforces any minimum length at all. This
     * doesn't replace BCrypt (which handles the actual cryptographic
     * strength), it just stops someone registering with a 1-character
     * password in the first place.
     */
    public record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 6) String password,
        @NotNull Role role,
        String linkedMemberId   // deliberately no @NotBlank — null is valid for ADMIN accounts
    ) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}