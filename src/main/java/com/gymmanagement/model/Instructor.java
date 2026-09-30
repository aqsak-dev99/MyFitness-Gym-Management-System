package com.gymmanagement.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

    public String getSpecialisation() { return specialisation; }
    public void setSpecialisation(String specialisation) { this.specialisation = specialisation; }

    /**
     * @JsonIgnore — same fix, same reason as Payment.getMember() yesterday.
     * GymClass.getInstructor() returns this Instructor; this method returns
     * a list that includes that same GymClass back. Serializing a
     * BootcampClass (which extends GymClass) would otherwise walk
     * class → instructor → assignedClasses → the same class → instructor
     * → ... forever. Ignored here so "who teaches this class" stays visible
     * on the class response; "what classes does this instructor teach" is
     * suppressed rather than causing a cycle. If a future endpoint needs
     * that direction instead (e.g. GET /api/instructors/{id}), the two
     * competing needs are exactly when this should become a proper DTO
     * instead of another @JsonIgnore pointed the other way.
     */
    @JsonIgnore
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