package com.gymmanagement.controller;

import com.gymmanagement.model.BootcampClass;
import com.gymmanagement.model.Payment;
import com.gymmanagement.model.membership.BootcampType;
import com.gymmanagement.model.membership.Membership;
import com.gymmanagement.service.MembershipService;
import com.gymmanagement.service.TrainerService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MembershipController — the HTTP-facing layer for bootcamp classes and
 * membership status. Same pattern as MemberController: this class only
 * translates HTTP requests into calls on MembershipService, which already
 * has 9 passing JUnit tests behind it.
 *
 * Three resource groups live here:
 *   /api/bootcamp-classes/...          — bootcamp class CRUD + enrolment
 *   /api/bootcamp-classes/{id}/instructor — assign/remove an instructor
 *   /api/members/{memberId}/membership — membership status + actions
 *
 * The instructor-assignment endpoints needed a second dependency
 * (TrainerService) added here, since assigning an instructor genuinely
 * spans two services: TrainerService owns "does this instructor exist,"
 * MembershipService owns "here's the actual class to assign them to."
 * The URL naturally belongs under /api/bootcamp-classes, which this
 * controller already owns, rather than splitting it into TrainerController.
 */
@RestController
public class MembershipController {

    private final MembershipService membershipService;
    private final TrainerService    trainerService;

    public MembershipController(MembershipService membershipService,
                                TrainerService     trainerService) {
        this.membershipService = membershipService;
        this.trainerService    = trainerService;
    }

    // ══════════════════════════════════════════════════════
    //  Bootcamp classes
    // ══════════════════════════════════════════════════════

    @GetMapping("/api/bootcamp-classes")
    public List<BootcampClass> getAllBootcampClasses() {
        return membershipService.getAllBootcampClasses();
    }

    @GetMapping("/api/bootcamp-classes/{classId}")
    public BootcampClass getBootcampClass(@PathVariable String classId) {
        return membershipService.getBootcampById(classId);
    }

    @PostMapping("/api/bootcamp-classes")
    @ResponseStatus(HttpStatus.CREATED)
    public BootcampClass createBootcampClass(@RequestBody CreateBootcampClassRequest request) {
        BootcampClass bc = new BootcampClass(
            request.classId(), request.type(), request.schedule(), request.maxCapacity()
        );
        membershipService.addBootcampClass(bc);
        return bc;
    }

    /**
     * POST /api/bootcamp-classes/{classId}/enrolments
     *
     * Deliberately combines two service calls — enrolInBootcamp() then
     * processPayment() — into one HTTP action. This isn't business logic
     * living in the controller (the discount calculation, capacity check,
     * and payment ceiling all still live entirely in MembershipService,
     * unchanged). It's a judgement call about what one HTTP request should
     * represent: from a member's perspective, "enrol in this class" and
     * "pay for it" are one action, not two separate steps a client should
     * have to orchestrate itself.
     */
    @PostMapping("/api/bootcamp-classes/{classId}/enrolments")
    @ResponseStatus(HttpStatus.CREATED)
    public Payment enrolInBootcamp(@PathVariable String classId,
                                   @RequestBody EnrolmentRequest request) {
        Payment payment = membershipService.enrolInBootcamp(request.memberId(), classId);
        membershipService.processPayment(payment);
        return payment;
    }

    @DeleteMapping("/api/bootcamp-classes/{classId}/enrolments/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFromBootcamp(@PathVariable String classId, @PathVariable String memberId) {
        membershipService.removeFromBootcamp(memberId, classId);
    }

    // ══════════════════════════════════════════════════════
    //  Instructor assignment
    // ══════════════════════════════════════════════════════

    /**
     * POST /api/bootcamp-classes/{classId}/instructor
     *
     * trainerService.assignInstructorToClass() only mutates the BootcampClass
     * object in memory — it does NOT save it. This is the exact bug found
     * earlier in GymConsoleApp: an instructor assignment that ran without
     * error but never actually reached the database, because the follow-up
     * save call was missing. The membershipService.addBootcampClass(bc)
     * call below is that save call — deliberately not skipped this time.
     */
    @PostMapping("/api/bootcamp-classes/{classId}/instructor")
    public BootcampClass assignInstructor(@PathVariable String classId,
                                          @RequestBody AssignInstructorRequest request) {
        BootcampClass bc = membershipService.getBootcampById(classId);
        trainerService.assignInstructorToClass(request.instructorId(), bc);
        membershipService.addBootcampClass(bc);   // persist — see note above
        return bc;
    }

    @DeleteMapping("/api/bootcamp-classes/{classId}/instructor/{instructorId}")
    public BootcampClass removeInstructor(@PathVariable String classId,
                                          @PathVariable String instructorId) {
        BootcampClass bc = membershipService.getBootcampById(classId);
        trainerService.removeInstructorFromClass(instructorId, bc);
        membershipService.addBootcampClass(bc);   // persist — same reason as above
        return bc;
    }

    // ══════════════════════════════════════════════════════
    //  Membership status
    // ══════════════════════════════════════════════════════

    @GetMapping("/api/members/{memberId}/membership")
    public Membership getMembership(@PathVariable String memberId) {
        return membershipService.getMembership(memberId);
    }

    /**
     * These three return the updated Membership after the action, rather
     * than 204 No Content — a client asking "freeze this" almost certainly
     * wants to see the resulting frozen status immediately, not fire a
     * separate follow-up GET to find out if it worked.
     */
    @PostMapping("/api/members/{memberId}/membership/freeze")
    public Membership freezeMembership(@PathVariable String memberId) {
        membershipService.freezeMembership(memberId);
        return membershipService.getMembership(memberId);
    }

    @PostMapping("/api/members/{memberId}/membership/unfreeze")
    public Membership unfreezeMembership(@PathVariable String memberId) {
        membershipService.unfreezeMembership(memberId);
        return membershipService.getMembership(memberId);
    }

    @PostMapping("/api/members/{memberId}/membership/sessions")
    public Membership recordPayAsYouGoSession(@PathVariable String memberId) {
        membershipService.recordPayAsYouGoSession(memberId);
        return membershipService.getMembership(memberId);
    }

    // ── request DTOs ──────────────────────────────────────

    public record CreateBootcampClassRequest(
        String classId, BootcampType type, String schedule, int maxCapacity
    ) {}

    public record EnrolmentRequest(String memberId) {}

    public record AssignInstructorRequest(String instructorId) {}
}