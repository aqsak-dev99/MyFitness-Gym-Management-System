package com.gymmanagement.exception;

/** Thrown when method arguments fail business-rule validation. */
public class InvalidInputException extends RuntimeException {
    public InvalidInputException(String message) { super(message); }
}
