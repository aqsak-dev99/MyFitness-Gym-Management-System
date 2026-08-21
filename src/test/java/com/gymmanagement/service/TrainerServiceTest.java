package com.gymmanagement.service;

import com.gymmanagement.exception.MemberNotFoundException;
import com.gymmanagement.exception.SchedulingConflictException;
import com.gymmanagement.model.FullTimeStaff;
import com.gymmanagement.model.GymClass;
import com.gymmanagement.model.Instructor;
import com.gymmanagement.model.PartTimeStaff;
import com.gymmanagement.model.Staff;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests TrainerService in isolation. Unlike MemberService and
 * MembershipService, no fake repository is needed here — TrainerService
 * holds its staff lists directly in memory rather than through a
 * repository interface. Each test starts with three empty lists and adds
 * exactly the staff it needs.
 */
class TrainerServiceTest {

    private TrainerService trainerService;

    @BeforeEach
    void setUp() {
        trainerService = new TrainerService(
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    // ── helpers ───────────────────────────────────────────

    private FullTimeStaff fullTimeStaff(String staffId, String name) {
        return new FullTimeStaff("P" + staffId, staffId, name,
            name.toLowerCase() + "@myfitness.com", "0700000000",
            "Trainer", 2500.00, "Mon-Fri 09:00-17:00");
    }

    private PartTimeStaff partTimeStaff(String staffId, String name) {
        return new PartTimeStaff("P" + staffId, staffId, name,
            name.toLowerCase() + "@myfitness.com", "0700000000",
            "Cleaner", 12.50, 20, "Weekdays");
    }

    private Instructor instructor(String staffId, String name) {
        return new Instructor("P" + staffId, staffId, name,
            name.toLowerCase() + "@myfitness.com", "0700000000",
            2800.00, "Mon-Fri 07:00-15:00", "Full Body");
    }

    // ── adding staff + duplicate guard ────────────────────

    @Test
    void addingFullTimeStaffSucceeds() {
        trainerService.addFullTimeStaff(fullTimeStaff("FT001", "Emma"));

        assertEquals(1, trainerService.getAllFullTimeStaff().size());
    }

    @Test
    void addingStaffWithDuplicateIdAcrossDifferentTypesThrows() {
        // Duplicate check must cover ALL staff types combined, not just
        // within one list — an instructor and a part-timer sharing an ID
        // should be rejected just as much as two full-timers would be.
        trainerService.addFullTimeStaff(fullTimeStaff("S001", "Emma"));

        assertThrows(IllegalArgumentException.class, () ->
            trainerService.addInstructor(instructor("S001", "Carlos"))
        );
    }

    // ── retrieval + polymorphism ───────────────────────────

    @Test
    void getAllStaffCombinesAllThreeTypes() {
        trainerService.addFullTimeStaff(fullTimeStaff("FT001", "Emma"));
        trainerService.addPartTimeStaff(partTimeStaff("PT001", "Mike"));
        trainerService.addInstructor(instructor("INS001", "Carlos"));

        List<Staff> all = trainerService.getAllStaff();

        assertEquals(3, all.size());
    }

    @Test
    void gettingUnknownInstructorThrows() {
        assertThrows(MemberNotFoundException.class, () ->
            trainerService.getInstructorById("DOES_NOT_EXIST")
        );
    }

    // ── availability ──────────────────────────────────────

    @Test
    void fullTimeStaffAreAlwaysAvailable() {
        trainerService.addFullTimeStaff(fullTimeStaff("FT001", "Emma"));

        assertTrue(trainerService.getAvailableStaff().get(0).isAvailable());
    }

    // ── class assignment — both sides of the relationship ──

    @Test
    void assigningInstructorToClassUpdatesBothSides() {
        Instructor carlos = instructor("INS001", "Carlos");
        trainerService.addInstructor(carlos);
        GymClass spinClass = new GymClass("GC001", "Spin Class", "Tue 18:00", 15);

        trainerService.assignInstructorToClass("INS001", spinClass);

        // The class should know its instructor...
        assertEquals(carlos, spinClass.getInstructor());
        // ...and the instructor should know it's teaching that class.
        assertTrue(carlos.getAssignedClasses().contains(spinClass));
    }

    @Test
    void removingInstructorFromClassClearsBothSides() {
        Instructor carlos = instructor("INS001", "Carlos");
        trainerService.addInstructor(carlos);
        GymClass spinClass = new GymClass("GC001", "Spin Class", "Tue 18:00", 15);
        trainerService.assignInstructorToClass("INS001", spinClass);

        trainerService.removeInstructorFromClass("INS001", spinClass);

        assertEquals(null, spinClass.getInstructor());
        assertFalse(carlos.getAssignedClasses().contains(spinClass));
    }

    // ── scheduling conflicts ────────────────────────────────

    /** The core case this feature exists to prevent. */
    @Test
    void assigningInstructorToTwoClassesWithSameScheduleThrows() {
        Instructor carlos = instructor("INS001", "Carlos");
        trainerService.addInstructor(carlos);
        GymClass spinClass = new GymClass("GC001", "Spin Class", "Tue 18:00", 15);
        GymClass yogaClass = new GymClass("GC002", "Yoga Class", "Tue 18:00", 15);   // same schedule
        trainerService.assignInstructorToClass("INS001", spinClass);

        assertThrows(SchedulingConflictException.class, () ->
            trainerService.assignInstructorToClass("INS001", yogaClass));
    }

    @Test
    void assigningInstructorToTwoClassesWithDifferentSchedulesSucceeds() {
        Instructor carlos = instructor("INS001", "Carlos");
        trainerService.addInstructor(carlos);
        GymClass spinClass = new GymClass("GC001", "Spin Class", "Tue 18:00", 15);
        GymClass yogaClass = new GymClass("GC002", "Yoga Class", "Thu 18:00", 15);   // different day
        trainerService.assignInstructorToClass("INS001", spinClass);

        trainerService.assignInstructorToClass("INS001", yogaClass);

        assertTrue(carlos.getAssignedClasses().contains(spinClass));
        assertTrue(carlos.getAssignedClasses().contains(yogaClass));
    }

    /**
     * Regression guard: the conflict check must not break the existing
     * idempotent re-assignment behaviour (assigning the same instructor
     * to the same class twice was always meant to be a safe no-op, via
     * Instructor.assignToClass()'s own contains() guard). Without
     * excluding the class itself from the conflict comparison, this
     * would incorrectly throw — a class always shares its own schedule
     * with itself.
     */
    @Test
    void reassigningInstructorToTheSameClassDoesNotThrow() {
        Instructor carlos = instructor("INS001", "Carlos");
        trainerService.addInstructor(carlos);
        GymClass spinClass = new GymClass("GC001", "Spin Class", "Tue 18:00", 15);
        trainerService.assignInstructorToClass("INS001", spinClass);

        assertDoesNotThrow(() -> trainerService.assignInstructorToClass("INS001", spinClass));
    }
}