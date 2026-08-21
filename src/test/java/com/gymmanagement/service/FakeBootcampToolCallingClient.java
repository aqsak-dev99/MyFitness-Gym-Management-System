package com.gymmanagement.service;

/** A hand-written fake implementation of BootcampToolCallingClient, used ONLY in tests. */
public class FakeBootcampToolCallingClient implements BootcampToolCallingClient {

    private String cannedResponse = "This is a fake tool-calling response.";

    @Override
    public String askAboutBootcampClasses(String question) {
        return cannedResponse;
    }

    public void setCannedResponse(String response) {
        this.cannedResponse = response;
    }
}