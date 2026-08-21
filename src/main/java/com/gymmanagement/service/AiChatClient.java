package com.gymmanagement.service;

/**
 * AiChatClient — the contract GeminiClient fulfills. Exists for exactly
 * one reason: DocumentQaService and BootcampRecommendationService need
 * something to depend on that a hand-rolled test fake can implement —
 * the real GeminiClient's constructor reads GEMINI_API_KEY and throws
 * if it's missing, which a test should never need to care about.
 *
 * Same reasoning as every repository interface in this project
 * (MemberRepository, DocumentRepository, etc.) — this isn't a new
 * pattern, it's that existing one applied to the two classes that
 * finally needed it.
 */
public interface AiChatClient {
    String ask(String prompt);
}