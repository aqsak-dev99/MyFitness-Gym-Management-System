package com.gymmanagement.service;

import com.gymmanagement.exception.MemberNotFoundException;
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
    public void assignInstructorToClass(String instructorId, GymClass gymClass) {
        Instructor instructor = getInstructorById(instructorId);
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