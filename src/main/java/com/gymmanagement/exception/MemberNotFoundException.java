package com.gymmanagement.exception;

/** Thrown when a member lookup returns no result. */
public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(String message) { super(message); }
}
