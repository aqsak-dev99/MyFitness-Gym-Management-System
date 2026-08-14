package com.gymmanagement.service;

import com.gymmanagement.model.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JwtService — the only class in this project that signs or verifies
 * tokens. Everything else (the login endpoint, the filter that checks
 * incoming requests) depends on THIS class, not on the JJWT library
 * directly — same pattern as GeminiClient being the sole point of
 * contact with Gemini's API.
 *
 * JWT_SECRET follows the exact same fail-fast pattern as every other
 * required environment variable in this project: read once at startup,
 * throw immediately and clearly if missing, never hardcoded.
 *
 * Tokens expire after 24 hours — a flat, simple expiration for now.
 * Refresh tokens (issuing a new token without re-entering a password)
 * are a real, separate feature, deliberately not built tonight.
 */
@Service
public class JwtService {

    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000; // 24 hours

    private final SecretKey signingKey;

    public JwtService() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET environment variable is not set. Generate one with " +
                "`openssl rand -base64 32` and export it before running.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a signed token carrying the user's identity and role.
     * linkedMemberId is included so a future "members can only see their
     * own data" check has what it needs without an extra database lookup
     * on every request — deliberately not enforced yet, just available.
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
            .subject(user.getUsername())
            .claim("role", user.getRole().name())
            .claim("linkedMemberId", user.getLinkedMemberId())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey)
            .compact();
    }

    /**
     * Verifies a token's signature and expiration, and returns its
     * claims if valid. Throws (an unchecked JJWT exception) if the
     * token is expired, malformed, or the signature doesn't match —
     * the filter that calls this is responsible for turning that into
     * a proper 401 response.
     */
    public Claims validateAndParse(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}