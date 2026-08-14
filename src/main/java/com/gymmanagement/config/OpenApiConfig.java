package com.gymmanagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenApiConfig — everything else about the Swagger UI (which endpoints
 * exist, their request/response shapes, which fields are required) gets
 * generated automatically by springdoc reflecting over the existing
 * @RestController classes. This class only supplies the top-level
 * title/description that would otherwise default to something generic
 * like "OpenAPI definition."
 *
 * Left fully public on purpose, unlike Actuator's health details —
 * different situation entirely. There's no sensitive information here
 * beyond what's already readable in this public GitHub repo, and for a
 * portfolio project specifically, a live, interactive API explorer is a
 * genuine asset, not a risk to hide.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI myFitnessOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("MyFitness Gym Management API")
                .description(
                    "A gym management backend covering members, memberships, " +
                    "bootcamp classes, staff, and authentication — plus two " +
                    "AI-powered features: semantic document Q&A (RAG, via " +
                    "pgvector and Gemini embeddings) and a bootcamp " +
                    "recommendation engine that reasons over a member's " +
                    "stated fitness goal and current enrolments.")
                .version("1.0.0"));
    }
}