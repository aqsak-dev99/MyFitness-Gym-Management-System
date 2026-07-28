package com.gymmanagement.repository;

import com.gymmanagement.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A hand-written fake implementation of UserRepository, used ONLY in tests.
 * Same pattern as FakeMemberRepository and FakeBootcampRepository.
 */
public class FakeUserRepository implements UserRepository {

    private final List<User> users = new ArrayList<>();

    @Override
    public void save(User user) {
        users.removeIf(u -> u.getUserId().equals(user.getUserId()));
        users.add(user);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return users.stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst();
    }

    @Override
    public Optional<User> findById(String userId) {
        return users.stream()
                    .filter(u -> u.getUserId().equals(userId))
                    .findFirst();
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(users);
    }

    @Override
    public boolean existsByUsername(String username) {
        return users.stream().anyMatch(u -> u.getUsername().equals(username));
    }
}