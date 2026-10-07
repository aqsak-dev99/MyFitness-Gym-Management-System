package com.gymmanagement.service;

/**
 * AdminAssistantClient — what the controller depends on for the Admin AI's
 * live-data answers. Same reasoning as BootcampToolCallingClient: the
 * controller (and its tests) depend on this interface, so swapping in a
 * fake never needs a network call or an API key.
 */
public interface AdminAssistantClient {

    /**
     * Answers an admin's question. If it needs live gym data the model
     * requests it through the allow-listed read-only tools; otherwise it
     * answers directly. Always returns plain text for the admin.
     */
    String askAdminAssistant(String question);
}
