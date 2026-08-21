package com.gymmanagement.service;

/**
 * BootcampToolCallingClient — the contract BootcampToolCallingService
 * fulfills. Same reasoning as AiChatClient/AiEmbeddingClient: the real
 * implementation's constructor reads GEMINI_API_KEY and throws if it's
 * missing, so anything that just needs an instance to exist (like
 * RoleAuthorizationInterceptorTest, which never actually invokes this
 * method) needs something a fake can implement instead.
 */
public interface BootcampToolCallingClient {
    String askAboutBootcampClasses(String question);
}
