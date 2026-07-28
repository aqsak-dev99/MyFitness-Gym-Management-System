package com.gymmanagement.exception;

/** Thrown when a bootcamp or gym class has no remaining capacity. */
public class ClassFullException extends RuntimeException {
    public ClassFullException(String message) { super(message); }
}
