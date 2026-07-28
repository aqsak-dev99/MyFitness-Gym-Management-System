package com.gymmanagement.service;

import com.gymmanagement.exception.ClassFullException;
import com.gymmanagement.exception.MemberNotFoundException;
import com.gymmanagement.exception.PaymentFailedException;
import com.gymmanagement.model.BootcampClass;
import com.gymmanagement.model.Member;
import com.gymmanagement.model.Payment;
import com.gymmanagement.model.membership.Membership;
import com.gymmanagement.model.membership.PayAsYouGoMembership;
import com.gymmanagement.repository.BootcampRepository;
import com.gymmanagement.repository.MemberRepository;

import java.util.List;

/**
 * MembershipService owns every business rule that involves Membership,
 * BootcampClass, and Payment objects.
 *
 * Rules enforced here:
 *  - A bootcamp class cannot exceed its maxCapacity.
 *  - A member cannot enrol in the same bootcamp class twice.
 *  - The 7% multi-class discount is applied when a member is enrolling
 *    in their second or subsequent bootcamp class.
 *  - Payments above £10,000 are rejected.
 *  - Session recording for PayAsYouGoMembership is managed here,
 *    not inside the model object.
 *
 * No System.out calls. Repositories injected via constructor.
 */
public class MembershipService {

    private final MemberRepository   memberRepo;
    private final BootcampRepository bootcampRepo;

    public MembershipService(MemberRepository   memberRepo,
                             BootcampRepository bootcampRepo) {
        this.memberRepo   = memberRepo;
        this.bootcampRepo = bootcampRepo;
    }

    // ── membership status ─────────────────────────────────

    public Membership getMembership(String memberId) {
        Member member = getMemberOrThrow(memberId);
        if (member.getMembership() == null)
            throw new MemberNotFoundException(
                "Member " + memberId + " has no active membership.");
        return member.getMembership();
    }

    /**
     * Freeze a Standard membership.
     * Other membership types silently ignore the call (they don't support freezing).
     */
    public void freezeMembership(String memberId) {
        Membership m = getMembership(memberId);
        m.freeze();
        memberRepo.save(getMemberOrThrow(memberId));
    }

    public void unfreezeMembership(String memberId) {
        Membership m = getMembership(memberId);
        m.unfreeze();
        memberRepo.save(getMemberOrThrow(memberId));
    }

    // ── pay-as-you-go session ─────────────────────────────

    /**
     * Record a visit for a PayAsYouGoMembership member.
     * Throws IllegalStateException if the member is not on PAYG or sessions are exhausted.
     */
    public void recordPayAsYouGoSession(String memberId) {
        Membership m = getMembership(memberId);
        if (!(m instanceof PayAsYouGoMembership))
            throw new IllegalStateException(
                "Member " + memberId + " does not hold a Pay-As-You-Go membership.");
        PayAsYouGoMembership payg = (PayAsYouGoMembership) m;
        if (!payg.addSession())
            throw new IllegalStateException(
                "Maximum sessions reached for member " + memberId + ". Please renew.");
        memberRepo.save(getMemberOrThrow(memberId));
    }

    // ── bootcamp enrolment ────────────────────────────────

    /**
     * Enrol a member in a bootcamp class and return the payment that must be processed.
     *
     * The discount logic lives here, not in Main:
     *  - Count how many bootcamp classes the member is already enrolled in.
     *  - If this enrolment makes the total >= 2, apply the 7% discount.
     */
    public Payment enrolInBootcamp(String memberId, String classId) {
        Member       member  = getMemberOrThrow(memberId);
        BootcampClass bc     = getBootcampOrThrow(classId);

        if (bc.isFull())
            throw new ClassFullException(
                "Bootcamp class " + classId + " is full. Cannot enrol " + member.getName() + ".");

        if (bc.getParticipants().contains(member))
            throw new IllegalStateException(
                member.getName() + " is already enrolled in " + bc.getClassName() + ".");

        // How many bootcamp classes is this member already in (before this one)?
        long existingEnrolments = bootcampRepo.findAll().stream()
                                              .filter(c -> c.getParticipants().contains(member))
                                              .count();

        // Enrol the member — model handles list mutation
        bc.enrolMember(member);
        bootcampRepo.save(bc);

        // Calculate fee: existingEnrolments + 1 = total classes after this enrolment
        int totalAfterThis = (int) existingEnrolments + 1;
        double fee = bc.calcBootcampFee(totalAfterThis);

        // Build and return the payment object — caller decides whether to process it
        String paymentId = "PAY-BC-" + memberId + "-" + classId;
        String desc = bc.getClassName() + (totalAfterThis >= 2 ? " (7% multi-class discount)" : "");
        return new Payment(paymentId, fee, desc, member);
    }

    /**
     * Remove a member from a bootcamp class.
     */
    public void removeFromBootcamp(String memberId, String classId) {
        Member        member = getMemberOrThrow(memberId);
        BootcampClass bc     = getBootcampOrThrow(classId);

        if (!bc.removeMember(member))
            throw new MemberNotFoundException(
                member.getName() + " is not enrolled in " + bc.getClassName() + ".");

        bootcampRepo.save(bc);
    }

    // ── payment processing ────────────────────────────────

    /**
     * Process a payment and record it on the member's payment history.
     * Throws PaymentFailedException if the amount exceeds the ceiling.
     */
    public void processPayment(Payment payment) {
        if (payment.getAmount() > 10_000)
            throw new PaymentFailedException(
                "Payment " + payment.getPaymentId() + " rejected — amount exceeds limit.");
        payment.markCompleted();
        payment.getMember().addPayment(payment);
        memberRepo.save(payment.getMember());
    }

    // ── bootcamp class management ─────────────────────────

    public void addBootcampClass(BootcampClass bc) {
        bootcampRepo.save(bc);
    }

    public List<BootcampClass> getAllBootcampClasses() {
        return bootcampRepo.findAll();
    }

    public BootcampClass getBootcampById(String classId) {
        return getBootcampOrThrow(classId);
    }

    // ── private helpers ───────────────────────────────────

    private Member getMemberOrThrow(String memberId) {
        return memberRepo.findById(memberId)
                         .orElseThrow(() -> new MemberNotFoundException(
                             "No member found with ID: " + memberId));
    }

    private BootcampClass getBootcampOrThrow(String classId) {
        return bootcampRepo.findById(classId)
                           .orElseThrow(() -> new MemberNotFoundException(
                               "No bootcamp class found with ID: " + classId));
    }
}