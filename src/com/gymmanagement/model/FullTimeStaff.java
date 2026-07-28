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

    @Override
    public boolean isAvailable() { return true; }

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
             + "Available  : true (full-time)";
    }
}
