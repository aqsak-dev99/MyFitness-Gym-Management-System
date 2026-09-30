package com.gymmanagement.model.membership;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the new membership billing logic added to the Membership base
 * class — pure model logic, no repository or fake needed, matching
 * GymClassTest's own style. Uses StandardMembership as a concrete
 * stand-in since Membership itself is abstract; the logic under test
 * (getPaymentStatus/advancePaymentDueDate) lives entirely in the base
 * class, not in StandardMembership specifically.
 *
 * Due dates are set explicitly via setNextPaymentDueDate() rather than
 * relying on constructor timing, so these assertions are deterministic
 * regardless of when the test actually runs.
 */
class MembershipTest {

    @Test
    void freshlyConstructedMembershipHasNoDueDateByDefault() {
        // Confirms the NULL-by-default state applies at the model level
        // for every type, including recurring ones — it's MemberService.
        // assignMembership() that sets up real billing, not the
        // constructor. This is the correct, expected separation, not
        // an oversight: a Membership object built directly (bypassing
        // the service) genuinely has no billing configured yet.
        StandardMembership m = new StandardMembership("MEM001", 12);
        assertNull(m.getNextPaymentDueDate());
        assertEquals("N/A", m.getPaymentStatus());
    }

    @Test
    void paymentStatusIsPaidWhenDueDateIsWellInTheFuture() {
        StandardMembership m = new StandardMembership("MEM001", 12);
        m.setNextPaymentDueDate(LocalDate.now().plusDays(10));

        assertEquals("PAID", m.getPaymentStatus());
    }

    @Test
    void paymentStatusIsDueSoonAtExactlyTheThreshold() {
        StandardMembership m = new StandardMembership("MEM001", 12);
        m.setNextPaymentDueDate(LocalDate.now().plusDays(Membership.DUE_SOON_THRESHOLD_DAYS));

        assertEquals("DUE_SOON", m.getPaymentStatus());
    }

    @Test
    void paymentStatusIsDueSoonJustInsideTheThreshold() {
        StandardMembership m = new StandardMembership("MEM001", 12);
        m.setNextPaymentDueDate(LocalDate.now().plusDays(1));

        assertEquals("DUE_SOON", m.getPaymentStatus());
    }

    @Test
    void paymentStatusIsOverdueWhenDueDateHasPassed() {
        StandardMembership m = new StandardMembership("MEM001", 12);
        m.setNextPaymentDueDate(LocalDate.now().minusDays(1));

        assertEquals("OVERDUE", m.getPaymentStatus());
    }

    @Test
    void paymentStatusIsOverdueEvenLongAfterDueDate() {
        StandardMembership m = new StandardMembership("MEM001", 12);
        m.setNextPaymentDueDate(LocalDate.now().minusMonths(2));

        assertEquals("OVERDUE", m.getPaymentStatus());
    }

    @Test
    void paymentStatusIsNAForPayAsYouGoEvenIfNothingSetsADueDate() {
        PayAsYouGoMembership m = new PayAsYouGoMembership("MEM001");

        assertNull(m.getNextPaymentDueDate());
        assertEquals("N/A", m.getPaymentStatus());
    }

    @Test
    void advancingPaymentDueDateMovesItForwardOneMonth() {
        StandardMembership m = new StandardMembership("MEM001", 12);
        LocalDate initialDue = LocalDate.of(2026, 1, 15);
        m.setNextPaymentDueDate(initialDue);

        m.advancePaymentDueDate();

        assertEquals(LocalDate.of(2026, 2, 15), m.getNextPaymentDueDate());
    }

    @Test
    void advancingPaymentDueDateWhenNullDoesNothingRatherThanThrowing() {
        // A PayAsYouGo membership (or any membership with no due date
        // configured) should tolerate this call harmlessly — matching
        // the same idempotent-safety discipline as freeze()/deactivate().
        PayAsYouGoMembership m = new PayAsYouGoMembership("MEM001");

        m.advancePaymentDueDate();

        assertNull(m.getNextPaymentDueDate());
    }

    @Test
    void repeatedAdvancesAccumulateCorrectly() {
        StandardMembership m = new StandardMembership("MEM001", 12);
        m.setNextPaymentDueDate(LocalDate.of(2026, 1, 1));

        m.advancePaymentDueDate();
        m.advancePaymentDueDate();
        m.advancePaymentDueDate();

        assertEquals(LocalDate.of(2026, 4, 1), m.getNextPaymentDueDate());
    }

    // ── startDate persistence-restoration fix ────────────────

    @Test
    void setStartDateOverridesTheConstructorDefault() {
        // The actual bug: reconstructMembership() had no way to
        // restore a historical startDate at all (no setter existed),
        // so every reload silently kept the constructor's own default
        // instead of the real stored value. This proves the setter
        // itself now correctly does what SqliteMemberRepository needs
        // it to do — the live database round-trip is verified
        // separately, the same way every other repository behavior in
        // this project is (no repository-level JUnit tests exist
        // anywhere here; they'd require a live Postgres connection).
        StandardMembership m = new StandardMembership("MEM001", 12);
        LocalDate constructedStartDate = m.getStartDate();
        LocalDate realHistoricalStartDate = LocalDate.of(2026, 3, 15);

        m.setStartDate(realHistoricalStartDate);

        assertEquals(realHistoricalStartDate, m.getStartDate());
        assertNotEquals(constructedStartDate, m.getStartDate(),
            "Sanity check that this test actually changed something, not comparing a value to itself");
    }

    @Test
    void setStartDateDoesNotAffectEndDateOrOtherFields() {
        StandardMembership m = new StandardMembership("MEM001", 12);
        LocalDate originalEndDate = m.getEndDate();

        m.setStartDate(LocalDate.of(2020, 1, 1));

        assertEquals(originalEndDate, m.getEndDate());
    }

    // ── getMembershipType ─────────────────────────────────

    @Test
    void getMembershipTypeReturnsRealHumanReadableNames() {
        assertEquals("Standard", new StandardMembership("MEM001", 12).getMembershipType());
        assertEquals("Student Saver", new StudentSaverMembership("MEM002", "S12345").getMembershipType());
        assertEquals("Pay As You Go", new PayAsYouGoMembership("MEM003").getMembershipType());
    }
}