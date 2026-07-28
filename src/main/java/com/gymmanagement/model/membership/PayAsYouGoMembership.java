package com.gymmanagement.model.membership;

import java.time.LocalDate;

public class PayAsYouGoMembership extends Membership {

    private static final double FEE_PER_SESSION = 7.99;
    private static final int    MAX_SESSIONS    = 30;

    private int sessionsUsed;

    public PayAsYouGoMembership(String membershipId) {
        super(membershipId,
              LocalDate.now(),
              LocalDate.now().plusYears(1),
              FEE_PER_SESSION);
        this.sessionsUsed = 0;
    }

    public int    getSessionsUsed()      { return sessionsUsed;              }
    public int    getSessionsRemaining() { return MAX_SESSIONS - sessionsUsed; }
    public double getFeePerSession()     { return FEE_PER_SESSION;           }

    // ── session recording (called by MembershipService) ──
    public boolean addSession() {
        if (sessionsUsed >= MAX_SESSIONS) return false;
        sessionsUsed++;
        return true;
    }

    @Override
    public double calcFee() { return sessionsUsed * FEE_PER_SESSION; }

    @Override
    public String getDetails() {
        return "--- Pay-As-You-Go Membership ---\n"
             + "ID              : " + getMembershipId()                          + "\n"
             + "Start           : " + getStartDate()                              + "\n"
             + "End             : " + getEndDate()                                + "\n"
             + "Sessions used   : " + sessionsUsed                                + "\n"
             + "Sessions left   : " + getSessionsRemaining()                      + "\n"
             + "Fee / session   : £" + String.format("%.2f", FEE_PER_SESSION)    + "\n"
             + "Total charged   : £" + String.format("%.2f", calcFee())           + "\n"
             + "Active          : " + isActive();
    }
}
