package com.gymmanagement.repository;

import com.gymmanagement.db.DatabaseManager;
import com.gymmanagement.model.Role;
import com.gymmanagement.model.User;

import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SqliteUserRepository — JDBC-backed implementation of UserRepository.
 *
 * Uses "INSERT ... ON CONFLICT DO UPDATE" rather than "INSERT OR REPLACE" —
 * same reasoning as SqliteMemberRepository.upsertMember(). REPLACE deletes
 * the row first, which would trigger any ON DELETE CASCADE/SET NULL rule.
 * There's no child table referencing users(user_id) yet, so this isn't a
 * live bug today — but writing it the safe way from day one means it never
 * becomes one later if a child table is added.
 *
 * @Repository — has a plain no-arg constructor, same as SqliteMemberRepository,
 * so nothing else needs wiring for this bean.
 */
@Repository
public class SqliteUserRepository implements UserRepository {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    @Override
    public void save(User user) {
        String sql =
            "INSERT INTO users (user_id, username, password_hash, role, member_id) " +
            "VALUES (?, ?, ?, ?, ?) " +
            "ON CONFLICT(user_id) DO UPDATE SET " +
            "  username = excluded.username, " +
            "  password_hash = excluded.password_hash, " +
            "  role = excluded.role, " +
            "  member_id = excluded.member_id";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, user.getUserId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole().name());
            ps.setString(5, user.getLinkedMemberId());  // OK if null
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save user " + user.getUsername()
                                       + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(buildUser(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByUsername failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findById(String userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(buildUser(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findById failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY username";
        List<User> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(buildUser(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findAll users failed: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public boolean existsByUsername(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ? LIMIT 1";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("existsByUsername failed: " + e.getMessage(), e);
        }
    }

    private User buildUser(ResultSet rs) throws SQLException {
        return new User(
            rs.getString("user_id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            Role.valueOf(rs.getString("role")),
            rs.getString("member_id")   // may be null — JDBC returns null cleanly here
        );
    }
}