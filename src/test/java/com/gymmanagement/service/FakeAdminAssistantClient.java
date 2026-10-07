package com.gymmanagement.service;

/** A hand-written fake of AdminAssistantClient, used ONLY in tests. */
public class FakeAdminAssistantClient implements AdminAssistantClient {

    private String cannedResponse = "This is a fake admin assistant response.";
    private String lastQuestionReceived;

    @Override
    public String askAdminAssistant(String question) {
        this.lastQuestionReceived = question;
        return cannedResponse;
    }

    public void setCannedResponse(String response) {
        this.cannedResponse = response;
    }

    public String getLastQuestionReceived() {
        return lastQuestionReceived;
    }
}
