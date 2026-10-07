package com.gymmanagement.service;

import com.gymmanagement.exception.DuplicateMemberException;
import com.gymmanagement.exception.MemberNotFoundException;
import com.gymmanagement.model.Member;
import com.gymmanagement.model.Payment;
import com.gymmanagement.model.membership.Membership;
import com.gymmanagement.model.membership.PayAsYouGoMembership;
import com.gymmanagement.model.membership.StandardMembership;
import com.gymmanagement.repository.FakeMemberRepository;
import com.gymmanagement.repository.MemberRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests MemberService in isolation, using FakeMemberRepository instead of
 * SqliteMemberRepository. MemberService has no idea it's talking to a fake —
 * it only knows the MemberRepository interface. That's the whole point.
 */
class MemberServiceTest {

    private MemberService memberService;

    /**
     * Runs before EVERY @Test method below, giving each test a completely
     * fresh, empty fake repository. Without this, one test's leftover data
     * could leak into the next test and cause confusing, order-dependent
     * failures — tests should never depend on each other.
     */
    @BeforeEach
    void setUp() {
        MemberRepository fakeRepo = new FakeMemberRepository();
        memberService = new MemberService(fakeRepo);
    }

    @Test
    void registeringNewMemberSucceeds() {
        Member member = memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");

        assertEquals("M001", member.getMemberId());
        assertEquals("Alice Smith", member.getName());
    }

    @Test
    void registeringDuplicateMemberIdThrows() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");

        // assertThrows takes the exception TYPE and a block of code (a
        // lambda) — it runs that code and checks the exception was thrown.
        // If registerMember DIDN'T throw, this test would fail, not pass.
        assertThrows(DuplicateMemberException.class, () ->
            memberService.registerMember("P002", "M001", "Someone Else", "x@x.com", "0000")
        );
    }

    @Test
    void gettingUnknownMemberThrowsNotFound() {
        assertThrows(MemberNotFoundException.class, () ->
            memberService.getMemberById("DOES_NOT_EXIST")
        );
    }

    @Test
    void removingMembershipFromMemberWithNoneThrows() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");

        assertThrows(MemberNotFoundException.class, () ->
            memberService.removeMembership("M001")
        );
    }

    // ── editing core details ──────────────────────────────

    @Test
    void updatingMemberDetailsPersistsAllThreeFields() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");

        Member updated = memberService.updateMemberDetails(
                "M001", "Alice Jones", "alice.jones@email.com", "07800199999");

        assertEquals("Alice Jones", updated.getName());
        assertEquals("alice.jones@email.com", updated.getEmail());
        assertEquals("07800199999", updated.getPhone());

        // Confirm it actually persisted, not just returned an in-memory copy —
        // fetch the member fresh via a separate call.
        Member reloaded = memberService.getMemberById("M001");
        assertEquals("Alice Jones", reloaded.getName());
    }

    @Test
    void updatingUnknownMemberThrowsNotFound() {
        assertThrows(MemberNotFoundException.class, () ->
            memberService.updateMemberDetails("DOES_NOT_EXIST", "X", "x@x.com", "0")
        );
    }

    // ── activation status ──────────────────────────────────

    @Test
    void newMemberIsActiveByDefault() {
        Member member = memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");

        assertEquals(true, member.isActive());
    }

    @Test
    void deactivatingMemberPersistsRealInactiveState() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");

        Member deactivated = memberService.deactivateMember("M001");
        assertEquals(false, deactivated.isActive());

        // Real persistence check, not just the returned object's state —
        // a fresh fetch must also reflect it.
        Member reloaded = memberService.getMemberById("M001");
        assertEquals(false, reloaded.isActive());
    }

    @Test
    void reactivatingMemberRestoresActiveState() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        memberService.deactivateMember("M001");

        Member reactivated = memberService.reactivateMember("M001");
        assertEquals(true, reactivated.isActive());
    }

    @Test
    void deactivatingAlreadyInactiveMemberIsIdempotentNotAnError() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        memberService.deactivateMember("M001");

        // Should not throw — deactivating twice is a harmless no-op,
        // matching Membership.freeze()'s established behavior.
        Member stillInactive = memberService.deactivateMember("M001");
        assertEquals(false, stillInactive.isActive());
    }

    // ── membership billing ──────────────────────────────────

    @Test
    void assigningStandardMembershipCreatesRealCompletedInitialPayment() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        Membership membership = new StandardMembership("MEM001", 12);

        memberService.assignMembership("M001", membership);

        Member member = memberService.getMemberById("M001");
        assertEquals(1, member.getPaymentHistory().size());
        assertEquals(Payment.STATUS_COMPLETED, member.getPaymentHistory().get(0).getStatus());
    }

    @Test
    void assigningStandardMembershipSetsRealDueDateOneMonthAfterStart() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        Membership membership = new StandardMembership("MEM001", 12);

        memberService.assignMembership("M001", membership);

        Member member = memberService.getMemberById("M001");
        assertEquals(
            member.getMembership().getStartDate().plusMonths(1),
            member.getMembership().getNextPaymentDueDate()
        );
    }

    @Test
    void assigningPayAsYouGoMembershipCreatesNoPaymentAndNoDueDate() {
        // The real, deliberate distinction: PayAsYouGo has no recurring
        // billing cycle, so signup shouldn't fabricate one.
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        Membership membership = new PayAsYouGoMembership("MEM001");

        memberService.assignMembership("M001", membership);

        Member member = memberService.getMemberById("M001");
        assertTrue(member.getPaymentHistory().isEmpty());
        assertNull(member.getMembership().getNextPaymentDueDate());
    }

    @Test
    void recordingMembershipPaymentCreatesNewPaymentAndAdvancesDueDate() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        memberService.assignMembership("M001", new StandardMembership("MEM001", 12));
        Member beforeRecord = memberService.getMemberById("M001");
        var dueDateBefore = beforeRecord.getMembership().getNextPaymentDueDate();

        memberService.recordMembershipPayment("M001");

        Member afterRecord = memberService.getMemberById("M001");
        assertEquals(2, afterRecord.getPaymentHistory().size(),
            "Should have the original signup payment plus this new one");
        assertEquals(dueDateBefore.plusMonths(1), afterRecord.getMembership().getNextPaymentDueDate());
    }

    @Test
    void recordingPaymentForPayAsYouGoMembershipThrows() {
        // Real validation, not a silent no-op — PayAsYouGo has nothing
        // to advance, so "recording a membership payment" against one
        // isn't a meaningful action.
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        memberService.assignMembership("M001", new PayAsYouGoMembership("MEM001"));

        assertThrows(IllegalArgumentException.class, () ->
            memberService.recordMembershipPayment("M001")
        );
    }

    @Test
    void recordingPaymentForMemberWithNoMembershipThrows() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");

        assertThrows(MemberNotFoundException.class, () ->
            memberService.recordMembershipPayment("M001")
        );
    }

    @Test
    void recordingPaymentForUnknownMemberThrows() {
        assertThrows(MemberNotFoundException.class, () ->
            memberService.recordMembershipPayment("DOES_NOT_EXIST")
        );
    }

    // ── member-facing simulated payment (payMembershipOnline) ─────────
    // Due dates are set explicitly (far enough past / future that the
    // status can't flip on a threshold edge), so these tests never
    // depend on the exact DUE_SOON window.

    /** Registers M001 with a Standard membership whose next payment is due on {@code due}. */
    private Member memberWithDueDate(LocalDate due) {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        memberService.assignMembership("M001", new StandardMembership("MEM001", 12));
        Member member = memberService.getMemberById("M001");
        member.getMembership().setNextPaymentDueDate(due);
        return member;
    }

    @Test
    void payingOnlineWhenOverdueRecordsACompletedPaymentAndAdvancesTheDueDate() {
        LocalDate due = LocalDate.now().minusDays(10);
        Member member = memberWithDueDate(due);
        int paymentsBefore = member.getPaymentHistory().size();
        double fee = member.getMembership().getMonthlyFee();

        memberService.payMembershipOnline("M001");

        Member after = memberService.getMemberById("M001");
        assertEquals(paymentsBefore + 1, after.getPaymentHistory().size());
        Payment newest = after.getPaymentHistory().get(after.getPaymentHistory().size() - 1);
        assertEquals(Payment.STATUS_COMPLETED, newest.getStatus());
        assertEquals(fee, newest.getAmount());
        assertEquals(due.plusMonths(1), after.getMembership().getNextPaymentDueDate());
    }

    @Test
    void payingOnlineLabelsThePaymentAsAnOnlinePayment() {
        Member member = memberWithDueDate(LocalDate.now().minusDays(10));

        memberService.payMembershipOnline("M001");

        Payment newest = member.getPaymentHistory().get(member.getPaymentHistory().size() - 1);
        assertTrue(newest.getDescription().contains("Online"),
            "Description should say it came through the online flow: " + newest.getDescription());
        assertFalse(newest.getDescription().startsWith("Membership renewal"),
            "Should read differently from the admin path's description: " + newest.getDescription());
        assertTrue(newest.getPaymentId().startsWith("PAY-MEM-M001-"));
    }

    @Test
    void payingOnlineIsAllowedWhenThePaymentIsDueSoon() {
        Member member = memberWithDueDate(LocalDate.now().plusDays(1));
        assertEquals("DUE_SOON", member.getMembership().getPaymentStatus());

        memberService.payMembershipOnline("M001");

        assertEquals(LocalDate.now().plusDays(1).plusMonths(1),
            memberService.getMemberById("M001").getMembership().getNextPaymentDueDate());
    }

    @Test
    void payingOnlineWhenAlreadyPaidUpIsRejectedAndChangesNothing() {
        LocalDate due = LocalDate.now().plusMonths(2);
        Member member = memberWithDueDate(due);
        int paymentsBefore = member.getPaymentHistory().size();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
            memberService.payMembershipOnline("M001"));

        assertTrue(thrown.getMessage().contains(due.toString()),
            "The message should tell the member when the next payment is due: " + thrown.getMessage());
        Member after = memberService.getMemberById("M001");
        assertEquals(paymentsBefore, after.getPaymentHistory().size());
        assertEquals(due, after.getMembership().getNextPaymentDueDate());
    }

    @Test
    void oneOnlinePaymentSettlesOnlyOneBillingCycle() {
        // 45 days behind: one payment moves the due date forward one
        // month (to ~14-17 days ago) — still overdue — so a second is needed.
        memberWithDueDate(LocalDate.now().minusDays(45));

        memberService.payMembershipOnline("M001");
        assertEquals("OVERDUE",
            memberService.getMemberById("M001").getMembership().getPaymentStatus());

        memberService.payMembershipOnline("M001");
        assertFalse("OVERDUE".equals(
            memberService.getMemberById("M001").getMembership().getPaymentStatus()));
    }

    @Test
    void payingOnlineTwiceInARowOnlyRecordsOnePaymentOnceTheMemberIsPaidUp() {
        // Models a double-clicked Pay button: the second call re-reads
        // the already-advanced due date and is refused.
        Member member = memberWithDueDate(LocalDate.now().minusDays(5));
        int paymentsBefore = member.getPaymentHistory().size();

        memberService.payMembershipOnline("M001");
        assertThrows(IllegalStateException.class, () -> memberService.payMembershipOnline("M001"));

        assertEquals(paymentsBefore + 1,
            memberService.getMemberById("M001").getPaymentHistory().size());
    }

    @Test
    void payingOnlineForPayAsYouGoMembershipThrows() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        memberService.assignMembership("M001", new PayAsYouGoMembership("MEM001"));

        assertThrows(IllegalArgumentException.class, () -> memberService.payMembershipOnline("M001"));
    }

    @Test
    void payingOnlineForMemberWithNoMembershipThrows() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");

        assertThrows(MemberNotFoundException.class, () -> memberService.payMembershipOnline("M001"));
    }

    @Test
    void payingOnlineForUnknownMemberThrows() {
        assertThrows(MemberNotFoundException.class, () ->
            memberService.payMembershipOnline("DOES_NOT_EXIST"));
    }

    @Test
    void payingOnlineForADeactivatedMemberIsRejected() {
        Member member = memberWithDueDate(LocalDate.now().minusDays(10));
        int paymentsBefore = member.getPaymentHistory().size();
        memberService.deactivateMember("M001");

        assertThrows(IllegalStateException.class, () -> memberService.payMembershipOnline("M001"));

        assertEquals(paymentsBefore, memberService.getMemberById("M001").getPaymentHistory().size());
    }

    @Test
    void theAdminPaymentPathStillAcceptsAPaidUpMemberAndKeepsItsOwnDescription() {
        // Admin can record e.g. a cash payment taken early — the
        // "only when due" rule is deliberately member-path only.
        Member member = memberWithDueDate(LocalDate.now().plusMonths(2));
        int paymentsBefore = member.getPaymentHistory().size();

        memberService.recordMembershipPayment("M001");

        assertEquals(paymentsBefore + 1,
            memberService.getMemberById("M001").getPaymentHistory().size());
        Payment newest = member.getPaymentHistory().get(member.getPaymentHistory().size() - 1);
        assertEquals("Membership renewal — StandardMembership", newest.getDescription());
    }

    // ── due-date backfill ────────────────────────────────────

    @Test
    void backfillSetsDueDateForRecurringMembershipThatHasNone() {
        Member member = memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        // Simulating a pre-existing membership from before the billing
        // feature existed — constructed directly, bypassing
        // assignMembership(), so nextPaymentDueDate is genuinely null.
        StandardMembership oldMembership = new StandardMembership("MEM001", 12);
        member.setMembership(oldMembership);

        int updated = memberService.backfillMissingDueDates();

        assertEquals(1, updated);
        Member reloaded = memberService.getMemberById("M001");
        assertEquals(
            oldMembership.getStartDate().plusMonths(1),
            reloaded.getMembership().getNextPaymentDueDate()
        );
    }

    @Test
    void backfillDoesNotTouchPayAsYouGoMembership() {
        Member member = memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        member.setMembership(new PayAsYouGoMembership("MEM001"));

        int updated = memberService.backfillMissingDueDates();

        assertEquals(0, updated);
        assertNull(memberService.getMemberById("M001").getMembership().getNextPaymentDueDate());
    }

    @Test
    void backfillDoesNotOverwriteAnAlreadySetDueDate() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        // Goes through the real assignMembership() path, which already
        // sets a real due date — the backfill must leave this alone.
        memberService.assignMembership("M001", new StandardMembership("MEM001", 12));
        var originalDueDate = memberService.getMemberById("M001").getMembership().getNextPaymentDueDate();

        int updated = memberService.backfillMissingDueDates();

        assertEquals(0, updated);
        assertEquals(originalDueDate, memberService.getMemberById("M001").getMembership().getNextPaymentDueDate());
    }

    @Test
    void backfillCreatesNoPaymentRecords() {
        // Explicit check for the requirement that this never invents
        // payment history — only assignMembership() creates a real
        // signup payment, and this member never went through that path.
        Member member = memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        member.setMembership(new StandardMembership("MEM001", 12));

        memberService.backfillMissingDueDates();

        assertTrue(memberService.getMemberById("M001").getPaymentHistory().isEmpty());
    }

    @Test
    void backfillIsIdempotentAcrossMultipleMembers() {
        Member alice = memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        alice.setMembership(new StandardMembership("MEM001", 12));

        Member bob = memberService.registerMember(
                "P002", "M002", "Bob Jones", "bob@email.com", "07800100002");
        bob.setMembership(new PayAsYouGoMembership("MEM002"));

        int firstRun = memberService.backfillMissingDueDates();
        int secondRun = memberService.backfillMissingDueDates();

        assertEquals(1, firstRun);
        assertEquals(0, secondRun, "Running twice must not re-update the same membership");
    }

    // ── clearDueDateForCorrection ────────────────────────────

    @Test
    void clearingDueDateSetsItBackToNull() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        memberService.assignMembership("M001", new StandardMembership("MEM001", 12));

        memberService.clearDueDateForCorrection("M001");

        assertNull(memberService.getMemberById("M001").getMembership().getNextPaymentDueDate());
    }

    @Test
    void clearingDueDateCreatesNoPaymentRecords() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        memberService.assignMembership("M001", new StandardMembership("MEM001", 12));
        int paymentsBefore = memberService.getMemberById("M001").getPaymentHistory().size();

        memberService.clearDueDateForCorrection("M001");

        assertEquals(paymentsBefore, memberService.getMemberById("M001").getPaymentHistory().size());
    }

    @Test
    void clearingDueDateForMemberWithNoMembershipThrows() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");

        assertThrows(MemberNotFoundException.class, () ->
            memberService.clearDueDateForCorrection("M001")
        );
    }

    // ── setMembershipStartDateForCorrection ──────────────────

    @Test
    void settingStartDateForCorrectionPersistsTheNewDate() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        memberService.assignMembership("M001", new StandardMembership("MEM001", 12));
        LocalDate backdated = LocalDate.now().minusWeeks(5);

        memberService.setMembershipStartDateForCorrection("M001", backdated);

        assertEquals(backdated, memberService.getMemberById("M001").getMembership().getStartDate());
    }

    @Test
    void settingStartDateThenBackfillingProducesRealOverdueStatus() {
        // The actual composed flow this feature exists for: backdate,
        // clear the now-stale due date, recompute from the real new
        // start date — proving the resulting OVERDUE status is
        // genuinely derived, not set directly anywhere.
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        memberService.assignMembership("M001", new StandardMembership("MEM001", 12));

        memberService.setMembershipStartDateForCorrection("M001", LocalDate.now().minusMonths(2));
        memberService.clearDueDateForCorrection("M001");
        memberService.backfillMissingDueDates();

        assertEquals("OVERDUE", memberService.getMemberById("M001").getMembership().getPaymentStatus());
    }

    @Test
    void settingStartDateForMemberWithNoMembershipThrows() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");

        assertThrows(MemberNotFoundException.class, () ->
            memberService.setMembershipStartDateForCorrection("M001", LocalDate.now())
        );
    }
}
