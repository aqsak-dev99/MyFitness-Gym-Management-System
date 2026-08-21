package com.gymmanagement.service;

/**
 * A hand-written fake implementation of AiChatClient, used ONLY in tests.
 * Same pattern as FakeMemberRepository — no Mockito, just a plain class
 * with a canned return value and a way to inspect what it was called with.
 */
public class FakeAiChatClient implements AiChatClient {

    private String cannedResponse = "This is a fake AI response.";
    private String lastPromptReceived;

    @Override
    public String ask(String prompt) {
        this.lastPromptReceived = prompt;
        return cannedResponse;
    }

    public void setCannedResponse(String response) {
        this.cannedResponse = response;
    }

    public String getLastPromptReceived() {
        return lastPromptReceived;
    }
}