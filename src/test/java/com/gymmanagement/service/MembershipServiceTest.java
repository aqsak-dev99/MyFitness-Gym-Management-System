package com.gymmanagement.service;

import com.gymmanagement.exception.ClassFullException;
import com.gymmanagement.exception.MemberNotFoundException;
import com.gymmanagement.exception.PaymentFailedException;
import com.gymmanagement.model.BootcampClass;
import com.gymmanagement.model.Member;
import com.gymmanagement.model.Payment;
import com.gymmanagement.model.membership.BootcampType;
import com.gymmanagement.model.membership.StandardMembership;
import com.gymmanagement.repository.BootcampRepository;
import com.gymmanagement.repository.FakeBootcampRepository;
import com.gymmanagement.repository.FakeMemberRepository;
import com.gymmanagement.repository.MemberRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests MembershipService in isolation, using fake repositories instead of
 * SQLite. Two tests here are deliberately written to double as regression
 * tests for real bugs found earlier in this project:
 *
 *  - enrollingInSecondBootcampClassAppliesDiscount guards against the
 *    Member.equals()/hashCode() bug that once made the discount silently
 *    never apply (every DB fetch created a new object, so reference
 *    equality always failed).
 *
 *  - enrollingTwiceInSameClassThrows guards the same fix from the other
 *    direction: the duplicate-enrolment check also depends on equals().
 *
 * If either of those bugs ever came back, these tests would fail — that's
 * the whole point of writing them, not just testing the happy path.
 */
class MembershipServiceTest {

    private MembershipService membershipService;
    private MemberRepository  memberRepo;
    private BootcampRepository bootcampRepo;

    @BeforeEach
    void setUp() {
        memberRepo   = new FakeMemberRepository();
        bootcampRepo = new FakeBootcampRepository();
        membershipService = new MembershipService(memberRepo, bootcampRepo);
    }

    // ── helpers ───────────────────────────────────────────

    private Member registerMember(String memberId, String name) {
        Member member = new Member("P" + memberId, memberId, name,
                                   name.toLowerCase() + "@email.com", "0700000000");
        memberRepo.save(member);
        return member;
    }

    private BootcampClass createBootcampClass(String classId, BootcampType type, int maxCapacity) {
        BootcampClass bc = new BootcampClass(classId, type, "Mon 07:00", maxCapacity);
        bootcampRepo.save(bc);
        return bc;
    }

    // ── bootcamp enrolment + discount ─────────────────────

    @Test
    void enrollingInOneBootcampClassChargesFullFee() {
        registerMember("M001", "Alice");
        createBootcampClass("BC001", BootcampType.FAT_BURN, 10);

        Payment payment = membershipService.enrolInBootcamp("M001", "BC001");

        assertEquals(35.50, payment.getAmount(), 0.001);
    }

    @Test
    void enrollingInSecondBootcampClassAppliesDiscount() {
        registerMember("M001", "Alice");
        createBootcampClass("BC001", BootcampType.FAT_BURN, 10);
        createBootcampClass("BC002", BootcampType.FITNESS_AND_ENDURANCE, 10);

        membershipService.enrolInBootcamp("M001", "BC001");   // 1st class — no discount
        Payment second = membershipService.enrolInBootcamp("M001", "BC002"); // 2nd — discount

        assertEquals(33.015, second.getAmount(), 0.001,
            "Second bootcamp enrolment should apply the 7% multi-class discount");
    }

    @Test
    void enrollingInFullClassThrowsClassFullException() {
        registerMember("M001", "Alice");
        registerMember("M002", "Ben");
        createBootcampClass("BC001", BootcampType.FAT_BURN, 1);   // capacity 1

        membershipService.enrolInBootcamp("M001", "BC001");   // fills the only spot

        assertThrows(ClassFullException.class, () ->
            membershipService.enrolInBootcamp("M002", "BC001")
        );
    }

    @Test
    void enrollingTwiceInSameClassThrows() {
        registerMember("M001", "Alice");
        createBootcampClass("BC001", BootcampType.FAT_BURN, 10);

        membershipService.enrolInBootcamp("M001", "BC001");

        assertThrows(IllegalStateException.class, () ->
            membershipService.enrolInBootcamp("M001", "BC001")
        );
    }

    @Test
    void removingUnenrolledMemberThrowsNotFound() {
        registerMember("M001", "Alice");
        createBootcampClass("BC001", BootcampType.FAT_BURN, 10);
        // Alice was never enrolled in BC001

        assertThrows(MemberNotFoundException.class, () ->
            membershipService.removeFromBootcamp("M001", "BC001")
        );
    }

    // ── payment processing ────────────────────────────────

    @Test
    void processingNormalPaymentSucceeds() {
        Member alice = registerMember("M001", "Alice");
        Payment payment = new Payment("PAY001", 35.50, "Test payment", alice);

        membershipService.processPayment(payment);

        assertEquals(Payment.STATUS_COMPLETED, payment.getStatus());
        assertTrue(alice.getPaymentHistory().contains(payment));
    }

    @Test
    void processingOversizedPaymentThrowsPaymentFailedException() {
        Member alice = registerMember("M001", "Alice");
        Payment tooLarge = new Payment("PAY002", 15_000.00, "Way too much", alice);

        assertThrows(PaymentFailedException.class, () ->
            membershipService.processPayment(tooLarge)
        );
    }

    // ── membership status ─────────────────────────────────

    @Test
    void gettingMembershipForMemberWithoutOneThrows() {
        registerMember("M001", "Alice");
        // No membership assigned to Alice

        assertThrows(MemberNotFoundException.class, () ->
            membershipService.getMembership("M001")
        );
    }

    @Test
    void freezingMembershipMarksItInactive() {
        Member alice = registerMember("M001", "Alice");
        alice.setMembership(new StandardMembership("MEM001", 12));
        memberRepo.save(alice);

        membershipService.freezeMembership("M001");

        assertTrue(membershipService.getMembership("M001").isFrozen());
    }
}