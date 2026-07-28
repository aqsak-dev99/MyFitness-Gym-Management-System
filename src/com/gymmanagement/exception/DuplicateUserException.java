package com.gymmanagement.exception;

/** Thrown when a username is already registered. */
public class DuplicateUserException extends RuntimeException {
    public DuplicateUserException(String message) { super(message); }
}