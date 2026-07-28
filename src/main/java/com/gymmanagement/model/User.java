package com.gymmanagement.model;

/**
 * User represents a login account — separate from Member and Staff.
 *
 * Why a separate class instead of adding a password field to Member?
 * Member models "someone who has a gym membership." User models "someone
 * who can log in." Those are different concerns — an ADMIN user (gym staff)
 * has no Member record at all, and not every Member necessarily has a login.
 * Keeping them separate means neither class grows fields it doesn't need.
 *
 * linkedMemberId is nullable: ADMIN accounts have no linked member.
 * A MEMBER-role account's linkedMemberId points at their Member row, which
 * is how the system knows "this login belongs to Alice, member M001."
 *
 * Note what's deliberately MISSING here: there is no setPassword(plainText)
 * method. Hashing a password is a decision, not a data assignment — it
 * belongs in AuthService, which decides HOW to hash. This class only ever
 * stores the resulting hash, never plain text.
 */
public class User {

    private final String userId;
    private final String username;
    private final String passwordHash;
    private final Role   role;
    private final String linkedMemberId;  // nullable

    public User(String userId, String username, String passwordHash,
               Role role, String linkedMemberId) {
        if (userId == null || userId.isBlank())
            throw new IllegalArgumentException("User ID cannot be empty.");
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Username cannot be empty.");
        if (passwordHash == null || passwordHash.isBlank())
            throw new IllegalArgumentException("Password hash cannot be empty.");
        if (role == null)
            throw new IllegalArgumentException("Role cannot be null.");

        this.userId         = userId;
        this.username        = username;
        this.passwordHash    = passwordHash;
        this.role            = role;
        this.linkedMemberId  = linkedMemberId;
    }

    public String getUserId()         { return userId;        }
    public String getUsername()       { return username;       }
    public String getPasswordHash()   { return passwordHash;   }
    public Role   getRole()           { return role;           }
    public String getLinkedMemberId() { return linkedMemberId; }

    /**
     * Deliberately does NOT include passwordHash in the printed output —
     * even though it's already a hash (not plain text), there's no reason
     * to ever print it. Good habit: never print secrets, hashed or not.
     */
    public String getDetails() {
        return "--- User Account ---\n"
             + "User ID  : " + userId + "\n"
             + "Username : " + username + "\n"
             + "Role     : " + role + "\n"
             + "Linked Member: " + (linkedMemberId != null ? linkedMemberId : "None (admin account)");
    }

    @Override
    public String toString() { return getDetails(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        return userId.equals(((User) o).userId);
    }

    @Override
    public int hashCode() {
        return userId.hashCode();
    }
}