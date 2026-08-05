package com.gymmanagement.exception;

/** Thrown when the AI rate limiter blocks a request to protect free-tier quota. */
public class AiRateLimitExceededException extends RuntimeException {
    public AiRateLimitExceededException(String message) { super(message); }
}
