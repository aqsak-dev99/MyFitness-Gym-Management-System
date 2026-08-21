package com.gymmanagement.exception;

/** Thrown when assigning an instructor to a class would conflict with a class they're already teaching at the same time. */
public class SchedulingConflictException extends RuntimeException {
    public SchedulingConflictException(String message) { super(message); }
}