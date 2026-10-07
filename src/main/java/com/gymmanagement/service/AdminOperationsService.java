package com.gymmanagement.service;

import com.gymmanagement.model.BootcampClass;
import com.gymmanagement.model.Member;
import com.gymmanagement.model.Payment;
import com.gymmanagement.model.membership.Membership;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * AdminOperationsService — the read-only operational questions an admin
 * asks about the gym ("how much did we make this month", "who is
 * overdue", "which classes are full"), answered from real data.
 *
 * Why this class exists: the Revenue and Reports pages compute their
 * numbers in the browser (AdminRevenue.jsx / AdminReports.jsx) from
 * GET /api/members and GET /api/bootcamp-classes. There was no backend
 * revenue or reporting service for the AI to reuse, so this is that
 * missing server-side piece. It does NOT introduce a new data path: it
 * calls the exact same MemberService.getAllMembers() and
 * MembershipService.getAllBootcampClasses() those endpoints already
 * use (which means the same MemberRepository.findAll() batch loading),
 * and it copies the Revenue/Reports pages' definitions on purpose so an
 * AI answer matches what the admin sees on screen:
 *   - revenue counts payments whose status is COMPLETED, nothing else
 *   - payment status (PAID / DUE_SOON / OVERDUE / N/A) comes from
 *     Membership.getPaymentStatus(), derived on read, never stored
 *   - SCHED-* classes are scratch test rows and are left out of class
 *     metrics, exactly as the Dashboard and Reports pages leave them out
 *
 * Read-only by construction, not by convention: this class only ever
 * calls getters and the two list-returning service methods above. It has
 * no repository field, no save call, and no path to any method that
 * records, changes or refunds a payment. That is the guarantee the AI
 * layer relies on for "money-related operations stay read-only".
 *
 * Every method returns a small immutable record (plain data, easy to
 * assert on in tests) rather than prose or a Map. Each record that can
 * legitimately be empty carries a {@code note} explaining WHY it is
 * empty, so the model is handed "there is no data" as an explicit fact
 * instead of having to infer it from a zero.
 *
 * Personal data is deliberately minimal: member id and name only. Email
 * and phone are never included, because these records are sent to an
 * external AI provider and no operational question needs them.
 */
@Service
public class AdminOperationsService {

    /** Most rows any list-style result will ever contain. */
    static final int MAX_LIST_ROWS = 50;

    static final int DEFAULT_RECENT_PAYMENTS = 10;
    static final int MAX_RECENT_PAYMENTS     = 25;

    /** Same rule as Dashboard.jsx / AdminReports.jsx: scratch scheduling-test classes. */
    static final String EXCLUDED_CLASS_PREFIX = "SCHED-";

    static final String CURRENCY = "GBP";

    private final MemberService     memberService;
    private final MembershipService membershipService;

    public AdminOperationsService(MemberService memberService, MembershipService membershipService) {
        this.memberService     = memberService;
        this.membershipService = membershipService;
    }

    // ── result shapes ─────────────────────────────────────

    public record MonthlyRevenue(String month, double revenue, int paymentCount) {}

    public record RevenueSummary(
        String  currency,
        LocalDate startDate,                 // null = no lower bound
        LocalDate endDate,                   // null = no upper bound
        double  totalRevenue,
        int     paymentCount,
        Double  averagePayment,              // null when there are no payments
        List<MonthlyRevenue> byMonth,
        int     excludedNonCompletedPayments,
        String  note
    ) {}

    public record OverdueMember(
        String  memberId,
        String  name,
        String  membershipType,
        LocalDate nextPaymentDueDate,
        long    daysOverdue,
        double  monthlyFee,
        boolean memberActive,
        boolean membershipFrozen
    ) {}

    public record OverdueMembers(
        int     totalOverdue,
        int     returned,
        boolean truncated,
        List<OverdueMember> members,
        String  note
    ) {}

    public record PaymentStatusCounts(int paid, int dueSoon, int overdue, int noRecurringDueDate) {}

    public record MembershipStats(
        int     totalMembers,
        int     activeMembers,
        int     inactiveMembers,
        int     membersWithMembership,
        int     membersWithoutMembership,
        int     frozenMemberships,
        Map<String, Integer> membershipsByType,
        PaymentStatusCounts  paymentStatus,
        String  note
    ) {}

    public record ClassOccupancyItem(
        String  classId,
        String  className,
        String  schedule,
        String  instructor,                  // null = not assigned
        int     capacity,
        int     enrolled,
        int     availableSpots,
        double  occupancyPercent,
        boolean full,
        boolean cancelled
    ) {}

    public record ClassOccupancy(
        int     classCount,
        int     totalCapacity,
        int     totalEnrolled,
        double  overallOccupancyPercent,
        List<ClassOccupancyItem> classes,
        String  note
    ) {}

    public record PaymentItem(
        String  paymentId,
        String  memberId,
        String  memberName,
        String  description,
        double  amount,
        LocalDate paymentDate,
        String  status
    ) {}

    public record RecentPayments(
        int     returned,
        int     totalCompletedPayments,
        List<PaymentItem> payments,
        String  note
    ) {}

    // ── revenue ───────────────────────────────────────────

    /**
     * Total of COMPLETED payments dated within [startDate, endDate],
     * both inclusive. Either bound may be null, meaning "no limit on
     * that side", so a null/null call is all-time revenue — the same
     * "Total Revenue" figure the Revenue page shows.
     *
     * Pending and failed payments are never counted as revenue; they are
     * only reported as a count so the answer can say they were left out.
     *
     * @throws IllegalArgumentException if startDate is after endDate
     */
    public RevenueSummary getRevenueSummary(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                "startDate (" + startDate + ") must not be after endDate (" + endDate + ").");
        }

        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        int excluded = 0;
        Map<YearMonth, BigDecimal> monthTotals = new TreeMap<>();
        Map<YearMonth, Integer>    monthCounts = new TreeMap<>();

        for (Member member : memberService.getAllMembers()) {
            for (Payment payment : member.getPaymentHistory()) {
                if (!withinRange(payment.getPaymentDate(), startDate, endDate)) continue;

                if (!Payment.STATUS_COMPLETED.equals(payment.getStatus())) {
                    excluded++;
                    continue;
                }
                BigDecimal amount = BigDecimal.valueOf(payment.getAmount());
                total = total.add(amount);
                count++;

                YearMonth month = YearMonth.from(payment.getPaymentDate());
                monthTotals.merge(month, amount, BigDecimal::add);
                monthCounts.merge(month, 1, Integer::sum);
            }
        }

        List<MonthlyRevenue> byMonth = new ArrayList<>();
        monthTotals.forEach((month, sum) ->
            byMonth.add(new MonthlyRevenue(month.toString(), money(sum), monthCounts.get(month))));

        Double average = count == 0 ? null : money(total.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP));

        String note = count == 0
            ? "No completed payments are recorded " + describeRange(startDate, endDate) + "."
            : "Revenue is the sum of completed payments recorded in MyFitness. It does not include expenses or profit.";

        return new RevenueSummary(CURRENCY, startDate, endDate, money(total), count, average,
                                  byMonth, excluded, note);
    }

    // ── overdue members ───────────────────────────────────

    /**
     * Members whose membership payment status is OVERDUE, longest
     * overdue first. Uses Membership.getPaymentStatus() — the single
     * place that rule lives — instead of re-deriving it from dates.
     */
    public OverdueMembers getOverdueMembers() {
        LocalDate today = LocalDate.now();
        List<OverdueMember> overdue = new ArrayList<>();

        for (Member member : memberService.getAllMembers()) {
            Membership membership = member.getMembership();
            if (membership == null || !"OVERDUE".equals(membership.getPaymentStatus())) continue;

            LocalDate due = membership.getNextPaymentDueDate();
            long daysOverdue = due == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(due, today));

            overdue.add(new OverdueMember(
                member.getMemberId(), member.getName(), membership.getMembershipType(),
                due, daysOverdue, membership.getMonthlyFee(),
                member.isActive(), membership.isFrozen()));
        }

        overdue.sort(Comparator.comparingLong(OverdueMember::daysOverdue).reversed()
                               .thenComparing(OverdueMember::memberId));

        int total = overdue.size();
        List<OverdueMember> shown = total > MAX_LIST_ROWS ? overdue.subList(0, MAX_LIST_ROWS) : overdue;

        String note = total == 0
            ? "No members currently have an overdue membership payment."
            : total > MAX_LIST_ROWS
                ? "Showing the " + MAX_LIST_ROWS + " longest-overdue of " + total + " overdue members."
                : null;

        return new OverdueMembers(total, shown.size(), total > MAX_LIST_ROWS, List.copyOf(shown), note);
    }

    // ── membership statistics ─────────────────────────────

    /**
     * Head-counts across all members: active vs inactive, who holds
     * which membership type, frozen memberships, and the payment-status
     * breakdown. The payment-status counts are taken over members who
     * hold a membership only — identical to the Revenue page's
     * "Payment Status Breakdown".
     */
    public MembershipStats getMembershipStats() {
        List<Member> members = memberService.getAllMembers();

        int active = 0, withMembership = 0, frozen = 0;
        int paid = 0, dueSoon = 0, overdue = 0, notApplicable = 0;
        Map<String, Integer> byType = new LinkedHashMap<>();

        for (Member member : members) {
            if (member.isActive()) active++;

            Membership membership = member.getMembership();
            if (membership == null) continue;

            withMembership++;
            if (membership.isFrozen()) frozen++;
            byType.merge(membership.getMembershipType(), 1, Integer::sum);

            switch (String.valueOf(membership.getPaymentStatus())) {
                case "PAID"     -> paid++;
                case "DUE_SOON" -> dueSoon++;
                case "OVERDUE"  -> overdue++;
                default         -> notApplicable++;   // "N/A": no recurring due date (e.g. Pay As You Go)
            }
        }

        int total = members.size();
        String note = total == 0 ? "No members are registered yet." : null;

        return new MembershipStats(
            total, active, total - active, withMembership, total - withMembership, frozen,
            byType, new PaymentStatusCounts(paid, dueSoon, overdue, notApplicable), note);
    }

    // ── class occupancy ───────────────────────────────────

    /**
     * Capacity, enrolment and fullness for every bootcamp class, using
     * the same figures GymClass already exposes (getMaxCapacity,
     * getCurrentEnrolments, isFull, isCancelled). Scratch SCHED-* rows
     * are excluded, matching the Dashboard and Reports pages.
     */
    public ClassOccupancy getClassOccupancy() {
        List<ClassOccupancyItem> items = new ArrayList<>();
        int totalCapacity = 0, totalEnrolled = 0;

        for (BootcampClass bc : membershipService.getAllBootcampClasses()) {
            if (bc.getClassId().startsWith(EXCLUDED_CLASS_PREFIX)) continue;

            int capacity = bc.getMaxCapacity();
            int enrolled = bc.getCurrentEnrolments();
            totalCapacity += capacity;
            totalEnrolled += enrolled;

            items.add(new ClassOccupancyItem(
                bc.getClassId(), bc.getClassName(), bc.getSchedule(),
                bc.getInstructor() != null ? bc.getInstructor().getName() : null,
                capacity, enrolled, Math.max(0, capacity - enrolled),
                percent(enrolled, capacity), bc.isFull(), bc.isCancelled()));
        }

        items.sort(Comparator.comparing(ClassOccupancyItem::classId));

        String note = items.isEmpty() ? "No bootcamp classes have been created yet." : null;

        return new ClassOccupancy(items.size(), totalCapacity, totalEnrolled,
                                  percent(totalEnrolled, totalCapacity), List.copyOf(items), note);
    }

    // ── recent payments ───────────────────────────────────

    /**
     * The most recent COMPLETED payments, newest first — the Revenue
     * page's "Recent Payments" list. limit is clamped to
     * [1, MAX_RECENT_PAYMENTS]; null means the default of 10.
     */
    public RecentPayments getRecentPayments(Integer limit) {
        int effective = limit == null ? DEFAULT_RECENT_PAYMENTS
                                      : Math.max(1, Math.min(limit, MAX_RECENT_PAYMENTS));

        List<PaymentItem> completed = new ArrayList<>();
        for (Member member : memberService.getAllMembers()) {
            for (Payment payment : member.getPaymentHistory()) {
                if (!Payment.STATUS_COMPLETED.equals(payment.getStatus())) continue;
                completed.add(new PaymentItem(
                    payment.getPaymentId(), member.getMemberId(), member.getName(),
                    payment.getDescription(), money(BigDecimal.valueOf(payment.getAmount())),
                    payment.getPaymentDate(), payment.getStatus()));
            }
        }

        completed.sort(Comparator.comparing(PaymentItem::paymentDate).reversed()
                                 .thenComparing(PaymentItem::paymentId));

        List<PaymentItem> shown = completed.size() > effective ? completed.subList(0, effective) : completed;
        String note = completed.isEmpty() ? "No completed payments have been recorded yet." : null;

        return new RecentPayments(shown.size(), completed.size(), List.copyOf(shown), note);
    }

    // ── helpers ───────────────────────────────────────────

    private static boolean withinRange(LocalDate date, LocalDate start, LocalDate end) {
        if (date == null) return false;
        if (start != null && date.isBefore(start)) return false;
        return end == null || !date.isAfter(end);
    }

    private static String describeRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) return "at all";
        if (start == null) return "up to " + end;
        if (end == null)   return "from " + start + " onwards";
        return "between " + start + " and " + end;
    }

    /** Currency to 2 decimal places, as a double only at the edge. Summing happens in BigDecimal so 0.1 + 0.2 never leaks into an answer. */
    private static double money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double percent(int part, int whole) {
        if (whole <= 0) return 0.0;
        return BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
