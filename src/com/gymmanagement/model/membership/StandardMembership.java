package com.gymmanagement.model.membership;

import java.time.LocalDate;

public class StandardMembership extends Membership {

    private static final double MONTHLY_FEE = 20.99;
    private static final double JOINING_FEE = 5.00;

    private double joiningFee;
    private int    freezeCount;

    public StandardMembership(String membershipId, int durationMonths) {
        super(membershipId,
              LocalDate.now(),
              LocalDate.now().plusMonths(durationMonths),
              MONTHLY_FEE);
        this.joiningFee  = JOINING_FEE;
        this.freezeCount = 0;
    }

    public double getJoiningFee()  { return joiningFee;  }
    public int    getFreezeCount() { return freezeCount; }

    @Override
    public void freeze() {
        super.freeze();
        freezeCount++;
    }

    @Override
    public double calcFee() { return joiningFee + getMonthlyFee(); }

    @Override
    public String getDetails() {
        return "--- Standard Membership ---\n"
             + "ID          : " + getMembershipId()                          + "\n"
             + "Start       : " + getStartDate()                              + "\n"
             + "End         : " + getEndDate()                                + "\n"
             + "Monthly Fee : £" + String.format("%.2f", getMonthlyFee())    + "\n"
             + "Joining Fee : £" + String.format("%.2f", joiningFee)         + "\n"
             + "First month : £" + String.format("%.2f", calcFee())           + "\n"
             + "Days left   : " + getDaysRemaining()                          + "\n"
             + "Frozen      : " + isFrozen()                                  + "\n"
             + "Active      : " + isActive();
    }
}
