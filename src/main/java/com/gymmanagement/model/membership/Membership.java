package com.gymmanagement.model.membership;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public abstract class Membership {

    private String    membershipId;
    private LocalDate startDate;
    private LocalDate endDate;
    private double    monthlyFee;
    private boolean   frozen;

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

    public void setMonthlyFee(double fee) {
        if (fee < 0) throw new IllegalArgumentException("Fee cannot be negative.");
        this.monthlyFee = fee;
    }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

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
