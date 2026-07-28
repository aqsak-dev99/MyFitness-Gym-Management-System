package com.gymmanagement.exception;

/** Thrown when a member with the same ID or email already exists. */
public class DuplicateMemberException extends RuntimeException {
    public DuplicateMemberException(String message) { super(message); }
}
