package com.gymmanagement.model;

import com.gymmanagement.model.membership.BootcampType;
import com.gymmanagement.model.membership.IBootcampFee;

public class BootcampClass extends GymClass implements IBootcampFee {

    private static final int DURATION_DAYS = 30;

    private BootcampType type;
    private int          durationDays;

    public BootcampClass(String classId, BootcampType type,
                         String schedule, int maxCapacity) {
        super(classId, "Bootcamp: " + type.getDisplayName(), schedule, maxCapacity);
        this.type         = type;
        this.durationDays = DURATION_DAYS;
    }

    public BootcampType getType()         { return type;         }
    public int          getDurationDays() { return durationDays; }

    // ── IBootcampFee implementation ───────────────────────
    @Override
    public double calcBootcampFee(int classesEnrolled) {
        double fee = BASE_FEE;
        if (classesEnrolled >= 2) fee = applyDiscount(fee);
        return fee;
    }

    @Override
    public double applyDiscount(double originalFee) {
        return originalFee * (1 - DISCOUNT_RATE);
    }

    // ── getDetails ────────────────────────────────────────
    @Override
    public String getDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- Bootcamp Class ---\n");
        sb.append("Class ID      : ").append(getClassId()).append("\n");
        sb.append("Type          : ").append(type.getDisplayName()).append("\n");
        sb.append("Schedule      : ").append(getSchedule()).append("\n");
        sb.append("Duration      : ").append(durationDays).append(" days\n");
        sb.append("Capacity      : ").append(getCurrentEnrolments())
          .append(" / ").append(getMaxCapacity()).append("\n");
        sb.append("Instructor    : ")
          .append(getInstructor() != null ? getInstructor().getName() : "Not assigned").append("\n");
        sb.append("Base fee      : £").append(String.format("%.2f", BASE_FEE)).append(" / month\n");
        sb.append("Fee (1 class) : £").append(String.format("%.2f", calcBootcampFee(1))).append("\n");
        sb.append("Fee (2+ cls)  : £").append(String.format("%.2f", applyDiscount(BASE_FEE)))
          .append(" (7% off)\n");
        sb.append("Participants  : ");
        if (getParticipants().isEmpty()) {
            sb.append("None");
        } else {
            getParticipants().forEach(m -> sb.append(m.getName()).append(", "));
        }
        return sb.toString().trim();
    }
}
