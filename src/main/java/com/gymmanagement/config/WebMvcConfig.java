package com.gymmanagement.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvcConfig — registers RoleAuthorizationInterceptor into Spring
 * MVC's request pipeline. Unlike JwtAuthenticationFilter (a plain
 * @Component is enough for a servlet Filter to be auto-registered),
 * a HandlerInterceptor needs to be explicitly added here — Spring MVC
 * doesn't scan for interceptors on its own.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final @NonNull RoleAuthorizationInterceptor roleAuthorizationInterceptor;

    public WebMvcConfig(@NonNull RoleAuthorizationInterceptor roleAuthorizationInterceptor) {
        this.roleAuthorizationInterceptor = roleAuthorizationInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(roleAuthorizationInterceptor);
    }
}