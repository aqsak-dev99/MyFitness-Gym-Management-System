package com.gymmanagement.model;

import java.util.ArrayList;
import java.util.List;

public class Instructor extends FullTimeStaff {

    private String         specialisation;
    private List<GymClass> assignedClasses;

    public Instructor(String personId, String staffId, String name,
                      String email, String phone, double salary,
                      String workSchedule, String specialisation) {
        super(personId, staffId, name, email, phone,
              "Fitness Instructor", salary, workSchedule);
        this.specialisation  = specialisation;
        this.assignedClasses = new ArrayList<>();
    }

    public String         getSpecialisation()  { return specialisation;              }
    public List<GymClass> getAssignedClasses() { return new ArrayList<>(assignedClasses); }

    public void assignToClass(GymClass gymClass) {
        if (!assignedClasses.contains(gymClass))
            assignedClasses.add(gymClass);
    }

    public void removeFromClass(GymClass gymClass) {
        assignedClasses.remove(gymClass);
    }

    @Override
    public String getDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- Instructor ---\n");
        sb.append("Staff ID        : ").append(getStaffId()).append("\n");
        sb.append("Name            : ").append(getName()).append("\n");
        sb.append("Email           : ").append(getEmail()).append("\n");
        sb.append("Phone           : ").append(getPhone()).append("\n");
        sb.append("Specialisation  : ").append(specialisation).append("\n");
        sb.append("Salary          : £").append(String.format("%.2f", getSalary()))
          .append(" / month\n");
        sb.append("Classes taught  : ");
        if (assignedClasses.isEmpty()) {
            sb.append("None assigned");
        } else {
            assignedClasses.forEach(c -> sb.append(c.getClassName()).append(", "));
        }
        return sb.toString().trim();
    }
}
