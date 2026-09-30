package com.gymmanagement.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CorsConfig — the one, deliberately minimal backend change made for
 * frontend integration. Always allows the Vite dev server
 * (localhost:5173), so local development never breaks; also allows
 * the real deployed frontend's origin via FRONTEND_URL, read the same
 * way DATABASE_URL/GEMINI_API_KEY/JWT_SECRET already are — set once on
 * Render, no code change needed if the deployed frontend's URL changes.
 *
 * No allowCredentials(true) — this app authenticates via a Bearer token
 * in a header (see JwtAuthenticationFilter), not cookies, so credentialed
 * CORS mode was never needed here.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String frontendUrl = System.getenv("FRONTEND_URL");
        String[] origins = (frontendUrl != null && !frontendUrl.isBlank())
            ? new String[]{"http://localhost:5173", frontendUrl}
            : new String[]{"http://localhost:5173"};

        registry.addMapping("/**")
            .allowedOrigins(origins)
            .allowedMethods("GET", "POST", "PATCH", "DELETE")
            .allowedHeaders("*");
    }
}