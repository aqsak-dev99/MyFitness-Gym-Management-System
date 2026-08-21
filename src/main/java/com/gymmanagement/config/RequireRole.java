package com.gymmanagement.config;

import com.gymmanagement.model.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RequireRole — marks a controller method as requiring a specific role,
 * checked by RoleAuthorizationInterceptor before the method ever runs.
 *
 * Deliberately an annotation, not a mapping table elsewhere in the
 * codebase or a manual check inside each method body — reading a
 * controller method and seeing @RequireRole(Role.ADMIN) right there
 * tells you exactly what's required without hunting anywhere else.
 *
 * Absence of this annotation means "any authenticated user" (any valid
 * token passes JwtAuthenticationFilter), not "no restriction at all" —
 * JwtAuthenticationFilter has already required a valid token on every
 * non-public path before this annotation is ever even checked.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequireRole {
    Role value();
}