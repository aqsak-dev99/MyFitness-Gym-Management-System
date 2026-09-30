package com.gymmanagement.service;

import com.gymmanagement.exception.MemberNotFoundException;
import com.gymmanagement.exception.SchedulingConflictException;
import com.gymmanagement.model.FullTimeStaff;
import com.gymmanagement.model.GymClass;
import com.gymmanagement.model.Instructor;
import com.gymmanagement.model.PartTimeStaff;
import com.gymmanagement.model.Staff;
import com.gymmanagement.repository.FakeStaffRepository;

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
 * Tests TrainerService in isolation, using FakeStaffRepository —
 * same hand-rolled-fake pattern as MemberServiceTest, added once
 * TrainerService stopped being purely in-memory (see StaffRepository/
 * SqliteStaffRepository/StaffSeeder for the real persistence fix this
 * followed from). Each test starts with three empty lists and an empty
 * fake repository, and adds exactly the staff it needs.
 */
class TrainerServiceTest {

    private TrainerService trainerService;
    private FakeStaffRepository fakeStaffRepo;

    @BeforeEach
    void setUp() {
        fakeStaffRepo = new FakeStaffRepository();
        trainerService = new TrainerService(
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), fakeStaffRepo);
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

    // ── persistence — the actual bug this fix addresses ────

    @Test
    void addingInstructorPersistsToRepository() {
        trainerService.addInstructor(instructor("INS001", "Carlos"));

        assertTrue(fakeStaffRepo.findAllInstructors().stream()
            .anyMatch(i -> i.getStaffId().equals("INS001")));
    }

    @Test
    void addingFullTimeStaffPersistsToRepository() {
        trainerService.addFullTimeStaff(fullTimeStaff("FT001", "Emma"));

        assertTrue(fakeStaffRepo.findAllFullTimeStaff().stream()
            .anyMatch(s -> s.getStaffId().equals("FT001")));
    }

    @Test
    void addingPartTimeStaffPersistsToRepository() {
        trainerService.addPartTimeStaff(partTimeStaff("PT001", "Mike"));

        assertTrue(fakeStaffRepo.findAllPartTimeStaff().stream()
            .anyMatch(s -> s.getStaffId().equals("PT001")));
    }

    /**
     * The direct proof of the actual bug report: "a newly created
     * instructor must still exist after restarting Spring Boot."
     * A real restart isn't reproducible in a unit test, but constructing
     * a genuinely NEW TrainerService instance — the same thing Spring
     * does on every real boot — against the SAME backing repository is
     * the exact scenario that matters. Before this fix, the equivalent
     * fake would have been an empty StaffConfig bean every time,
     * regardless of what the first instance had added.
     */
    @Test
    void newTrainerServiceInstanceSeesStaffAddedByAPreviousInstance() {
        trainerService.addInstructor(instructor("INS001", "Carlos"));
        trainerService.addFullTimeStaff(fullTimeStaff("FT001", "Emma"));
        trainerService.addPartTimeStaff(partTimeStaff("PT001", "Mike"));

        // Simulates Spring re-constructing the bean on a fresh boot —
        // loading from the repository exactly as StaffConfig's real
        // @Bean methods do.
        TrainerService afterRestart = new TrainerService(
            fakeStaffRepo.findAllFullTimeStaff(),
            fakeStaffRepo.findAllPartTimeStaff(),
            fakeStaffRepo.findAllInstructors(),
            fakeStaffRepo);

        assertEquals(1, afterRestart.getAllInstructors().size());
        assertEquals("Carlos", afterRestart.getAllInstructors().get(0).getName());
        assertEquals(1, afterRestart.getAllFullTimeStaff().size());
        assertEquals(1, afterRestart.getAllPartTimeStaff().size());
    }

    // ── editing staff ───────────────────────────────────────

    @Test
    void updatingFullTimeStaffPersistsAllFieldsIncludingRole() {
        trainerService.addFullTimeStaff(fullTimeStaff("FT001", "Emma"));

        FullTimeStaff updated = trainerService.updateFullTimeStaff(
            "FT001", "Emma Clarke", "emma.c@myfitness.com", "07700000099",
            "Operations Manager", 3200.00, "Mon-Fri 08:00-16:00");

        assertEquals("Emma Clarke", updated.getName());
        assertEquals("Operations Manager", updated.getRole());
        assertEquals(3200.00, updated.getSalary(), 0.001);

        // Real persistence check via the fake repository directly.
        FullTimeStaff persisted = fakeStaffRepo.findAllFullTimeStaff().stream()
            .filter(s -> s.getStaffId().equals("FT001")).findFirst().orElseThrow();
        assertEquals("Operations Manager", persisted.getRole());
    }

    @Test
    void updatingPartTimeStaffPersistsAllFields() {
        trainerService.addPartTimeStaff(partTimeStaff("PT001", "Mike"));

        PartTimeStaff updated = trainerService.updatePartTimeStaff(
            "PT001", "Mike Lee", "mike.lee@myfitness.com", "07700000098",
            "Senior Cleaner", 15.00, 25, "Weekends");

        assertEquals(15.00, updated.getHourlyRate(), 0.001);
        assertEquals(25, updated.getHoursPerWeek());
        assertEquals("Weekends", updated.getShiftPattern());
    }

    @Test
    void updatingInstructorPersistsSpecialisation() {
        trainerService.addInstructor(instructor("INS001", "Carlos"));

        Instructor updated = trainerService.updateInstructor(
            "INS001", "Carlos Ruiz", "carlos.ruiz@myfitness.com", "07700000097",
            2900.00, "Mon-Fri 06:00-14:00", "Strength & Conditioning");

        assertEquals("Strength & Conditioning", updated.getSpecialisation());
    }

    @Test
    void updatingUnknownStaffMemberThrows() {
        assertThrows(MemberNotFoundException.class, () ->
            trainerService.updateFullTimeStaff("DOES_NOT_EXIST", "X", "x@x.com", "0", "Role", 1000, "Sched")
        );
    }

    // ── activation status ──────────────────────────────────

    @Test
    void newStaffIsAvailableByDefault() {
        Instructor i = instructor("INS001", "Carlos");
        trainerService.addInstructor(i);
        assertTrue(i.isAvailable());
    }

    @Test
    void deactivatingInstructorPersistsRealUnavailableState() {
        trainerService.addInstructor(instructor("INS001", "Carlos"));

        trainerService.deactivateStaff("INS001");

        Instructor persisted = fakeStaffRepo.findAllInstructors().stream()
            .filter(s -> s.getStaffId().equals("INS001")).findFirst().orElseThrow();
        assertFalse(persisted.isAvailable());
    }

    @Test
    void deactivateStaffFindsFullTimeStaffCorrectly() {
        trainerService.addFullTimeStaff(fullTimeStaff("FT001", "Emma"));

        Staff result = trainerService.deactivateStaff("FT001");

        assertFalse(result.isAvailable());
    }

    @Test
    void deactivateStaffFindsPartTimeStaffCorrectly() {
        trainerService.addPartTimeStaff(partTimeStaff("PT001", "Mike"));

        Staff result = trainerService.deactivateStaff("PT001");

        assertFalse(result.isAvailable());
    }

    @Test
    void reactivatingStaffRestoresAvailability() {
        trainerService.addInstructor(instructor("INS001", "Carlos"));
        trainerService.deactivateStaff("INS001");

        Staff result = trainerService.reactivateStaff("INS001");

        assertTrue(result.isAvailable());
    }

    @Test
    void deactivatingUnknownStaffIdThrows() {
        assertThrows(MemberNotFoundException.class, () ->
            trainerService.deactivateStaff("DOES_NOT_EXIST")
        );
    }
}