package com.gymmanagement.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;

public class Payment {

    public static final String STATUS_PENDING   = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED    = "FAILED";

    private String    paymentId;
    private double    amount;
    private LocalDate paymentDate;
    private String    description;
    private String    status;
    private Member    member;

    public Payment(String paymentId, double amount, String description, Member member) {
        if (paymentId == null || paymentId.isBlank())
            throw new IllegalArgumentException("Payment ID cannot be empty.");
        if (amount <= 0)
            throw new IllegalArgumentException("Payment amount must be positive.");
        if (member == null)
            throw new IllegalArgumentException("Payment must be linked to a member.");
        this.paymentId   = paymentId;
        this.amount      = amount;
        this.description = description;
        this.member      = member;
        this.paymentDate = LocalDate.now();
        this.status      = STATUS_PENDING;
    }

    // ── getters ──────────────────────────────────────────
    public String    getPaymentId()   { return paymentId;   }
    public double    getAmount()      { return amount;      }
    public LocalDate getPaymentDate() { return paymentDate; }
    public String    getDescription() { return description; }
    public String    getStatus()      { return status;      }

    /**
     * @JsonIgnore stops Jackson (Spring's JSON converter) from serialising
     * this field. Without it: Member → paymentHistory → each Payment →
     * getMember() → the same Member → paymentHistory → ... forever, until
     * the stack overflows. That's exactly what crashed GET /api/members —
     * a payment nested inside a member re-including that same member,
     * infinitely.
     *
     * This is a pragmatic fix, not the permanent one. It works because
     * a Payment is always accessed through its owning Member already
     * (member.getPaymentHistory()), so re-including the member inside
     * each payment's JSON is redundant, not just cyclical. The more
     * scalable long-term fix — for this AND the same latent problem in
     * Instructor ↔ GymClass — is a dedicated response DTO per endpoint
     * that only includes what that specific response actually needs,
     * so the domain model itself never has to know Jackson exists.
     */
    @JsonIgnore
    public Member getMember() { return member; }

    // ── status mutator (used by PaymentService only) ──────
    public void markCompleted() { this.status = STATUS_COMPLETED; }
    public void markFailed()    { this.status = STATUS_FAILED;    }

    // ── persistence helper ────────────────────────────────
    /**
     * Restores the exact payment date loaded from the database.
     *
     * The constructor always stamps paymentDate = LocalDate.now(), which is
     * right for a payment being created today but wrong for one being
     * rebuilt from a stored row. Before this method existed, every payment
     * read back from the database reported "today" as its date, so any
     * date-based figure (revenue this month, a monthly breakdown) quietly
     * collapsed into the current period.
     *
     * Same pattern, same reasoning, and same "FromDb" naming as
     * Member.setRegistrationDateFromDb(): public only because the
     * repository lives in another package, and application/service code
     * should never call it. A null date is ignored rather than wiping the
     * default.
     */
    public void setPaymentDateFromDb(LocalDate date) {
        if (date != null) this.paymentDate = date;
    }

    // ── getDetails ────────────────────────────────────────
    public String getDetails() {
        return String.format(
            "Payment ID  : %s%n" +
            "Member      : %s%n" +
            "Amount      : £%.2f%n" +
            "Date        : %s%n" +
            "Description : %s%n" +
            "Status      : %s",
            paymentId, member.getName(), amount, paymentDate, description, status);
    }

    @Override
    public String toString() { return getDetails(); }
}
