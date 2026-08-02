package com.gymmanagement.controller;

import com.gymmanagement.exception.ClassFullException;
import com.gymmanagement.exception.DuplicateMemberException;
import com.gymmanagement.exception.DuplicateUserException;
import com.gymmanagement.exception.InvalidCredentialsException;
import com.gymmanagement.exception.MemberNotFoundException;
import com.gymmanagement.exception.PaymentFailedException;
import com.gymmanagement.exception.UnauthorizedException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * GlobalExceptionHandler — catches exceptions thrown anywhere in any
 * controller and converts them into the correct HTTP status code, instead
 * of every uncaught exception becoming a generic 500 Internal Server Error.
 *
 * This is exactly what "meaningful HTTP status codes" means in practice:
 *  - MemberNotFoundException    → 404 Not Found     (nothing to look up)
 *  - DuplicateMemberException   → 409 Conflict       (request conflicts with existing state)
 *  - DuplicateUserException     → 409 Conflict       (username already taken)
 *  - ClassFullException         → 409 Conflict       (class capacity conflicts with the enrolment request)
 *  - IllegalStateException      → 409 Conflict       (already enrolled, wrong membership type, session limit hit)
 *  - PaymentFailedException     → 402 Payment Required (a real HTTP status that exists for exactly this)
 *  - InvalidCredentialsException → 401 Unauthorized  (login failed — who you claim to be wasn't verified)
 *  - UnauthorizedException      → 403 Forbidden       (you ARE who you say — you just can't do this)
 *  - IllegalArgumentException   → 400 Bad Request    (the request itself was invalid)
 *
 * 401 vs 403 is a real, commonly-confused distinction worth being precise
 * about: 401 means authentication itself failed (no valid identity was
 * established at all — a wrong password, a login attempt that didn't
 * verify). 403 means authentication succeeded — the system knows exactly
 * who you are — but that identity isn't permitted to do the specific
 * thing being asked. InvalidCredentialsException always fires from
 * login(), where identity was never established, so 401 is correct.
 * UnauthorizedException fires from requireRole(), which only ever runs
 * on an ALREADY-authenticated User — so 403 is correct there, not 401.
 *
 * @RestControllerAdvice applies these handlers across every
 * @RestController in the project — one place, not repeated per-controller.
 *
 * None of the existing exception classes changed. This class doesn't touch
 * any service or the exceptions themselves — it only decides how each one
 * should look once it reaches an HTTP client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(MemberNotFoundException e) {
        return buildResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DuplicateMemberException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateMemberException e) {
        return buildResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateUser(DuplicateUserException e) {
        return buildResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ClassFullException.class)
    public ResponseEntity<Map<String, Object>> handleClassFull(ClassFullException e) {
        return buildResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Covers "already enrolled in this class" and "not a Pay-As-You-Go
     * member" / "session limit reached" — all thrown as IllegalStateException
     * by MembershipService. Each represents the same underlying idea: the
     * request is well-formed, but the current state of the system conflicts
     * with what's being asked for.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        return buildResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentFailed(PaymentFailedException e) {
        return buildResponse(HttpStatus.PAYMENT_REQUIRED, e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException e) {
        return buildResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException e) {
        return buildResponse(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidInput(IllegalArgumentException e) {
        return buildResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * A small, consistent JSON error shape for every handled exception —
     * so a client always knows what fields to expect in an error response,
     * rather than each exception type returning a differently-shaped body.
     */
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = Map.of(
            "timestamp", Instant.now().toString(),
            "status", status.value(),
            "error", status.getReasonPhrase(),
            "message", message
        );
        return ResponseEntity.status(status).body(body);
    }
}