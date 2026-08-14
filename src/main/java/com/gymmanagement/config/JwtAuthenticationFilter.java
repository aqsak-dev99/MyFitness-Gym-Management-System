package com.gymmanagement.config;

import com.gymmanagement.service.JwtService;

import io.jsonwebtoken.Claims;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JwtAuthenticationFilter — this is what makes auth enforcement real,
 * not just "a login endpoint that exists." Every incoming request runs
 * through this before it can reach any controller.
 *
 * Auto-registered by Spring Boot as a servlet filter just from being a
 * @Component — no separate configuration class needed to wire it in,
 * same as every other bean in this project.
 *
 * Important architectural point: this is a SERVLET filter, running
 * BEFORE Spring MVC's own machinery — including GlobalExceptionHandler,
 * which only catches exceptions thrown from inside a controller.
 * Rejecting a request here means writing the error response directly,
 * not throwing something and hoping GlobalExceptionHandler catches it —
 * it can't, at this layer.
 *
 * Scope, stated plainly: this checks that a request carries ANY valid
 * token. It does not yet check WHICH role that token belongs to against
 * what a specific endpoint requires — that's fine-grained authorization,
 * a deliberate, separate next step once this base layer is proven.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Paths reachable without a token — necessarily so, since you can't
    // present a token to obtain one in the first place, and monitoring/
    // docs tooling shouldn't require a login to function.
    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/auth/register",
        "/api/auth/login",
        "/actuator/health",
        "/swagger-ui",
        "/v3/api-docs"
    );

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "Missing or malformed Authorization header. Expected: Bearer <token>");
            return;
        }

        String token = authHeader.substring("Bearer ".length());

        try {
            Claims claims = jwtService.validateAndParse(token);
            // Identity made available to the rest of this request via
            // attributes — a simple, direct mechanism appropriate for
            // this project's scale, rather than reaching for Spring
            // Security's SecurityContext machinery, which this project
            // deliberately isn't using.
            request.setAttribute("username", claims.getSubject());
            request.setAttribute("role", claims.get("role", String.class));
            request.setAttribute("linkedMemberId", claims.get("linkedMemberId", String.class));
        } catch (Exception e) {
            writeUnauthorized(response, "Invalid or expired token.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
            "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}"
        );
    }
}