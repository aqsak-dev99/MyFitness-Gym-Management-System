package com.gymmanagement.service;

import com.gymmanagement.exception.DuplicateUserException;
import com.gymmanagement.exception.InvalidCredentialsException;
import com.gymmanagement.exception.UnauthorizedException;
import com.gymmanagement.model.Role;
import com.gymmanagement.model.User;
import com.gymmanagement.repository.FakeUserRepository;
import com.gymmanagement.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests AuthService in isolation using a fake repository.
 *
 * These tests are a bit different in character from the other test classes
 * — most tests in this project check BUSINESS rules ("does the discount
 * apply correctly"), but several tests here check SECURITY properties
 * ("is the password actually hashed", "does a wrong password and an unknown
 * username produce the exact same error"). Those are just as testable as
 * business logic, and arguably more important to pin down with a test
 * rather than trust to memory.
 */
class AuthServiceTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        UserRepository fakeRepo = new FakeUserRepository();
        authService = new AuthService(fakeRepo);
    }

    // ── registration ──────────────────────────────────────

    @Test
    void registeringNewUserSucceeds() {
        User user = authService.register("admin", "Admin@123", Role.ADMIN, null);

        assertEquals("admin", user.getUsername());
        assertEquals(Role.ADMIN, user.getRole());
    }

    @Test
    void registeringDuplicateUsernameThrows() {
        authService.register("admin", "Admin@123", Role.ADMIN, null);

        assertThrows(DuplicateUserException.class, () ->
            authService.register("admin", "DifferentPassword", Role.ADMIN, null)
        );
    }

    @Test
    void passwordIsNeverStoredAsPlainText() {
        User user = authService.register("alice", "MyPassword123", Role.MEMBER, "M001");

        assertNotEquals("MyPassword123", user.getPasswordHash(),
            "The stored hash must never equal the plain-text password");
    }

    @Test
    void samePasswordProducesDifferentHashesForDifferentUsers() {
        // This is what BCrypt.gensalt() buys you: a fresh random salt every
        // call, so two identical passwords never produce the same hash.
        User user1 = authService.register("alice", "SamePassword123", Role.MEMBER, "M001");
        User user2 = authService.register("bob",   "SamePassword123", Role.MEMBER, "M002");

        assertNotEquals(user1.getPasswordHash(), user2.getPasswordHash(),
            "Same password should still produce different hashes due to random salting");
    }

    // ── login ─────────────────────────────────────────────

    @Test
    void loginWithCorrectCredentialsSucceeds() {
        authService.register("admin", "Admin@123", Role.ADMIN, null);

        User loggedIn = authService.login("admin", "Admin@123");

        assertEquals("admin", loggedIn.getUsername());
    }

    @Test
    void loginWithWrongPasswordThrowsInvalidCredentials() {
        authService.register("admin", "Admin@123", Role.ADMIN, null);

        assertThrows(InvalidCredentialsException.class, () ->
            authService.login("admin", "WrongPassword")
        );
    }

    @Test
    void loginWithUnknownUsernameThrowsSameExceptionAsWrongPassword() {
        // Deliberately no registration here — "ghost" doesn't exist at all.
        // This must throw the exact same exception type as a wrong password
        // (tested above), never a different one — that's what prevents an
        // attacker from telling "wrong password" apart from "no such user".
        assertThrows(InvalidCredentialsException.class, () ->
            authService.login("ghost", "AnyPassword")
        );
    }

    // ── authorization ─────────────────────────────────────

    @Test
    void requireRoleWithMatchingRolePassesSilently() {
        User admin = authService.register("admin", "Admin@123", Role.ADMIN, null);

        assertDoesNotThrow(() -> authService.requireRole(admin, Role.ADMIN));
    }

    @Test
    void requireRoleWithMismatchedRoleThrowsUnauthorized() {
        User member = authService.register("alice", "Alice@123", Role.MEMBER, "M001");

        assertThrows(UnauthorizedException.class, () ->
            authService.requireRole(member, Role.ADMIN)
        );
    }
}