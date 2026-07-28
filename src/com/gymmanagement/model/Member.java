package com.gymmanagement.model;

import com.gymmanagement.model.membership.Membership;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Member extends Person {

    private String        memberId;
    private LocalDate     registrationDate;
    private Membership    membership;
    private List<Payment> paymentHistory;

    public Member(String personId, String memberId, String name,
                  String email, String phone) {
        super(personId, name, email, phone);
        if (memberId == null || memberId.isBlank())
            throw new IllegalArgumentException("Member ID cannot be empty.");
        this.memberId         = memberId;
        this.registrationDate = LocalDate.now();
        this.paymentHistory   = new ArrayList<>();
    }

    // ── getters ──────────────────────────────────────────
    public String        getMemberId()         { return memberId;         }
    public LocalDate     getRegistrationDate() { return registrationDate; }
    public Membership    getMembership()       { return membership;       }
    public List<Payment> getPaymentHistory()   { return new ArrayList<>(paymentHistory); }

    // ── membership management ─────────────────────────────
    public void setMembership(Membership membership) {
        this.membership = membership;
    }

    public void removeMembership() {
        this.membership = null;
    }

    // ── payment management ────────────────────────────────
    public void addPayment(Payment payment) {
        paymentHistory.add(payment);
    }

    // ── equality by memberId ──────────────────────────────
    /**
     * Two Member objects represent the same member if their memberId matches.
     * This is required for List.contains() and stream .filter(list::contains)
     * checks to work correctly, since repository implementations (e.g.
     * SqliteMemberRepository) construct a brand new Member instance on every
     * fetch — without this override, contains() would silently use reference
     * equality and never match a member fetched in a separate query.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member)) return false;
        return memberId.equals(((Member) o).memberId);
    }

    @Override
    public int hashCode() {
        return memberId.hashCode();
    }

    // ── persistence helper ────────────────────────────────
    /**
     * Restores the exact registration date loaded from the database.
     *
     * The default constructor always sets registrationDate = LocalDate.now().
     * When reconstructing a Member from a DB row, the repository must restore
     * the original stored date.
     *
     * This method is public because SqliteMemberRepository lives in a
     * different package (com.gymmanagement.repository) and package-private
     * access does not cross package boundaries. The "FromDb" naming and this
     * Javadoc mark it as a persistence-layer concern — application and
     * service code should never call this directly.
     */
    public void setRegistrationDateFromDb(LocalDate date) {
        this.registrationDate = date;
    }

    // ── getDetails ────────────────────────────────────────
    @Override
    public String getDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- Member Details ---\n");
        sb.append("Member ID  : ").append(memberId).append("\n");
        sb.append("Name       : ").append(getName()).append("\n");
        sb.append("Email      : ").append(getEmail()).append("\n");
        sb.append("Phone      : ").append(getPhone()).append("\n");
        sb.append("Registered : ").append(registrationDate).append("\n");
        if (membership != null) {
            sb.append("Membership : ").append(membership.getMembershipId())
              .append(" | Active: ").append(membership.isActive())
              .append(" | Days left: ").append(membership.getDaysRemaining()).append("\n");
        } else {
            sb.append("Membership : None\n");
        }
        sb.append("Payments   : ").append(paymentHistory.size());
        return sb.toString();
    }
}