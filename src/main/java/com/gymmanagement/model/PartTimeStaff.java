package com.gymmanagement.model;

public class PartTimeStaff extends Staff {

    private double hourlyRate;
    private int    hoursPerWeek;
    private String shiftPattern;

    public PartTimeStaff(String personId, String staffId, String name,
                         String email, String phone, String role,
                         double hourlyRate, int hoursPerWeek, String shiftPattern) {
        super(personId, staffId, name, email, phone, role);
        if (hourlyRate < 0)
            throw new IllegalArgumentException("Hourly rate cannot be negative.");
        this.hourlyRate   = hourlyRate;
        this.hoursPerWeek = hoursPerWeek;
        this.shiftPattern = shiftPattern;
    }

    public double getHourlyRate()   { return hourlyRate;   }
    public int    getHoursPerWeek() { return hoursPerWeek; }
    public String getShiftPattern() { return shiftPattern; }

    public void setHourlyRate(double rate) {
        if (rate < 0) throw new IllegalArgumentException("Rate cannot be negative.");
        this.hourlyRate = rate;
    }

    public void setHoursPerWeek(int hoursPerWeek) { this.hoursPerWeek = hoursPerWeek; }
    public void setShiftPattern(String shiftPattern) { this.shiftPattern = shiftPattern; }

    public double calcMonthlyEarnings() {
        return hourlyRate * hoursPerWeek * 4;
    }

    @Override
    public boolean isAvailable() { return super.isAvailable(); }

    @Override
    public String getDetails() {
        return "--- Part-Time Staff ---\n"
             + "Staff ID      : " + getStaffId()                                  + "\n"
             + "Name          : " + getName()                                       + "\n"
             + "Email         : " + getEmail()                                      + "\n"
             + "Phone         : " + getPhone()                                      + "\n"
             + "Role          : " + getRole()                                       + "\n"
             + "Hourly Rate   : £" + String.format("%.2f", hourlyRate)             + "\n"
             + "Hours / week  : " + hoursPerWeek                                    + "\n"
             + "Shift Pattern : " + shiftPattern                                    + "\n"
             + "Est. monthly  : £" + String.format("%.2f", calcMonthlyEarnings())  + "\n"
             + "Available     : " + isAvailable();
    }
}