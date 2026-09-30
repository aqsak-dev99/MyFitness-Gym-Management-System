package com.gymmanagement.model;

public class FullTimeStaff extends Staff {

    private double salary;
    private String workSchedule;

    public FullTimeStaff(String personId, String staffId, String name,
                         String email, String phone, String role,
                         double salary, String workSchedule) {
        super(personId, staffId, name, email, phone, role);
        if (salary < 0)
            throw new IllegalArgumentException("Salary cannot be negative.");
        this.salary       = salary;
        this.workSchedule = workSchedule;
    }

    public double getSalary()       { return salary;       }
    public String getWorkSchedule() { return workSchedule; }

    public void setSalary(double salary) {
        if (salary < 0) throw new IllegalArgumentException("Salary cannot be negative.");
        this.salary = salary;
    }

    public void setWorkSchedule(String workSchedule) { this.workSchedule = workSchedule; }

    /**
     * Real bug found via the new deactivate-staff tests, not present
     * before they existed to catch it: this was hardcoded to always
     * return true, completely ignoring the real available field
     * inherited from Staff. setAvailable(false) correctly changed the
     * underlying data the whole time — nothing could ever read it back
     * as false. Instructor extends FullTimeStaff and inherits this same
     * fix automatically, since it has no override of its own.
     * PartTimeStaff already does this correctly (explicit
     * super.isAvailable() passthrough) — matching that same pattern here.
     */
    @Override
    public boolean isAvailable() { return super.isAvailable(); }

    @Override
    public String getDetails() {
        return "--- Full-Time Staff ---\n"
             + "Staff ID   : " + getStaffId()                          + "\n"
             + "Name       : " + getName()                              + "\n"
             + "Email      : " + getEmail()                             + "\n"
             + "Phone      : " + getPhone()                             + "\n"
             + "Role       : " + getRole()                              + "\n"
             + "Salary     : £" + String.format("%.2f", salary) + " / month\n"
             + "Schedule   : " + workSchedule                           + "\n"
             + "Available  : " + isAvailable();
    }
}