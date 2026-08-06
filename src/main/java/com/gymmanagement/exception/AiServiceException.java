package com.gymmanagement.exception;


/** Thrown when a call to an external AI service (Gemini) fails or returns an unusable response. */
public class AiServiceException extends RuntimeException {
    public AiServiceException(String message) { super(message); }
}
