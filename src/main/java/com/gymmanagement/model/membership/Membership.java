package com.gymmanagement.model.membership;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public abstract class Membership {

    /** How many days before the due date counts as "due soon" — kept as
     *  a named constant specifically so it's easy to find and change
     *  later, per the explicit ask to keep this configurable. */
    public static final int DUE_SOON_THRESHOLD_DAYS = 3;

    private String    membershipId;
    private LocalDate startDate;
    private LocalDate endDate;
    private double    monthlyFee;
    private boolean   frozen;

    /**
     * Nullable — genuinely, not a bug. PayAsYouGoMembership has no
     * recurring due date at all (pay-per-session, no subscription
     * cycle), so it's left null rather than forcing an artificial date
     * onto a membership type that doesn't conceptually have one.
     * Standard/StudentSaver (the two real recurring types) get a real
     * value set at assignment time in MemberService.assignMembership().
     */
    private LocalDate nextPaymentDueDate;

    public Membership(String membershipId, LocalDate startDate,
                      LocalDate endDate, double monthlyFee) {
        if (membershipId == null || membershipId.isBlank())
            throw new IllegalArgumentException("Membership ID cannot be empty.");
        if (startDate == null || endDate == null)
            throw new IllegalArgumentException("Dates cannot be null.");
        if (endDate.isBefore(startDate))
            throw new IllegalArgumentException("End date must be after start date.");
        this.membershipId = membershipId;
        this.startDate    = startDate;
        this.endDate      = endDate;
        this.monthlyFee   = monthlyFee;
        this.frozen       = false;
    }

    // ── getters / setters ─────────────────────────────────
    public String    getMembershipId() { return membershipId; }
    public LocalDate getStartDate()    { return startDate;    }
    public LocalDate getEndDate()      { return endDate;      }
    public double    getMonthlyFee()   { return monthlyFee;   }
    public boolean   isFrozen()        { return frozen;       }
    public LocalDate getNextPaymentDueDate() { return nextPaymentDueDate; }

    public void setMonthlyFee(double fee) {
        if (fee < 0) throw new IllegalArgumentException("Fee cannot be negative.");
        this.monthlyFee = fee;
    }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    /**
     * Genuine persistence-restoration setter, matching setEndDate()'s
     * own existing purpose — this was the missing half of that pair.
     * The repository's reconstruction comment always claimed both
     * dates were restored from storage; only endDate actually was.
     * Without this, every reload silently reset startDate to whatever
     * the concrete constructor defaults to internally, discarding the
     * real historical join date on every single read.
     */
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    /**
     * Sets the due date directly — used both by MemberService when
     * first assigning a recurring membership, and by the repository
     * when restoring a stored membership from the database. Public
     * (not a repository-only backdoor) since MemberService itself
     * needs to call it too, for the same "no redundant persistence-only
     * setter" reasoning already used for Member.active.
     */
    public void setNextPaymentDueDate(LocalDate dueDate) { this.nextPaymentDueDate = dueDate; }

    /**
     * Advances the due date by one billing cycle (one month) — called
     * when a real membership payment is recorded. Mirrors how an actual
     * subscription system moves the next charge date forward on
     * successful payment, rather than requiring a separate "was this
     * period paid" lookup against payment history.
     */
    public void advancePaymentDueDate() {
        if (nextPaymentDueDate != null) {
            this.nextPaymentDueDate = nextPaymentDueDate.plusMonths(1);
        }
    }

    /**
     * Derived, not stored — always reflects the real current date
     * versus the real due date, never goes stale. "N/A" for
     * PayAsYouGoMembership (no recurring due date to compare against).
     */
    public String getPaymentStatus() {
        if (nextPaymentDueDate == null) return "N/A";
        LocalDate today = LocalDate.now();
        if (today.isAfter(nextPaymentDueDate)) return "OVERDUE";
        long daysUntilDue = ChronoUnit.DAYS.between(today, nextPaymentDueDate);
        if (daysUntilDue <= DUE_SOON_THRESHOLD_DAYS) return "DUE_SOON";
        return "PAID";
    }

    /**
     * Derived, human-readable type — not stored, computed the same
     * way isRecurringMembership() already checks type internally.
     * Added specifically so Reports can show a real membership-type
     * breakdown without inferring it from unrelated fields (like
     * whether studentIdNumber happens to be present) — the same
     * discipline already applied everywhere else this session.
     */
    public String getMembershipType() {
        String className = getClass().getSimpleName();
        return switch (className) {
            case "StandardMembership" -> "Standard";
            case "StudentSaverMembership" -> "Student Saver";
            case "PayAsYouGoMembership" -> "Pay As You Go";
            default -> className;
        };
    }

    // ── business logic ────────────────────────────────────
    public boolean isActive() {
        LocalDate today = LocalDate.now();
        return !frozen && !today.isBefore(startDate) && !today.isAfter(endDate);
    }

    public long getDaysRemaining() {
        return Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), endDate));
    }

    public long getMonthsRemaining() {
        return getDaysRemaining() / 30;
    }

    public void freeze() {
        this.frozen = true;
    }

    public void unfreeze() {
        this.frozen = false;
    }

    // ── abstract ──────────────────────────────────────────
    public abstract double calcFee();
    public abstract String getDetails();

    @Override
    public String toString() { return getDetails(); }
}