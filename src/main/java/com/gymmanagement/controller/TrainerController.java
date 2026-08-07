package com.gymmanagement.controller;

import com.gymmanagement.model.FullTimeStaff;
import com.gymmanagement.model.Instructor;
import com.gymmanagement.model.PartTimeStaff;
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
    public FullTimeStaff addFullTimeStaff(@Valid @RequestBody FullTimeStaffRequest request) {
        FullTimeStaff staff = new FullTimeStaff(
            request.personId(), request.staffId(), request.name(),
            request.email(), request.phone(), request.role(),
            request.salary(), request.workSchedule()
        );
        trainerService.addFullTimeStaff(staff);
        return staff;
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
    public PartTimeStaff addPartTimeStaff(@Valid @RequestBody PartTimeStaffRequest request) {
        PartTimeStaff staff = new PartTimeStaff(
            request.personId(), request.staffId(), request.name(),
            request.email(), request.phone(), request.role(),
            request.hourlyRate(), request.hoursPerWeek(), request.shiftPattern()
        );
        trainerService.addPartTimeStaff(staff);
        return staff;
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
    public Instructor addInstructor(@Valid @RequestBody InstructorRequest request) {
        Instructor instructor = new Instructor(
            request.personId(), request.staffId(), request.name(),
            request.email(), request.phone(), request.salary(),
            request.workSchedule(), request.specialisation()
        );
        trainerService.addInstructor(instructor);
        return instructor;
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
}