package com.gymmanagement.controller;

import com.gymmanagement.config.RequireRole;
import com.gymmanagement.model.FullTimeStaff;
import com.gymmanagement.model.Instructor;
import com.gymmanagement.model.PartTimeStaff;
import com.gymmanagement.model.Role;
import com.gymmanagement.model.Staff;
import com.gymmanagement.service.TrainerService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TrainerController — the HTTP-facing layer for staff and instructors.
 * Same pattern as MemberController and MembershipController: translates
 * HTTP requests into calls on TrainerService, no business logic here.
 */
@RestController
@RequestMapping("/api")
public class TrainerController {

    private final TrainerService trainerService;

    public TrainerController(TrainerService trainerService) {
        this.trainerService = trainerService;
    }

    // ══════════════════════════════════════════════════════
    //  Staff — all types combined
    // ══════════════════════════════════════════════════════

    @GetMapping("/staff")
    public List<Staff> getAllStaff() {
        return trainerService.getAllStaff();
    }

    @GetMapping("/staff/available")
    public List<Staff> getAvailableStaff() {
        return trainerService.getAvailableStaff();
    }

    // ══════════════════════════════════════════════════════
    //  Full-time staff
    // ══════════════════════════════════════════════════════

    @GetMapping("/staff/full-time")
    public List<FullTimeStaff> getAllFullTimeStaff() {
        return trainerService.getAllFullTimeStaff();
    }

    @PostMapping("/staff/full-time")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRole(Role.ADMIN)
    public FullTimeStaff addFullTimeStaff(@Valid @RequestBody FullTimeStaffRequest request) {
        FullTimeStaff staff = new FullTimeStaff(
            request.personId(), request.staffId(), request.name(),
            request.email(), request.phone(), request.role(),
            request.salary(), request.workSchedule()
        );
        trainerService.addFullTimeStaff(staff);
        return staff;
    }

    @PatchMapping("/staff/full-time/{staffId}")
    @RequireRole(Role.ADMIN)
    public FullTimeStaff updateFullTimeStaff(@PathVariable String staffId,
                                             @Valid @RequestBody UpdateFullTimeStaffRequest request) {
        return trainerService.updateFullTimeStaff(
            staffId, request.name(), request.email(), request.phone(),
            request.role(), request.salary(), request.workSchedule());
    }

    // ══════════════════════════════════════════════════════
    //  Part-time staff
    // ══════════════════════════════════════════════════════

    @GetMapping("/staff/part-time")
    public List<PartTimeStaff> getAllPartTimeStaff() {
        return trainerService.getAllPartTimeStaff();
    }

    @PostMapping("/staff/part-time")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRole(Role.ADMIN)
    public PartTimeStaff addPartTimeStaff(@Valid @RequestBody PartTimeStaffRequest request) {
        PartTimeStaff staff = new PartTimeStaff(
            request.personId(), request.staffId(), request.name(),
            request.email(), request.phone(), request.role(),
            request.hourlyRate(), request.hoursPerWeek(), request.shiftPattern()
        );
        trainerService.addPartTimeStaff(staff);
        return staff;
    }

    @PatchMapping("/staff/part-time/{staffId}")
    @RequireRole(Role.ADMIN)
    public PartTimeStaff updatePartTimeStaff(@PathVariable String staffId,
                                             @Valid @RequestBody UpdatePartTimeStaffRequest request) {
        return trainerService.updatePartTimeStaff(
            staffId, request.name(), request.email(), request.phone(), request.role(),
            request.hourlyRate(), request.hoursPerWeek(), request.shiftPattern());
    }

    // ══════════════════════════════════════════════════════
    //  Instructors
    // ══════════════════════════════════════════════════════

    @GetMapping("/instructors")
    public List<Instructor> getAllInstructors() {
        return trainerService.getAllInstructors();
    }

    @GetMapping("/instructors/{staffId}")
    public Instructor getInstructor(@PathVariable String staffId) {
        return trainerService.getInstructorById(staffId);
    }

    @PostMapping("/instructors")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRole(Role.ADMIN)
    public Instructor addInstructor(@Valid @RequestBody InstructorRequest request) {
        Instructor instructor = new Instructor(
            request.personId(), request.staffId(), request.name(),
            request.email(), request.phone(), request.salary(),
            request.workSchedule(), request.specialisation()
        );
        trainerService.addInstructor(instructor);
        return instructor;
    }

    @PatchMapping("/instructors/{staffId}")
    @RequireRole(Role.ADMIN)
    public Instructor updateInstructor(@PathVariable String staffId,
                                       @Valid @RequestBody UpdateInstructorRequest request) {
        return trainerService.updateInstructor(
            staffId, request.name(), request.email(), request.phone(),
            request.salary(), request.workSchedule(), request.specialisation());
    }

    // ══════════════════════════════════════════════════════
    //  Activation status — unified across all three types, since
    //  deactivation only ever flips one shared boolean (Staff.available)
    // ══════════════════════════════════════════════════════

    @PatchMapping("/staff/{staffId}/deactivate")
    @RequireRole(Role.ADMIN)
    public Staff deactivateStaff(@PathVariable String staffId) {
        return trainerService.deactivateStaff(staffId);
    }

    @PatchMapping("/staff/{staffId}/reactivate")
    @RequireRole(Role.ADMIN)
    public Staff reactivateStaff(@PathVariable String staffId) {
        return trainerService.reactivateStaff(staffId);
    }

    // ── request DTOs ──────────────────────────────────────

    public record FullTimeStaffRequest(
        @NotBlank String personId, @NotBlank String staffId, @NotBlank String name,
        @NotBlank String email, @NotBlank String phone, @NotBlank String role,
        @Positive double salary, @NotBlank String workSchedule
    ) {}

    public record PartTimeStaffRequest(
        @NotBlank String personId, @NotBlank String staffId, @NotBlank String name,
        @NotBlank String email, @NotBlank String phone, @NotBlank String role,
        @Positive double hourlyRate, @Positive int hoursPerWeek, @NotBlank String shiftPattern
    ) {}

    public record InstructorRequest(
        @NotBlank String personId, @NotBlank String staffId, @NotBlank String name,
        @NotBlank String email, @NotBlank String phone, @Positive double salary,
        @NotBlank String workSchedule, @NotBlank String specialisation
    ) {}

    public record UpdateFullTimeStaffRequest(
        @NotBlank String name, @NotBlank String email, @NotBlank String phone,
        @NotBlank String role, @Positive double salary, @NotBlank String workSchedule
    ) {}

    public record UpdatePartTimeStaffRequest(
        @NotBlank String name, @NotBlank String email, @NotBlank String phone,
        @NotBlank String role, @Positive double hourlyRate, @Positive int hoursPerWeek,
        @NotBlank String shiftPattern
    ) {}

    public record UpdateInstructorRequest(
        @NotBlank String name, @NotBlank String email, @NotBlank String phone,
        @Positive double salary, @NotBlank String workSchedule, @NotBlank String specialisation
    ) {}
}