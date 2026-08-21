package com.gymmanagement.config;

import com.gymmanagement.exception.UnauthorizedException;
import com.gymmanagement.model.Role;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * RoleAuthorizationInterceptor — the enforcement half of both
 * @RequireRole and @RequireOwnership.
 *
 * Runs inside Spring MVC's own dispatch cycle (unlike
 * JwtAuthenticationFilter, a raw servlet filter that runs before Spring
 * MVC even resolves a handler), which is why this can THROW
 * UnauthorizedException and have GlobalExceptionHandler catch it
 * normally — the filter couldn't do that; it had to write a raw JSON
 * response itself, since GlobalExceptionHandler's @ExceptionHandler
 * methods only see exceptions thrown from within Spring MVC's own
 * processing, not from a filter that runs before it.
 *
 * Reads the role/linkedMemberId the filter already put on the request
 * as attributes — this class never touches a token directly, it trusts
 * that JwtAuthenticationFilter already validated one, since this only
 * ever runs on paths the filter has already let through.
 */
@Component
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;   // not a controller method (e.g. a static resource) — nothing to check
        }

        checkRequiredRole(request, handlerMethod);
        checkOwnership(request, handlerMethod);

        return true;
    }

    private void checkRequiredRole(HttpServletRequest request, HandlerMethod handlerMethod) {
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            return;   // no role restriction — any authenticated user may proceed
        }

        String actualRole   = (String) request.getAttribute("role");
        String requiredRole = requireRole.value().name();

        if (actualRole == null || !actualRole.equals(requiredRole)) {
            throw new UnauthorizedException(
                "This action requires " + requiredRole + " role" +
                (actualRole != null ? ", but the authenticated user has role " + actualRole + "." : "."));
        }
    }

    /**
     * Enforces "ADMIN can access any member; MEMBER can only access the
     * member their own token is linked to." ADMIN bypasses this check
     * entirely — it only restricts MEMBER-role tokens.
     *
     * Reads the {memberId}-style path variable via Spring's own
     * HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE — the standard,
     * documented way an interceptor accesses resolved path variables.
     * Spring populates this request attribute during handler resolution,
     * which always completes before preHandle() runs, so it's safe to
     * read here without any extra wiring.
     */
    private void checkOwnership(HttpServletRequest request, HandlerMethod handlerMethod) {
        RequireOwnership requireOwnership = handlerMethod.getMethodAnnotation(RequireOwnership.class);
        if (requireOwnership == null) {
            return;   // no ownership restriction on this endpoint
        }

        String actualRole = (String) request.getAttribute("role");
        if (Role.ADMIN.name().equals(actualRole)) {
            return;   // ADMIN bypasses ownership checks entirely
        }

        String linkedMemberId = (String) request.getAttribute("linkedMemberId");
        String pathMemberId   = extractPathVariable(request, requireOwnership.value());

        if (linkedMemberId == null || !linkedMemberId.equals(pathMemberId)) {
            throw new UnauthorizedException("You can only access or modify your own member data.");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractPathVariable(HttpServletRequest request, String variableName) {
        Map<String, String> pathVariables =
            (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return pathVariables != null ? pathVariables.get(variableName) : null;
    }
}