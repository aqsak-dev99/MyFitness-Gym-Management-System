package com.gymmanagement.model;

import java.util.ArrayList;
import java.util.List;

public class GymClass {

    private String        classId;
    private String        className;
    private String        schedule;
    private int           maxCapacity;
    private Instructor    instructor;
    private List<Member>  participants;

    public GymClass(String classId, String className,
                    String schedule, int maxCapacity) {
        if (classId == null || classId.isBlank())
            throw new IllegalArgumentException("Class ID cannot be empty.");
        this.classId      = classId;
        this.className    = className;
        this.schedule     = schedule;
        this.maxCapacity  = maxCapacity;
        this.participants = new ArrayList<>();
    }

    // ── getters / setters ─────────────────────────────────
    public String       getClassId()     { return classId;      }
    public String       getClassName()   { return className;    }
    public String       getSchedule()    { return schedule;     }
    public int          getMaxCapacity() { return maxCapacity;  }
    public Instructor   getInstructor()  { return instructor;   }
    public List<Member> getParticipants(){ return new ArrayList<>(participants); }
    public int  getCurrentEnrolments()   { return participants.size(); }
    public boolean isFull()              { return participants.size() >= maxCapacity; }

    public void setSchedule(String schedule)     { this.schedule    = schedule;    }
    public void setMaxCapacity(int maxCapacity)  { this.maxCapacity = maxCapacity; }

    // ── enrol / remove ────────────────────────────────────
    public boolean enrolMember(Member member) {
        if (member == null) return false;
        if (isFull())                            return false;
        if (participants.contains(member))       return false;
        participants.add(member);
        return true;
    }

    public boolean removeMember(Member member) {
        return participants.remove(member);
    }

    // ── instructor management ─────────────────────────────
    public void assignInstructor(Instructor instructor) {
        this.instructor = instructor;
    }

    public void removeInstructor() {
        this.instructor = null;
    }

    // ── getDetails ────────────────────────────────────────
    public String getDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append("Class ID    : ").append(classId).append("\n");
        sb.append("Name        : ").append(className).append("\n");
        sb.append("Schedule    : ").append(schedule).append("\n");
        sb.append("Capacity    : ").append(participants.size())
          .append(" / ").append(maxCapacity).append("\n");
        sb.append("Instructor  : ")
          .append(instructor != null ? instructor.getName() : "Not assigned").append("\n");
        sb.append("Participants: ");
        if (participants.isEmpty()) {
            sb.append("None");
        } else {
            participants.forEach(m -> sb.append(m.getName()).append(", "));
        }
        return sb.toString().trim();
    }

    @Override
    public String toString() { return getDetails(); }
}
