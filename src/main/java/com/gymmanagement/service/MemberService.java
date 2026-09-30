package com.gymmanagement.service;

import com.gymmanagement.exception.DuplicateMemberException;
import com.gymmanagement.exception.MemberNotFoundException;
import com.gymmanagement.model.Member;
import com.gymmanagement.model.Payment;
import com.gymmanagement.model.membership.Membership;
import com.gymmanagement.repository.MemberRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * MemberService owns every business rule that involves a Member.
 *
 * Rules enforced here:
 *  - A member ID must be unique before registration.
 *  - A member must exist before a membership can be assigned or removed.
 *  - Removing a membership sets it to null — the record stays.
 *
 * This class has NO System.out calls and NO ArrayList declarations.
 * It receives a MemberRepository via constructor injection so the
 * storage implementation can be swapped without touching this class.
 *
 * @Service marks this as a Spring-managed bean. It's the ONLY change
 * Spring needed here — the constructor-injection pattern this class
 * already used is exactly what Spring auto-wires. The 4 existing JUnit
 * tests still run with zero Spring involvement (`new MemberService(fakeRepo)`
 * still works exactly as before) — this annotation is inert outside of a
 * running Spring container.
 */
@Service
public class MemberService {

    private final MemberRepository memberRepo;

    public MemberService(MemberRepository memberRepo) {
        this.memberRepo = memberRepo;
    }

    // ── registration ──────────────────────────────────────

    /**
     * Register a new member.
     * Throws DuplicateMemberException if the ID is already taken.
     */
    public Member registerMember(String personId, String memberId,
                                 String name, String email, String phone) {
        if (memberRepo.existsById(memberId))
            throw new DuplicateMemberException(
                "Member ID already registered: " + memberId);

        Member member = new Member(personId, memberId, name, email, phone);
        memberRepo.save(member);
        return member;
    }

    // ── retrieval ─────────────────────────────────────────

    public Member getMemberById(String memberId) {
        return memberRepo.findById(memberId)
                         .orElseThrow(() -> new MemberNotFoundException(
                             "No member found with ID: " + memberId));
    }

    public List<Member> getAllMembers() {
        return memberRepo.findAll();
    }

    // ── fitness goal ───────────────────────────────────────

    /** Sets or updates a member's stated fitness goal. */
    public Member updateFitnessGoal(String memberId, String fitnessGoal) {
        Member member = getMemberById(memberId);
        member.setFitnessGoal(fitnessGoal);
        memberRepo.save(member);
        return member;
    }

    // ── editing core details ──────────────────────────────

    /**
     * Updates a member's name/email/phone. Deliberately a separate
     * method from registerMember() rather than reusing it — that method
     * has an existsById() guard specifically to prevent "creating" a
     * member that already exists, which is correct for a create
     * endpoint but wrong for an edit one. This method requires the
     * member to already exist (the normal case for editing) and calls
     * the same real, already-validated setName()/setEmail()/setPhone()
     * methods inherited from Person — setName() already rejects a
     * blank name, unchanged here.
     */
    public Member updateMemberDetails(String memberId, String name, String email, String phone) {
        Member member = getMemberById(memberId);
        member.setName(name);
        member.setEmail(email);
        member.setPhone(phone);
        memberRepo.save(member);
        return member;
    }

    // ── activation status ──────────────────────────────────

    /**
     * Soft-deactivation — the member row is never deleted, so historical
     * membership/enrolment data referencing this memberId stays intact.
     * Idempotent, matching freeze()/unfreeze()'s established behavior:
     * deactivating an already-inactive member is a harmless no-op, not
     * an error.
     */
    public Member deactivateMember(String memberId) {
        Member member = getMemberById(memberId);
        member.deactivate();
        memberRepo.save(member);
        return member;
    }

    public Member reactivateMember(String memberId) {
        Member member = getMemberById(memberId);
        member.reactivate();
        memberRepo.save(member);
        return member;
    }

    // ── membership billing ────────────────────────────────

    /**
     * The real admin action for collecting a recurring membership
     * payment — creates a genuine, completed Payment (same class,
     * same real persistence as bootcamp fees) and advances the
     * membership's due date by one billing cycle. Rejects
     * PayAsYouGoMembership outright: it has no recurring due date to
     * advance, so "recording a membership payment" against one isn't a
     * real, meaningful action, not merely an unsupported one.
     */
    public Member recordMembershipPayment(String memberId) {
        Member member = getMemberById(memberId);
        Membership membership = member.getMembership();
        if (membership == null)
            throw new MemberNotFoundException(
                "Member " + memberId + " has no membership to record a payment against.");
        if (membership.getNextPaymentDueDate() == null)
            throw new IllegalArgumentException(
                "This membership type has no recurring due date — nothing to record.");

        String paymentId = "PAY-MEM-" + memberId + "-" + UUID.randomUUID().toString().substring(0, 8);
        Payment payment = new Payment(
            paymentId, membership.getMonthlyFee(),
            "Membership renewal — " + membership.getClass().getSimpleName(), member);
        payment.markCompleted();
        member.addPayment(payment);
        membership.advancePaymentDueDate();

        memberRepo.save(member);
        return member;
    }

    // ── membership assignment ─────────────────────────────

    /**
     * Assign a membership to an existing member.
     * Overwrites any previously held membership.
     */
    /**
     * Assigns a membership, and — for the two genuinely recurring types
     * (Standard, Student Saver) — sets up real billing: a due date one
     * cycle after signup, and an initial COMPLETED payment representing
     * "paid at signup", using the exact same real Payment class already
     * proven for bootcamp fees. PayAsYouGoMembership deliberately gets
     * neither — it has no subscription cycle to bill against.
     */
    public void assignMembership(String memberId, Membership membership) {
        Member member = getMemberById(memberId);
        member.setMembership(membership);

        if (isRecurringMembership(membership)) {
            membership.setNextPaymentDueDate(membership.getStartDate().plusMonths(1));
            String paymentId = "PAY-MEM-" + memberId + "-" + UUID.randomUUID().toString().substring(0, 8);
            Payment initialPayment = new Payment(
                paymentId, membership.getMonthlyFee(),
                "Membership signup — " + membership.getClass().getSimpleName(), member);
            initialPayment.markCompleted();
            member.addPayment(initialPayment);
        }

        memberRepo.save(member);
    }

    /**
     * Shared by assignMembership() and backfillMissingDueDates() — one
     * definition of "recurring", not two copies of the same check.
     */
    private boolean isRecurringMembership(Membership membership) {
        String type = membership.getClass().getSimpleName();
        return type.equals("StandardMembership") || type.equals("StudentSaverMembership");
    }

    /**
     * Clears a single membership's due date, so the existing
     * backfillMissingDueDates() can correctly recompute it once
     * getStartDate() reflects the real historical date. Deliberately
     * scoped to one member at a time, not a blanket reset — this is
     * for correcting the specific handful of records affected by the
     * startDate-restoration bug, not a general-purpose reset tool.
     * Creates no Payment records, touches no other field.
     */
    public Member clearDueDateForCorrection(String memberId) {
        Member member = getMemberById(memberId);
        Membership membership = member.getMembership();
        if (membership == null)
            throw new MemberNotFoundException(
                "Member " + memberId + " has no membership to correct.");

        membership.setNextPaymentDueDate(null);
        memberRepo.save(member);
        return member;
    }

    /**
     * Sets a membership's start date directly — an admin correction/
     * demo-setup action, not something the normal assignment flow
     * exposes (assignMembership() always uses "now", by design, and
     * stays completely unchanged here). Real use: giving a demo
     * membership a genuine, varied backstory (e.g. "joined 5 weeks
     * ago") so the real billing logic — the same getPaymentStatus()
     * used everywhere else — naturally computes OVERDUE/DUE_SOON for
     * it, rather than hardcoding a status anywhere.
     *
     * Does not itself touch nextPaymentDueDate — pair this with
     * clearDueDateForCorrection() + backfillMissingDueDates() to
     * recompute the due date from the new start date, exactly the same
     * composition already used for the earlier data-correction work.
     */
    public Member setMembershipStartDateForCorrection(String memberId, LocalDate startDate) {
        Member member = getMemberById(memberId);
        Membership membership = member.getMembership();
        if (membership == null)
            throw new MemberNotFoundException(
                "Member " + memberId + " has no membership to correct.");

        membership.setStartDate(startDate);
        memberRepo.save(member);
        return member;
    }

    /**
     * One-time migration for memberships assigned before this billing
     * feature existed — real Standard/StudentSaver memberships that
     * never got a due date set, because assignMembership() didn't have
     * this logic yet when they were created. Uses the exact same
     * startDate.plusMonths(1) formula assignMembership() already uses
     * for new memberships — the honest "first due date", not an
     * arbitrary or demo-tuned one. If that date has already passed,
     * the member correctly shows OVERDUE: no membership payment was
     * ever actually recorded for them, which is true, not staged.
     *
     * Explicitly does NOT touch PayAsYouGoMembership (no recurring
     * cycle to backfill) or any membership that already has a real
     * due date set — idempotent by construction, safe to run more
     * than once. Creates no Payment records — this only initializes a
     * real going-forward billing cycle, it doesn't invent history.
     *
     * Returns the count of memberships actually updated, so the
     * caller/endpoint can report something real rather than a bare
     * "done".
     */
    public int backfillMissingDueDates() {
        int updated = 0;
        for (Member member : memberRepo.findAll()) {
            Membership membership = member.getMembership();
            if (membership == null) continue;
            if (!isRecurringMembership(membership)) continue;
            if (membership.getNextPaymentDueDate() != null) continue;

            membership.setNextPaymentDueDate(membership.getStartDate().plusMonths(1));
            memberRepo.save(member);
            updated++;
        }
        return updated;
    }

    /**
     * Remove (cancel) a member's current membership.
     * The member record is retained.
     */
    public void removeMembership(String memberId) {
        Member member = getMemberById(memberId);
        if (member.getMembership() == null)
            throw new MemberNotFoundException(
                "Member " + memberId + " has no active membership to remove.");
        member.removeMembership();
        memberRepo.save(member);
    }

    // ── removal ───────────────────────────────────────────

    /**
     * Permanently remove a member from the system.
     */
    public void deleteMember(String memberId) {
        if (!memberRepo.existsById(memberId))
            throw new MemberNotFoundException(
                "Cannot delete — no member found with ID: " + memberId);
        memberRepo.delete(memberId);
    }
}