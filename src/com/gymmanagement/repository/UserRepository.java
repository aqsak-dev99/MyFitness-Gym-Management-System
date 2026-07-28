package com.gymmanagement.repository;

import com.gymmanagement.model.User;
import java.util.List;
import java.util.Optional;

/**
 * Contract for all User data-access implementations — same pattern as
 * MemberRepository and BootcampRepository. AuthService depends on this
 * interface, not on SqliteUserRepository directly.
 */
public interface UserRepository {
    void         save(User user);
    Optional<User> findByUsername(String username);
    Optional<User> findById(String userId);
    List<User>   findAll();
    boolean      existsByUsername(String username);
}