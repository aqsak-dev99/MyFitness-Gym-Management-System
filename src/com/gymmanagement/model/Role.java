package com.gymmanagement.model;

/**
 * The two access levels in MyFitness.
 *
 * ADMIN  — gym staff/owner accounts. Can manage members, staff, and classes.
 * MEMBER — a login linked to an existing Member row. Can only see their own data.
 *
 * Kept as a plain enum (not a class with behaviour) because right now the
 * only thing that varies between roles is "which actions are allowed" —
 * that check lives in AuthService.requireRole(), not on the enum itself.
 */
public enum Role {
    ADMIN,
    MEMBER
}