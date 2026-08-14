package com.gymmanagement.controller;

import com.gymmanagement.model.Role;
import com.gymmanagement.model.User;
import com.gymmanagement.service.AuthService;
import com.gymmanagement.service.JwtService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — the HTTP-facing layer for registration and login.
 *
 * Now issues a real JWT alongside the User on both endpoints — this is
 * the other half of auth enforcement: JwtAuthenticationFilter checks
 * incoming tokens on every other request, this is where a token first
 * gets created. Registration auto-issues a token too (log in immediately
 * after registering, not a separate second step).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService  jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService  = jwtService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(
            request.username(), request.password(), request.role(), request.linkedMemberId()
        );
        String token = jwtService.generateToken(user);
        return new LoginResponse(token, user);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request.username(), request.password());
        String token = jwtService.generateToken(user);
        return new LoginResponse(token, user);
    }

    // ── request/response DTOs ──────────────────────────────

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

    /**
     * The token a client saves and sends back as
     * "Authorization: Bearer <token>" on every subsequent request.
     */
    public record LoginResponse(String token, User user) {}
}