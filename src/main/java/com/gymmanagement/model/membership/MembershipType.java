package com.gymmanagement.model.membership;

/**
 * The three real Membership subtypes that already exist in this
 * project — StandardMembership, StudentSaverMembership,
 * PayAsYouGoMembership. Used only as a request-body discriminator so
 * the new assign-membership endpoint knows which concrete class to
 * construct; the actual membership behavior is entirely unchanged.
 */
public enum MembershipType {
    STANDARD,
    STUDENT_SAVER,
    PAY_AS_YOU_GO
}