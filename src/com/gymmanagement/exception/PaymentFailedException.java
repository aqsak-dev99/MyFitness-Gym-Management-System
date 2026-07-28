package com.gymmanagement.exception;

/** Thrown when a payment transaction cannot be completed. */
public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(String message) { super(message); }
}
