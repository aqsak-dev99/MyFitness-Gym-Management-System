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
    private boolean       cancelled;   // soft state — see cancel()/reactivate()

    public GymClass(String classId, String className,
                    String schedule, int maxCapacity) {
        if (classId == null || classId.isBlank())
            throw new IllegalArgumentException("Class ID cannot be empty.");
        this.classId      = classId;
        this.className    = className;
        this.schedule     = schedule;
        this.maxCapacity  = maxCapacity;
        this.participants = new ArrayList<>();
        this.cancelled    = false;
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
    public boolean isCancelled()         { return cancelled;    }

    public void setSchedule(String schedule)     { this.schedule    = schedule;    }

    /**
     * Rejects shrinking capacity below the number of people already
     * enrolled — a genuine invalid state (more participants than the
     * class can hold), not just a cosmetic concern. Real enrolment
     * count, not a caller-supplied one, so this can't be bypassed by a
     * stale client value.
     */
    public void setMaxCapacity(int maxCapacity) {
        if (maxCapacity < participants.size())
            throw new IllegalArgumentException(
                "Cannot set capacity to " + maxCapacity + " — "
                + participants.size() + " members are already enrolled.");
        this.maxCapacity = maxCapacity;
    }

    // ── cancellation status ────────────────────────────────
    /**
     * Soft-cancellation, matching Member.deactivate()/reactivate() and
     * Membership.freeze()/unfreeze() — the class row is never deleted,
     * so existing enrolment history stays intact. Idempotent, same as
     * those other two.
     */
    public void cancel()     { this.cancelled = true;  }
    public void reactivate() { this.cancelled = false; }

    // ── enrol / remove ────────────────────────────────────
    public boolean enrolMember(Member member) {
        if (member == null)                      return false;
        if (cancelled)                            return false;
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