package com.gymmanagement.exception;

/** Thrown when a document lookup returns no result. */
public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String message) { super(message); }
}