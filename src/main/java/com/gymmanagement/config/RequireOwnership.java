package com.gymmanagement.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RequireOwnership — marks a controller method as restricted to the
 * member whose token this is, unless the caller is ADMIN. Checked by
 * the same RoleAuthorizationInterceptor that already enforces
 * @RequireRole — no new filter, no new exception type, no new
 * response-writing logic.
 *
 * value() names the @PathVariable holding the memberId to check
 * ownership against — e.g. @RequireOwnership("memberId") on a method
 * with @PathVariable String memberId in its path.
 *
 * ADMIN bypasses this check entirely; it only restricts MEMBER-role
 * tokens to their own linked member.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequireOwnership {
    String value();
}