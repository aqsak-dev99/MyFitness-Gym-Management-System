package com.gymmanagement.service;

import com.gymmanagement.exception.MemberNotFoundException;
import com.gymmanagement.exception.SchedulingConflictException;
import com.gymmanagement.model.FullTimeStaff;
import com.gymmanagement.model.GymClass;
import com.gymmanagement.model.Instructor;
import com.gymmanagement.model.PartTimeStaff;
import com.gymmanagement.model.Staff;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TrainerService owns every business rule that involves Staff,
 * FullTimeStaff, PartTimeStaff, and Instructor objects.
 *
 * Rules enforced here:
 *  - Staff IDs must be unique across the combined staff roster.
 *  - Only an Instructor (not generic Staff) can be assigned to a class.
 *  - Availability is derived from the staff type at query time.
 *
 * No System.out calls. No ArrayList exposed outside this class.
 * Lists are injected at construction so this service is fully testable.
 *
 * @Service — Spring auto-wires the three List constructor parameters from
 * the beans already defined in StaffConfig (built specifically ahead of
 * this, when MembershipService was wired up).
 */
@Service
public class TrainerService {

    private final List<FullTimeStaff> fullTimeStaff;
    private final List<PartTimeStaff> partTimeStaff;
    private final List<Instructor>    instructors;

    public TrainerService(List<FullTimeStaff> fullTimeStaff,
                          List<PartTimeStaff> partTimeStaff,
                          List<Instructor>    instructors) {
        this.fullTimeStaff = new ArrayList<>(fullTimeStaff);
        this.partTimeStaff = new ArrayList<>(partTimeStaff);
        this.instructors   = new ArrayList<>(instructors);
    }

    // ── add staff ─────────────────────────────────────────

    public void addFullTimeStaff(FullTimeStaff staff) {
        if (staffIdExists(staff.getStaffId()))
            throw new IllegalArgumentException(
                "Staff ID already in use: " + staff.getStaffId());
        fullTimeStaff.add(staff);
    }

    public void addPartTimeStaff(PartTimeStaff staff) {
        if (staffIdExists(staff.getStaffId()))
            throw new IllegalArgumentException(
                "Staff ID already in use: " + staff.getStaffId());
        partTimeStaff.add(staff);
    }

    public void addInstructor(Instructor instructor) {
        if (staffIdExists(instructor.getStaffId()))
            throw new IllegalArgumentException(
                "Staff ID already in use: " + instructor.getStaffId());
        instructors.add(instructor);
    }

    // ── retrieval ─────────────────────────────────────────

    public List<FullTimeStaff> getAllFullTimeStaff()  { return new ArrayList<>(fullTimeStaff); }
    public List<PartTimeStaff> getAllPartTimeStaff()  { return new ArrayList<>(partTimeStaff); }
    public List<Instructor>    getAllInstructors()    { return new ArrayList<>(instructors);   }

    /** Returns every person on the payroll regardless of type. */
    public List<Staff> getAllStaff() {
        return Stream.of(fullTimeStaff, partTimeStaff, instructors)
                     .flatMap(List::stream)
                     .collect(Collectors.toList());
    }

    public Instructor getInstructorById(String staffId) {
        return instructors.stream()
                          .filter(i -> i.getStaffId().equals(staffId))
                          .findFirst()
                          .orElseThrow(() -> new MemberNotFoundException(
                              "No instructor found with ID: " + staffId));
    }

    // ── class assignment ──────────────────────────────────

    /**
     * Assign an instructor to a gym class.
     * Both sides of the relationship are updated atomically.
     */
    /**
     * Assign an instructor to a gym class. Rejects the assignment if the
     * instructor is already teaching a DIFFERENT class at the exact same
     * schedule string — a genuine, real scheduling conflict, not just a
     * data-integrity nicety. Excludes the class being assigned itself
     * from this check (matched by classId, not object identity) so a
     * repeat call assigning the same instructor to the same class stays
     * the safe no-op it already was via Instructor.assignToClass()'s own
     * idempotency guard, rather than being newly (and wrongly) treated
     * as a conflict with itself.
     *
     * Deliberately exact-string comparison, not semantic day/time
     * parsing — schedule is free text ("Mon/Wed 07:00"), and building
     * real time-overlap logic would be a much larger feature than this
     * check calls for. Matches the same granularity the data already has.
     */
    public void assignInstructorToClass(String instructorId, GymClass gymClass) {
        Instructor instructor = getInstructorById(instructorId);

        boolean hasConflict = instructor.getAssignedClasses().stream()
            .filter(existingClass -> !existingClass.getClassId().equals(gymClass.getClassId()))
            .anyMatch(existingClass -> existingClass.getSchedule().equals(gymClass.getSchedule()));

        if (hasConflict) {
            throw new SchedulingConflictException(
                "Instructor " + instructor.getName() + " is already teaching a class at \"" +
                gymClass.getSchedule() + "\" — cannot assign to another class at the same time.");
        }

        gymClass.assignInstructor(instructor);
        instructor.assignToClass(gymClass);
    }

    /**
     * Remove an instructor from a gym class.
     * Both sides of the relationship are cleared.
     */
    public void removeInstructorFromClass(String instructorId, GymClass gymClass) {
        Instructor instructor = getInstructorById(instructorId);
        gymClass.removeInstructor();
        instructor.removeFromClass(gymClass);
    }

    // ── availability ──────────────────────────────────────

    /** Returns all staff members who are currently available. */
    public List<Staff> getAvailableStaff() {
        return getAllStaff().stream()
                           .filter(Staff::isAvailable)
                           .collect(Collectors.toList());
    }

    // ── internal helpers ──────────────────────────────────

    private boolean staffIdExists(String staffId) {
        return getAllStaff().stream()
                            .anyMatch(s -> s.getStaffId().equals(staffId));
    }
}