package com.gymmanagement.controller;

import com.gymmanagement.config.RequireOwnership;
import com.gymmanagement.config.RequireRole;
import com.gymmanagement.model.BootcampClass;
import com.gymmanagement.model.Payment;
import com.gymmanagement.model.Role;
import com.gymmanagement.model.membership.BootcampType;
import com.gymmanagement.model.membership.Membership;
import com.gymmanagement.model.membership.MembershipType;
import com.gymmanagement.model.membership.PayAsYouGoMembership;
import com.gymmanagement.model.membership.StandardMembership;
import com.gymmanagement.model.membership.StudentSaverMembership;
import com.gymmanagement.service.MemberService;
import com.gymmanagement.service.MembershipService;
import com.gymmanagement.service.TrainerService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
 *
 * A third dependency (MemberService) was added for the same reason:
 * assigning a MEMBERSHIP calls the existing, unmodified
 * MemberService.assignMembership() — this controller just translates
 * the request into the right concrete Membership subtype first.
 */
@RestController
public class MembershipController {

    private final MembershipService membershipService;
    private final TrainerService    trainerService;
    private final MemberService     memberService;

    public MembershipController(MembershipService membershipService,
                                TrainerService     trainerService,
                                MemberService      memberService) {
        this.membershipService = membershipService;
        this.trainerService    = trainerService;
        this.memberService     = memberService;
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

    /**
     * POST /api/bootcamp-classes — also the real edit path, since
     * addBootcampClass() upserts on class_id (see SqliteBootcampRepository).
     * A genuinely new BootcampClass object always defaults cancelled=false —
     * without the check below, editing a cancelled class's schedule would
     * silently un-cancel it as an unintended side effect. Preserving the
     * existing status on update, so this endpoint edits type/schedule/
     * capacity without ever touching cancellation state as a side effect —
     * that's what the dedicated cancel/reactivate endpoints are for.
     */
    @PostMapping("/api/bootcamp-classes")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRole(Role.ADMIN)
    public BootcampClass createBootcampClass(@Valid @RequestBody CreateBootcampClassRequest request) {
        BootcampClass bc = new BootcampClass(
            request.classId(), request.type(), request.schedule(), request.maxCapacity()
        );
        boolean existingWasCancelled = false;
        try {
            existingWasCancelled = membershipService.getBootcampById(request.classId()).isCancelled();
        } catch (RuntimeException notFound) {
            // Genuinely new class — nothing to preserve.
        }
        if (existingWasCancelled) bc.cancel();
        membershipService.addBootcampClass(bc);
        return bc;
    }

    /**
     * PATCH /api/bootcamp-classes/{classId}/cancel and /reactivate
     * Soft-cancellation — the class row is never deleted, so real
     * enrolment history stays intact. Idempotent, same as
     * Member.deactivate()/Membership.freeze().
     */
    @PatchMapping("/api/bootcamp-classes/{classId}/cancel")
    @RequireRole(Role.ADMIN)
    public BootcampClass cancelBootcampClass(@PathVariable String classId) {
        BootcampClass bc = membershipService.getBootcampById(classId);
        bc.cancel();
        membershipService.addBootcampClass(bc);
        return bc;
    }

    @PatchMapping("/api/bootcamp-classes/{classId}/reactivate")
    @RequireRole(Role.ADMIN)
    public BootcampClass reactivateBootcampClass(@PathVariable String classId) {
        BootcampClass bc = membershipService.getBootcampById(classId);
        bc.reactivate();
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
    @RequireRole(Role.ADMIN)
    public Payment enrolInBootcamp(@PathVariable String classId,
                                   @Valid @RequestBody EnrolmentRequest request) {
        Payment payment = membershipService.enrolInBootcamp(request.memberId(), classId);
        membershipService.processPayment(payment);
        return payment;
    }

    @DeleteMapping("/api/bootcamp-classes/{classId}/enrolments/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireRole(Role.ADMIN)
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
    @RequireRole(Role.ADMIN)
    public BootcampClass assignInstructor(@PathVariable String classId,
                                          @Valid @RequestBody AssignInstructorRequest request) {
        BootcampClass bc = membershipService.getBootcampById(classId);
        trainerService.assignInstructorToClass(request.instructorId(), bc);
        membershipService.addBootcampClass(bc);   // persist — see note above
        return bc;
    }

    @DeleteMapping("/api/bootcamp-classes/{classId}/instructor/{instructorId}")
    @RequireRole(Role.ADMIN)
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
     * POST /api/members/{memberId}/membership
     *
     * The minimal endpoint that exposes MemberService.assignMembership()
     * — a real, already-existing, already-tested method that was never
     * reachable via REST before now (only ever called from the old
     * console demo). No new business logic: this method's entire job is
     * translating the request into the right concrete Membership
     * subtype, then handing off to that unmodified service method.
     *
     * Per-type required fields (durationMonths for STANDARD,
     * studentIdNumber for STUDENT_SAVER) are checked here rather than
     * via Bean Validation, since @NotNull can't conditionally apply
     * based on another field's value without a custom validator — more
     * machinery than this minimal endpoint calls for. Beyond that,
     * StudentSaverMembership's own constructor still runs its existing
     * blank-check validation unchanged, and IllegalArgumentException
     * already maps to 400 via the existing generic handler — no new
     * exception handling needed anywhere.
     */
    @PostMapping("/api/members/{memberId}/membership")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRole(Role.ADMIN)
    public Membership assignMembership(@PathVariable String memberId,
                                       @Valid @RequestBody AssignMembershipRequest request) {
        Membership membership = switch (request.type()) {
            case STANDARD -> {
                if (request.durationMonths() == null)
                    throw new IllegalArgumentException(
                        "durationMonths is required for a STANDARD membership.");
                yield new StandardMembership(request.membershipId(), request.durationMonths());
            }
            case STUDENT_SAVER -> {
                if (request.studentIdNumber() == null)
                    throw new IllegalArgumentException(
                        "studentIdNumber is required for a STUDENT_SAVER membership.");
                yield new StudentSaverMembership(request.membershipId(), request.studentIdNumber());
            }
            case PAY_AS_YOU_GO -> new PayAsYouGoMembership(request.membershipId());
        };

        memberService.assignMembership(memberId, membership);
        return membership;
    }

    /**
     * These three return the updated Membership after the action, rather
     * than 204 No Content — a client asking "freeze this" almost certainly
     * wants to see the resulting frozen status immediately, not fire a
     * separate follow-up GET to find out if it worked.
     */
    @PostMapping("/api/members/{memberId}/membership/freeze")
    @RequireRole(Role.ADMIN)
    public Membership freezeMembership(@PathVariable String memberId) {
        membershipService.freezeMembership(memberId);
        return membershipService.getMembership(memberId);
    }

    @PostMapping("/api/members/{memberId}/membership/unfreeze")
    @RequireRole(Role.ADMIN)
    public Membership unfreezeMembership(@PathVariable String memberId) {
        membershipService.unfreezeMembership(memberId);
        return membershipService.getMembership(memberId);
    }

    /**
     * POST /api/members/{memberId}/membership/payment
     * The real admin action for recording a membership payment —
     * creates a genuine completed Payment and advances the real due
     * date. Rejects PayAsYouGoMembership (no recurring due date to
     * advance) via MemberService's own real validation, not a silent
     * no-op.
     */
    @PostMapping("/api/members/{memberId}/membership/payment")
    @RequireRole(Role.ADMIN)
    public Membership recordMembershipPayment(@PathVariable String memberId) {
        memberService.recordMembershipPayment(memberId);
        return membershipService.getMembership(memberId);
    }

    /**
     * POST /api/members/{memberId}/membership/pay
     * The member-facing "Pay now" — a SIMULATED online payment (no card,
     * no provider). Records the same real Payment and advances the same
     * real due date as the admin endpoint above, but is only accepted
     * when a payment is actually due, and only for the member's OWN
     * membership: @RequireOwnership("memberId") lets a MEMBER token
     * through only when its linked member matches the path (ADMIN
     * bypasses ownership, as everywhere else).
     */
    @PostMapping("/api/members/{memberId}/membership/pay")
    @RequireOwnership("memberId")
    public Membership payMembershipOnline(@PathVariable String memberId) {
        memberService.payMembershipOnline(memberId);
        return membershipService.getMembership(memberId);
    }

    /**
     * DELETE /api/members/{memberId}/membership
     * Exposes MemberService.removeMembership() — a real, already-existing
     * method that had no REST endpoint before now (same situation as
     * assignMembership before it was wired up earlier this project).
     * Cancels the membership; the member record itself is untouched.
     */
    @DeleteMapping("/api/members/{memberId}/membership")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireRole(Role.ADMIN)
    public void removeMembership(@PathVariable String memberId) {
        memberService.removeMembership(memberId);
    }

    /**
     * POST /api/admin/backfill-membership-due-dates
     * One-time migration action, not ongoing business logic — sets a
     * real due date on any recurring membership that predates this
     * billing feature and never got one. See
     * MemberService.backfillMissingDueDates() for the full reasoning.
     * Safe to call more than once — already-set/non-recurring
     * memberships are skipped, not overwritten.
     */
    @PostMapping("/api/admin/backfill-membership-due-dates")
    @RequireRole(Role.ADMIN)
    public java.util.Map<String, Integer> backfillMembershipDueDates() {
        int updated = memberService.backfillMissingDueDates();
        return java.util.Map.of("membershipsUpdated", updated);
    }

    /**
     * POST /api/admin/members/{memberId}/membership/clear-due-date
     * One-time correction for the specific memberships affected by the
     * startDate-restoration bug — clears a single membership's due
     * date so the backfill above can correctly recompute it. Not a
     * general-purpose reset; scoped to one member per call.
     */
    @PostMapping("/api/admin/members/{memberId}/membership/clear-due-date")
    @RequireRole(Role.ADMIN)
    public Membership clearDueDateForCorrection(@PathVariable String memberId) {
        memberService.clearDueDateForCorrection(memberId);
        return membershipService.getMembership(memberId);
    }

    /**
     * PATCH /api/admin/members/{memberId}/membership/start-date
     * Admin correction/demo-setup action — see MemberService's own
     * javadoc for the full reasoning. Body: {"startDate": "2026-08-01"}.
     */
    @PatchMapping("/api/admin/members/{memberId}/membership/start-date")
    @RequireRole(Role.ADMIN)
    public Membership setMembershipStartDate(@PathVariable String memberId,
                                             @Valid @RequestBody SetStartDateRequest request) {
        memberService.setMembershipStartDateForCorrection(memberId, request.startDate());
        return membershipService.getMembership(memberId);
    }

    @PostMapping("/api/members/{memberId}/membership/sessions")
    @RequireRole(Role.ADMIN)
    public Membership recordPayAsYouGoSession(@PathVariable String memberId) {
        membershipService.recordPayAsYouGoSession(memberId);
        return membershipService.getMembership(memberId);
    }

    // ── request DTOs ──────────────────────────────────────

    public record CreateBootcampClassRequest(
        @NotBlank String classId,
        @NotNull  BootcampType type,
        @NotBlank String schedule,
        @Positive int maxCapacity
    ) {}

    public record EnrolmentRequest(@NotBlank String memberId) {}

    public record AssignInstructorRequest(@NotBlank String instructorId) {}

    public record AssignMembershipRequest(
        @NotBlank String membershipId,
        @NotNull  MembershipType type,
        Integer durationMonths,
        String studentIdNumber
    ) {}

    public record SetStartDateRequest(@NotNull LocalDate startDate) {}
}
