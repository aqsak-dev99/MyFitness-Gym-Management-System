package com.gymmanagement.exception;

/** Thrown when a username doesn't exist OR a password doesn't match. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) { super(message); }
}