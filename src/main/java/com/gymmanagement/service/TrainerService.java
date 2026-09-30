package com.gymmanagement.service;

import com.gymmanagement.exception.MemberNotFoundException;
import com.gymmanagement.exception.SchedulingConflictException;
import com.gymmanagement.model.FullTimeStaff;
import com.gymmanagement.model.GymClass;
import com.gymmanagement.model.Instructor;
import com.gymmanagement.model.PartTimeStaff;
import com.gymmanagement.model.Staff;
import com.gymmanagement.repository.StaffRepository;

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
 * Persistence model (added when the original in-memory-only design was
 * found to never survive a restart — see StaffRepository/
 * SqliteStaffRepository/StaffSeeder for the full fix):
 *
 * This service still holds its three lists in memory and mutates them
 * directly, exactly as before — assignInstructorToClass()'s conflict
 * check and every other read here is completely unchanged. What's new
 * is that the constructor now seeds those in-memory lists from
 * staffRepo (a real database table) instead of StaffConfig's hardcoded
 * beans, and add*() now writes through to staffRepo in addition to the
 * in-memory list. The in-memory lists remain the single source of
 * truth *during* a running process — staffRepo is only consulted at
 * startup and on write, deliberately not on every read, to avoid a
 * mutual-recursion risk with SqliteBootcampRepository (see
 * SqliteStaffRepository's class-level comment for the full reasoning).
 */
@Service
public class TrainerService {

    private final List<FullTimeStaff> fullTimeStaff;
    private final List<PartTimeStaff> partTimeStaff;
    private final List<Instructor>    instructors;
    private final StaffRepository     staffRepo;

    public TrainerService(List<FullTimeStaff> fullTimeStaff,
                          List<PartTimeStaff> partTimeStaff,
                          List<Instructor>    instructors,
                          StaffRepository     staffRepo) {
        this.fullTimeStaff = new ArrayList<>(fullTimeStaff);
        this.partTimeStaff = new ArrayList<>(partTimeStaff);
        this.instructors   = new ArrayList<>(instructors);
        this.staffRepo     = staffRepo;
    }

    // ── add staff ─────────────────────────────────────────

    public void addFullTimeStaff(FullTimeStaff staff) {
        if (staffIdExists(staff.getStaffId()))
            throw new IllegalArgumentException(
                "Staff ID already in use: " + staff.getStaffId());
        fullTimeStaff.add(staff);
        staffRepo.saveFullTimeStaff(staff);
    }

    public void addPartTimeStaff(PartTimeStaff staff) {
        if (staffIdExists(staff.getStaffId()))
            throw new IllegalArgumentException(
                "Staff ID already in use: " + staff.getStaffId());
        partTimeStaff.add(staff);
        staffRepo.savePartTimeStaff(staff);
    }

    public void addInstructor(Instructor instructor) {
        if (staffIdExists(instructor.getStaffId()))
            throw new IllegalArgumentException(
                "Staff ID already in use: " + instructor.getStaffId());
        instructors.add(instructor);
        staffRepo.saveInstructor(instructor);
    }

    // ── edit staff ────────────────────────────────────────
    // Three type-specific methods, mirroring add*()'s existing shape —
    // each type has genuinely different editable fields (specialisation
    // only applies to instructors, hourlyRate only to part-time), so a
    // single generic method would need type-checking anyway. Every
    // method here fetches the SAME live in-memory object the rest of
    // this service uses (not a fresh copy), mutates it via the model's
    // own setters, then persists via the exact same upsert-based
    // save*() the add* methods already use.

    public FullTimeStaff updateFullTimeStaff(String staffId, String name, String email,
                                              String phone, String role, double salary,
                                              String workSchedule) {
        FullTimeStaff staff = findFullTimeStaffOrThrow(staffId);
        staff.setName(name);
        staff.setEmail(email);
        staff.setPhone(phone);
        staff.setRole(role);
        staff.setSalary(salary);
        staff.setWorkSchedule(workSchedule);
        staffRepo.saveFullTimeStaff(staff);
        return staff;
    }

    public PartTimeStaff updatePartTimeStaff(String staffId, String name, String email,
                                             String phone, String role, double hourlyRate,
                                             int hoursPerWeek, String shiftPattern) {
        PartTimeStaff staff = findPartTimeStaffOrThrow(staffId);
        staff.setName(name);
        staff.setEmail(email);
        staff.setPhone(phone);
        staff.setRole(role);
        staff.setHourlyRate(hourlyRate);
        staff.setHoursPerWeek(hoursPerWeek);
        staff.setShiftPattern(shiftPattern);
        staffRepo.savePartTimeStaff(staff);
        return staff;
    }

    public Instructor updateInstructor(String staffId, String name, String email,
                                       String phone, double salary, String workSchedule,
                                       String specialisation) {
        Instructor instructor = findInstructorOrThrow(staffId);
        instructor.setName(name);
        instructor.setEmail(email);
        instructor.setPhone(phone);
        instructor.setSalary(salary);
        instructor.setWorkSchedule(workSchedule);
        instructor.setSpecialisation(specialisation);
        staffRepo.saveInstructor(instructor);
        return instructor;
    }

    // ── activation status ──────────────────────────────────
    /**
     * Unified across all three types (unlike edit above) — deactivation
     * only ever flips one shared boolean (Staff.available), so there's
     * no type-specific field to justify three separate methods here.
     * Searches all three in-memory lists for the matching staffId, then
     * persists via whichever save*() method matches the real type found.
     */
    public Staff deactivateStaff(String staffId) {
        return setAvailability(staffId, false);
    }

    public Staff reactivateStaff(String staffId) {
        return setAvailability(staffId, true);
    }

    private Staff setAvailability(String staffId, boolean available) {
        for (Instructor i : instructors) {
            if (i.getStaffId().equals(staffId)) {
                i.setAvailable(available);
                staffRepo.saveInstructor(i);
                return i;
            }
        }
        for (FullTimeStaff s : fullTimeStaff) {
            if (s.getStaffId().equals(staffId)) {
                s.setAvailable(available);
                staffRepo.saveFullTimeStaff(s);
                return s;
            }
        }
        for (PartTimeStaff s : partTimeStaff) {
            if (s.getStaffId().equals(staffId)) {
                s.setAvailable(available);
                staffRepo.savePartTimeStaff(s);
                return s;
            }
        }
        throw new MemberNotFoundException("No staff member found with ID: " + staffId);
    }

    private FullTimeStaff findFullTimeStaffOrThrow(String staffId) {
        return fullTimeStaff.stream()
            .filter(s -> s.getStaffId().equals(staffId))
            .findFirst()
            .orElseThrow(() -> new MemberNotFoundException("No full-time staff found with ID: " + staffId));
    }

    private PartTimeStaff findPartTimeStaffOrThrow(String staffId) {
        return partTimeStaff.stream()
            .filter(s -> s.getStaffId().equals(staffId))
            .findFirst()
            .orElseThrow(() -> new MemberNotFoundException("No part-time staff found with ID: " + staffId));
    }

    private Instructor findInstructorOrThrow(String staffId) {
        return instructors.stream()
            .filter(i -> i.getStaffId().equals(staffId))
            .findFirst()
            .orElseThrow(() -> new MemberNotFoundException("No instructor found with ID: " + staffId));
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