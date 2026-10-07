package com.gymmanagement.service;

import com.gymmanagement.model.BootcampClass;
import com.gymmanagement.model.Member;
import com.gymmanagement.model.Payment;
import com.gymmanagement.model.membership.BootcampType;
import com.gymmanagement.model.membership.Membership;
import com.gymmanagement.model.membership.PayAsYouGoMembership;
import com.gymmanagement.model.membership.StandardMembership;
import com.gymmanagement.repository.FakeBootcampRepository;
import com.gymmanagement.repository.FakeMemberRepository;
import com.gymmanagement.service.AdminOperationsService.ClassOccupancy;
import com.gymmanagement.service.AdminOperationsService.ClassOccupancyItem;
import com.gymmanagement.service.AdminOperationsService.MembershipStats;
import com.gymmanagement.service.AdminOperationsService.OverdueMember;
import com.gymmanagement.service.AdminOperationsService.OverdueMembers;
import com.gymmanagement.service.AdminOperationsService.PaymentItem;
import com.gymmanagement.service.AdminOperationsService.RecentPayments;
import com.gymmanagement.service.AdminOperationsService.RevenueSummary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests AdminOperationsService against fake repositories — real
 * MemberService and MembershipService on top of FakeMemberRepository /
 * FakeBootcampRepository, so the same service-reuse path production uses
 * is the one under test. No database, no AI, no network.
 *
 * Payments are given explicit historical dates through
 * Payment.setPaymentDateFromDb() (the same hook the real repository uses
 * to restore stored dates), so date-range behaviour is tested against
 * fixed calendar dates rather than "whatever today happens to be".
 */
class AdminOperationsServiceTest {

    /** A FakeMemberRepository that counts writes, to prove the service never makes any. */
    private static class WriteCountingMemberRepository extends FakeMemberRepository {
        int writes = 0;
        @Override public void save(Member member)   { writes++; super.save(member); }
        @Override public void delete(String id)     { writes++; super.delete(id); }
    }

    private WriteCountingMemberRepository memberRepo;
    private FakeBootcampRepository       bootcampRepo;
    private MemberService                memberService;
    private MembershipService            membershipService;
    private AdminOperationsService       ops;

    @BeforeEach
    void setUp() {
        memberRepo        = new WriteCountingMemberRepository();
        bootcampRepo      = new FakeBootcampRepository();
        memberService     = new MemberService(memberRepo);
        membershipService = new MembershipService(memberRepo, bootcampRepo);
        ops               = new AdminOperationsService(memberService, membershipService);
    }

    // ── helpers ───────────────────────────────────────────

    private Member addMember(String id, String name) {
        return memberService.registerMember("P" + id, id, name, id.toLowerCase() + "@email.com", "07800100001");
    }

    /** Adds a payment dated {@code date}; completed unless {@code completed} is false (then it stays PENDING). */
    private void addPayment(Member member, String paymentId, double amount, LocalDate date, boolean completed) {
        Payment payment = new Payment(paymentId, amount, "Test payment " + paymentId, member);
        if (completed) payment.markCompleted();
        payment.setPaymentDateFromDb(date);
        member.addPayment(payment);
        memberRepo.save(member);
    }

    private void addCompleted(Member member, String paymentId, double amount, LocalDate date) {
        addPayment(member, paymentId, amount, date, true);
    }

    private Member addMemberWithStandardMembership(String id, String name, LocalDate nextPaymentDue) {
        Member member = addMember(id, name);
        memberService.assignMembership(id, new StandardMembership("MEM-" + id, 12));
        member.getMembership().setNextPaymentDueDate(nextPaymentDue);
        return member;
    }

    // ══════════════════════════════════════════════════════
    //  getRevenueSummary
    // ══════════════════════════════════════════════════════

    @Test
    void allTimeRevenueCountsOnlyCompletedPayments() {
        Member alice = addMember("M001", "Alice Smith");
        addCompleted(alice, "PAY-1", 40.00, LocalDate.of(2026, 8, 5));
        addCompleted(alice, "PAY-2", 25.50, LocalDate.of(2026, 9, 5));
        addPayment(alice, "PAY-3", 100.00, LocalDate.of(2026, 9, 6), false);   // PENDING
        Payment failed = new Payment("PAY-4", 70.00, "Failed card", alice);
        failed.markFailed();
        failed.setPaymentDateFromDb(LocalDate.of(2026, 9, 7));
        alice.addPayment(failed);

        RevenueSummary summary = ops.getRevenueSummary(null, null);

        assertEquals(65.50, summary.totalRevenue());
        assertEquals(2, summary.paymentCount());
        assertEquals(2, summary.excludedNonCompletedPayments());
        assertEquals("GBP", summary.currency());
    }

    @Test
    void revenueDateRangeIsInclusiveOnBothEnds() {
        Member alice = addMember("M001", "Alice Smith");
        addCompleted(alice, "PAY-0", 10.00, LocalDate.of(2026, 8, 31));   // day before — out
        addCompleted(alice, "PAY-1", 20.00, LocalDate.of(2026, 9, 1));    // first day — in
        addCompleted(alice, "PAY-2", 30.00, LocalDate.of(2026, 9, 15));   // middle — in
        addCompleted(alice, "PAY-3", 40.00, LocalDate.of(2026, 9, 30));   // last day — in
        addCompleted(alice, "PAY-4", 50.00, LocalDate.of(2026, 10, 1));   // day after — out

        RevenueSummary september = ops.getRevenueSummary(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertEquals(90.00, september.totalRevenue());
        assertEquals(3, september.paymentCount());
        assertEquals(30.00, september.averagePayment());
    }

    @Test
    void openEndedRangesActAsMissingBounds() {
        Member alice = addMember("M001", "Alice Smith");
        addCompleted(alice, "PAY-1", 10.00, LocalDate.of(2026, 1, 10));
        addCompleted(alice, "PAY-2", 20.00, LocalDate.of(2026, 6, 10));
        addCompleted(alice, "PAY-3", 30.00, LocalDate.of(2026, 9, 10));

        assertEquals(50.00, ops.getRevenueSummary(LocalDate.of(2026, 6, 1), null).totalRevenue());
        assertEquals(30.00, ops.getRevenueSummary(null, LocalDate.of(2026, 6, 30)).totalRevenue());
    }

    @Test
    void revenueIsBrokenDownByMonthInDateOrder() {
        Member alice = addMember("M001", "Alice Smith");
        Member bob   = addMember("M002", "Bob Jones");
        addCompleted(alice, "PAY-1", 40.00, LocalDate.of(2026, 9, 3));
        addCompleted(bob,   "PAY-2", 60.00, LocalDate.of(2026, 9, 20));
        addCompleted(alice, "PAY-3", 25.00, LocalDate.of(2026, 7, 11));

        RevenueSummary summary = ops.getRevenueSummary(null, null);

        assertEquals(2, summary.byMonth().size());
        assertEquals("2026-07", summary.byMonth().get(0).month());
        assertEquals(25.00, summary.byMonth().get(0).revenue());
        assertEquals("2026-09", summary.byMonth().get(1).month());
        assertEquals(100.00, summary.byMonth().get(1).revenue());
        assertEquals(2, summary.byMonth().get(1).paymentCount());
    }

    @Test
    void revenueDoesNotSufferFloatingPointDrift() {
        Member alice = addMember("M001", "Alice Smith");
        addCompleted(alice, "PAY-1", 0.10, LocalDate.of(2026, 9, 1));
        addCompleted(alice, "PAY-2", 0.20, LocalDate.of(2026, 9, 2));

        assertEquals(0.30, ops.getRevenueSummary(null, null).totalRevenue());
    }

    @Test
    void emptyRevenueSaysSoExplicitlyInsteadOfJustReturningZero() {
        addMember("M001", "Alice Smith");   // a member, but no payments at all

        RevenueSummary summary = ops.getRevenueSummary(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertEquals(0.0, summary.totalRevenue());
        assertEquals(0, summary.paymentCount());
        assertNull(summary.averagePayment());
        assertTrue(summary.byMonth().isEmpty());
        assertTrue(summary.note().contains("No completed payments"));
        assertTrue(summary.note().contains("2026-09-01"));
    }

    @Test
    void revenueWithNoMembersAtAllIsAlsoExplicitlyEmpty() {
        RevenueSummary summary = ops.getRevenueSummary(null, null);

        assertEquals(0, summary.paymentCount());
        assertTrue(summary.note().contains("No completed payments"));
    }

    @Test
    void revenueRangeWithStartAfterEndIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            ops.getRevenueSummary(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 1)));
    }

    // ══════════════════════════════════════════════════════
    //  getOverdueMembers
    // ══════════════════════════════════════════════════════

    @Test
    void overdueMembersListsOnlyThoseWithOverduePayments() {
        LocalDate today = LocalDate.now();
        addMemberWithStandardMembership("M001", "Alice Smith", today.minusDays(10));    // overdue
        addMemberWithStandardMembership("M002", "Bob Jones",   today.plusDays(45));     // paid up
        addMember("M003", "Carol No-Membership");                                       // no membership
        addMember("M004", "Dan Payg");
        memberService.assignMembership("M004", new PayAsYouGoMembership("MEM-M004"));   // no due date

        OverdueMembers result = ops.getOverdueMembers();

        assertEquals(1, result.totalOverdue());
        OverdueMember overdue = result.members().get(0);
        assertEquals("M001", overdue.memberId());
        assertEquals("Alice Smith", overdue.name());
        assertEquals(10, overdue.daysOverdue());
        assertEquals(today.minusDays(10), overdue.nextPaymentDueDate());
        assertTrue(overdue.memberActive());
        assertFalse(result.truncated());
    }

    @Test
    void overdueMembersAreOrderedLongestOverdueFirst() {
        LocalDate today = LocalDate.now();
        addMemberWithStandardMembership("M001", "Alice Smith", today.minusDays(5));
        addMemberWithStandardMembership("M002", "Bob Jones",   today.minusDays(40));
        addMemberWithStandardMembership("M003", "Carol King",  today.minusDays(20));

        List<String> order = ops.getOverdueMembers().members().stream().map(OverdueMember::memberId).toList();

        assertEquals(List.of("M002", "M003", "M001"), order);
    }

    @Test
    void overdueReportsWhetherTheMemberIsStillActive() {
        addMemberWithStandardMembership("M001", "Alice Smith", LocalDate.now().minusDays(10));
        memberService.deactivateMember("M001");

        assertFalse(ops.getOverdueMembers().members().get(0).memberActive());
    }

    @Test
    void nobodyOverdueIsReportedExplicitly() {
        addMemberWithStandardMembership("M001", "Alice Smith", LocalDate.now().plusDays(45));

        OverdueMembers result = ops.getOverdueMembers();

        assertEquals(0, result.totalOverdue());
        assertTrue(result.members().isEmpty());
        assertTrue(result.note().contains("No members"));
    }

    @Test
    void overdueListIsCappedButTheTrueTotalIsStillReported() {
        LocalDate longAgo = LocalDate.now().minusDays(30);
        for (int i = 1; i <= AdminOperationsService.MAX_LIST_ROWS + 5; i++) {
            addMemberWithStandardMembership(String.format("M%03d", i), "Member " + i, longAgo);
        }

        OverdueMembers result = ops.getOverdueMembers();

        assertEquals(AdminOperationsService.MAX_LIST_ROWS + 5, result.totalOverdue());
        assertEquals(AdminOperationsService.MAX_LIST_ROWS, result.returned());
        assertEquals(AdminOperationsService.MAX_LIST_ROWS, result.members().size());
        assertTrue(result.truncated());
        assertTrue(result.note().contains("Showing"));
    }

    // ══════════════════════════════════════════════════════
    //  getMembershipStats
    // ══════════════════════════════════════════════════════

    @Test
    void membershipStatsCountMembersMembershipsAndPaymentStatuses() {
        LocalDate today = LocalDate.now();
        Member alice = addMemberWithStandardMembership("M001", "Alice Smith", today.minusDays(10));   // overdue
        addMemberWithStandardMembership("M002", "Bob Jones",  today.plusDays(45));                    // paid
        addMemberWithStandardMembership("M003", "Carol King", today.plusDays(50));                    // paid
        addMember("M004", "Dan Payg");
        memberService.assignMembership("M004", new PayAsYouGoMembership("MEM-M004"));                 // no due date
        addMember("M005", "Eve Nomembership");
        memberService.deactivateMember("M005");

        String standardType = alice.getMembership().getMembershipType();
        String paygType     = memberService.getMemberById("M004").getMembership().getMembershipType();

        MembershipStats stats = ops.getMembershipStats();

        assertEquals(5, stats.totalMembers());
        assertEquals(4, stats.activeMembers());
        assertEquals(1, stats.inactiveMembers());
        assertEquals(4, stats.membersWithMembership());
        assertEquals(1, stats.membersWithoutMembership());
        assertEquals(Map.of(standardType, 3, paygType, 1), stats.membershipsByType());
        assertEquals(2, stats.paymentStatus().paid());
        assertEquals(1, stats.paymentStatus().overdue());
        assertEquals(1, stats.paymentStatus().noRecurringDueDate());
        assertEquals(0, stats.paymentStatus().dueSoon());
    }

    @Test
    void paymentStatusCountsAlwaysAddUpToTheMembersWhoHoldAMembership() {
        LocalDate today = LocalDate.now();
        addMemberWithStandardMembership("M001", "Alice Smith", today.minusDays(3));
        addMemberWithStandardMembership("M002", "Bob Jones",   today.plusDays(1));
        addMemberWithStandardMembership("M003", "Carol King",  today.plusDays(60));
        addMember("M004", "Dan Payg");
        memberService.assignMembership("M004", new PayAsYouGoMembership("MEM-M004"));

        MembershipStats stats = ops.getMembershipStats();
        var counts = stats.paymentStatus();

        assertEquals(stats.membersWithMembership(),
            counts.paid() + counts.dueSoon() + counts.overdue() + counts.noRecurringDueDate());
    }

    @Test
    void frozenMembershipsAreCounted() {
        addMemberWithStandardMembership("M001", "Alice Smith", LocalDate.now().plusDays(45));
        addMemberWithStandardMembership("M002", "Bob Jones",   LocalDate.now().plusDays(45));
        membershipService.freezeMembership("M002");

        assertEquals(1, ops.getMembershipStats().frozenMemberships());
    }

    @Test
    void membershipStatsWithNoMembersSaysSo() {
        MembershipStats stats = ops.getMembershipStats();

        assertEquals(0, stats.totalMembers());
        assertTrue(stats.membershipsByType().isEmpty());
        assertTrue(stats.note().contains("No members"));
    }

    // ══════════════════════════════════════════════════════
    //  getClassOccupancy
    // ══════════════════════════════════════════════════════

    private BootcampClass addClass(String classId, int capacity, String... enrolledMemberIds) {
        BootcampClass bc = new BootcampClass(classId, BootcampType.FAT_BURN, "Mon 18:00", capacity);
        for (String memberId : enrolledMemberIds) {
            bc.enrolMember(memberService.getMemberById(memberId));
        }
        membershipService.addBootcampClass(bc);
        return bc;
    }

    @Test
    void classOccupancyReportsCapacityEnrolmentAndFullness() {
        for (int i = 1; i <= 5; i++) addMember("M00" + i, "Member " + i);
        addClass("BC-1", 2, "M001", "M002");            // full
        addClass("BC-2", 10, "M003", "M004", "M005");   // 30%

        ClassOccupancy occupancy = ops.getClassOccupancy();

        assertEquals(2, occupancy.classCount());
        assertEquals(12, occupancy.totalCapacity());
        assertEquals(5, occupancy.totalEnrolled());
        assertEquals(41.7, occupancy.overallOccupancyPercent());

        ClassOccupancyItem full = occupancy.classes().get(0);
        assertEquals("BC-1", full.classId());
        assertEquals(2, full.enrolled());
        assertEquals(0, full.availableSpots());
        assertEquals(100.0, full.occupancyPercent());
        assertTrue(full.full());

        ClassOccupancyItem open = occupancy.classes().get(1);
        assertEquals("BC-2", open.classId());
        assertEquals(7, open.availableSpots());
        assertEquals(30.0, open.occupancyPercent());
        assertFalse(open.full());
        assertNull(open.instructor());
    }

    @Test
    void scratchSchedulingClassesAreExcludedLikeTheDashboardDoes() {
        addMember("M001", "Alice Smith");
        addClass("BC-1", 10, "M001");
        addClass("SCHED-A", 5);
        addClass("SCHED-B", 5);

        ClassOccupancy occupancy = ops.getClassOccupancy();

        assertEquals(1, occupancy.classCount());
        assertEquals(10, occupancy.totalCapacity());
        assertTrue(occupancy.classes().stream().noneMatch(c -> c.classId().startsWith("SCHED-")));
    }

    @Test
    void cancelledClassesAreFlagged() {
        BootcampClass bc = addClass("BC-1", 10);
        bc.cancel();
        bootcampRepo.save(bc);

        assertTrue(ops.getClassOccupancy().classes().get(0).cancelled());
    }

    @Test
    void noClassesIsReportedExplicitly() {
        ClassOccupancy occupancy = ops.getClassOccupancy();

        assertEquals(0, occupancy.classCount());
        assertEquals(0.0, occupancy.overallOccupancyPercent());
        assertTrue(occupancy.note().contains("No bootcamp classes"));
    }

    // ══════════════════════════════════════════════════════
    //  getRecentPayments
    // ══════════════════════════════════════════════════════

    private void addCompletedPaymentsOnConsecutiveDays(Member member, int count) {
        LocalDate start = LocalDate.of(2026, 9, 1);
        for (int i = 0; i < count; i++) {
            addCompleted(member, String.format("PAY-%03d", i), 10.00 + i, start.plusDays(i));
        }
    }

    @Test
    void recentPaymentsAreNewestFirstAndDefaultToTen() {
        Member alice = addMember("M001", "Alice Smith");
        addCompletedPaymentsOnConsecutiveDays(alice, 12);

        RecentPayments result = ops.getRecentPayments(null);

        assertEquals(10, result.returned());
        assertEquals(12, result.totalCompletedPayments());
        assertEquals(LocalDate.of(2026, 9, 12), result.payments().get(0).paymentDate());
        assertEquals("PAY-011", result.payments().get(0).paymentId());
        assertEquals("Alice Smith", result.payments().get(0).memberName());
        assertEquals("M001", result.payments().get(0).memberId());
        assertEquals(LocalDate.of(2026, 9, 3), result.payments().get(9).paymentDate());
    }

    @Test
    void recentPaymentsLimitIsClampedToASafeRange() {
        Member alice = addMember("M001", "Alice Smith");
        addCompletedPaymentsOnConsecutiveDays(alice, AdminOperationsService.MAX_RECENT_PAYMENTS + 5);

        assertEquals(3, ops.getRecentPayments(3).returned());
        assertEquals(1, ops.getRecentPayments(0).returned());
        assertEquals(1, ops.getRecentPayments(-7).returned());
        assertEquals(AdminOperationsService.MAX_RECENT_PAYMENTS, ops.getRecentPayments(1000).returned());
    }

    @Test
    void recentPaymentsLeaveOutPendingAndFailedOnes() {
        Member alice = addMember("M001", "Alice Smith");
        addCompleted(alice, "PAY-1", 40.00, LocalDate.of(2026, 9, 1));
        addPayment(alice, "PAY-2", 99.00, LocalDate.of(2026, 9, 2), false);   // PENDING

        RecentPayments result = ops.getRecentPayments(10);

        assertEquals(1, result.returned());
        assertEquals("PAY-1", result.payments().get(0).paymentId());
        assertEquals(Payment.STATUS_COMPLETED, result.payments().get(0).status());
    }

    @Test
    void noPaymentsAtAllIsReportedExplicitly() {
        addMember("M001", "Alice Smith");

        RecentPayments result = ops.getRecentPayments(null);

        assertEquals(0, result.returned());
        assertTrue(result.note().contains("No completed payments"));
    }

    // ══════════════════════════════════════════════════════
    //  guarantees that matter for an AI-facing service
    // ══════════════════════════════════════════════════════

    @Test
    void runningEveryOperationNeverWritesToTheRepository() {
        LocalDate today = LocalDate.now();
        Member alice = addMemberWithStandardMembership("M001", "Alice Smith", today.minusDays(10));
        addCompleted(alice, "PAY-1", 40.00, LocalDate.of(2026, 9, 1));
        addClass("BC-1", 10, "M001");

        int writesBefore = memberRepo.writes;
        int paymentsBefore = alice.getPaymentHistory().size();
        LocalDate dueBefore = alice.getMembership().getNextPaymentDueDate();

        ops.getRevenueSummary(null, null);
        ops.getOverdueMembers();
        ops.getMembershipStats();
        ops.getClassOccupancy();
        ops.getRecentPayments(5);

        assertEquals(writesBefore, memberRepo.writes);
        assertEquals(paymentsBefore, alice.getPaymentHistory().size());
        assertEquals(dueBefore, alice.getMembership().getNextPaymentDueDate());
        Membership membership = alice.getMembership();
        assertEquals("OVERDUE", membership.getPaymentStatus());   // nothing was "fixed" behind the scenes
    }

    @Test
    void resultsNeverCarryContactDetailsBecauseTheyAreSentToAnExternalAiProvider() {
        List<String> fieldNames = new ArrayList<>();
        for (Class<?> type : List.of(OverdueMember.class, PaymentItem.class)) {
            Arrays.stream(type.getRecordComponents()).forEach(c -> fieldNames.add(c.getName().toLowerCase()));
        }

        assertTrue(fieldNames.stream().noneMatch(n -> n.contains("email") || n.contains("phone")),
            "AI-facing results must not include email or phone: " + fieldNames);
    }
}
