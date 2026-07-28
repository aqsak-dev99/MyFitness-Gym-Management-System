package com.gymmanagement.model.membership;

import java.time.LocalDate;

public class StudentSaverMembership extends Membership {

    private static final double BASE_MONTHLY   = 19.89;
    private static final double DISCOUNT        = 0.20;
    private static final int    DURATION_MONTHS = 9;

    private String studentIdNumber;

    public StudentSaverMembership(String membershipId, String studentIdNumber) {
        super(membershipId,
              LocalDate.now(),
              LocalDate.now().plusMonths(DURATION_MONTHS),
              BASE_MONTHLY * (1 - DISCOUNT));
        if (studentIdNumber == null || studentIdNumber.isBlank())
            throw new IllegalArgumentException("Student ID is required.");
        this.studentIdNumber = studentIdNumber;
    }

    public String  getStudentIdNumber() { return studentIdNumber; }

    public boolean validateStudentId() {
        return studentIdNumber.startsWith("S") && studentIdNumber.length() >= 6;
    }

    @Override
    public double calcFee() { return getMonthlyFee(); }

    @Override
    public String getDetails() {
        return "--- Student Saver Membership ---\n"
             + "ID          : " + getMembershipId()                        + "\n"
             + "Student ID  : " + studentIdNumber                           + "\n"
             + "Start       : " + getStartDate()                            + "\n"
             + "End         : " + getEndDate()                              + "\n"
             + "Monthly Fee : £" + String.format("%.2f", calcFee())        + "\n"
             + "Days left   : " + getDaysRemaining()                        + "\n"
             + "Active      : " + isActive()                                + "\n"
             + "Valid ID    : " + validateStudentId();
    }
}
