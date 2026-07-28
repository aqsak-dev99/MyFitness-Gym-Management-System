package com.gymmanagement.exception;

/** Thrown when a logged-in user attempts an action their role doesn't permit. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) { super(message); }
}
