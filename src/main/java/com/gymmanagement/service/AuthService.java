package com.gymmanagement.service;

import com.gymmanagement.exception.DuplicateUserException;
import com.gymmanagement.exception.InvalidCredentialsException;
import com.gymmanagement.exception.UnauthorizedException;
import com.gymmanagement.model.Role;
import com.gymmanagement.model.User;
import com.gymmanagement.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * AuthService owns every rule around who can log in and what they're
 * allowed to do. This is the ONLY class in the project that touches
 * BCrypt directly — User never sees a plain-text password, and
 * SqliteUserRepository never hashes or checks anything, it just stores
 * whatever hash it's given.
 *
 * @Service — same reasoning as the other three services.
 */
@Service
public class AuthService {

    private final UserRepository userRepo;

    public AuthService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    // ── registration ──────────────────────────────────────

    /**
     * Register a new login account.
     *
     * @param username       must be unique
     * @param plainPassword  the raw password — hashed here, never stored as-is
     * @param role           ADMIN or MEMBER
     * @param linkedMemberId the Member this account belongs to, or null for ADMIN accounts
     */
    public User register(String username, String plainPassword,
                         Role role, String linkedMemberId) {
        if (userRepo.existsByUsername(username))
            throw new DuplicateUserException("Username already taken: " + username);

        // BCrypt.gensalt() generates a fresh random salt every call, so two
        // users with the identical password get completely different hashes.
        // This is why you never see the same hash twice even for "password123".
        String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt());

        User user = new User(UUID.randomUUID().toString(), username, hash, role, linkedMemberId);
        userRepo.save(user);
        return user;
    }

    // ── login ─────────────────────────────────────────────

    /**
     * Verify a username/password pair and return the matching User.
     *
     * Deliberately throws the SAME message whether the username doesn't
     * exist or the password is wrong. Telling an attacker "that username
     * doesn't exist" (vs "wrong password") lets them enumerate which
     * usernames are real — a well-known real-world login-security mistake.
     * One generic message closes that leak.
     */
    public User login(String username, String plainPassword) {
        User user = userRepo.findByUsername(username)
                            .orElseThrow(() -> new InvalidCredentialsException(
                                "Invalid username or password."));

        // BCrypt.checkpw re-hashes the input with the salt embedded in the
        // stored hash and compares the result — it never "decrypts" anything,
        // because BCrypt hashing is one-way by design.
        if (!BCrypt.checkpw(plainPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password.");
        }
        return user;
    }

    // ── authorization ─────────────────────────────────────

    /**
     * Confirm a user holds a specific role before letting an action proceed.
     * This is "authorization" — a separate concern from "authentication"
     * (login proves WHO you are; this checks WHAT you're allowed to do).
     */
    public void requireRole(User user, Role required) {
        if (user.getRole() != required) {
            throw new UnauthorizedException(
                "Action requires " + required + " role, but " + user.getUsername()
                + " has " + user.getRole() + " role.");
        }
    }
}