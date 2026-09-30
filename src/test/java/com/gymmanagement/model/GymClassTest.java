package com.gymmanagement.model;

import com.gymmanagement.model.membership.BootcampType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the cancellation status and capacity-validation logic added to
 * GymClass for Admin class management — pure model logic, no
 * repository or fake needed, matching BootcampFeeTest's own style.
 */
class GymClassTest {

    @Test
    void newClassIsNotCancelledByDefault() {
        BootcampClass bc = new BootcampClass("BC001", BootcampType.FAT_BURN, "Mon 07:00", 10);
        assertFalse(bc.isCancelled());
    }

    @Test
    void cancellingSetsCancelledTrue() {
        BootcampClass bc = new BootcampClass("BC001", BootcampType.FAT_BURN, "Mon 07:00", 10);
        bc.cancel();
        assertTrue(bc.isCancelled());
    }

    @Test
    void reactivatingRestoresCancelledFalse() {
        BootcampClass bc = new BootcampClass("BC001", BootcampType.FAT_BURN, "Mon 07:00", 10);
        bc.cancel();
        bc.reactivate();
        assertFalse(bc.isCancelled());
    }

    @Test
    void enrolmentIsRejectedIntoACancelledClass() {
        BootcampClass bc = new BootcampClass("BC001", BootcampType.FAT_BURN, "Mon 07:00", 10);
        bc.cancel();
        Member member = new Member("P001", "M001", "Alice", "a@a.com", "0");

        boolean enrolled = bc.enrolMember(member);

        assertFalse(enrolled, "Enrolling into a cancelled class should be rejected, not silently allowed");
        assertEquals(0, bc.getCurrentEnrolments());
    }

    @Test
    void cancellingAClassWithExistingEnrolmentsPreservesThem() {
        // The real scenario the ordering fix in SqliteBootcampRepository's
        // buildBootcampClass() protects against: a class cancelled AFTER
        // already having enrolments must not lose them.
        BootcampClass bc = new BootcampClass("BC001", BootcampType.FAT_BURN, "Mon 07:00", 10);
        Member member = new Member("P001", "M001", "Alice", "a@a.com", "0");
        bc.enrolMember(member);

        bc.cancel();

        assertEquals(1, bc.getCurrentEnrolments(), "Cancelling must not remove existing enrolments");
    }

    @Test
    void reducingCapacityBelowCurrentEnrolmentsThrows() {
        BootcampClass bc = new BootcampClass("BC001", BootcampType.FAT_BURN, "Mon 07:00", 10);
        bc.enrolMember(new Member("P001", "M001", "Alice", "a@a.com", "0"));
        bc.enrolMember(new Member("P002", "M002", "Bob", "b@b.com", "0"));

        assertThrows(IllegalArgumentException.class, () -> bc.setMaxCapacity(1),
            "Setting capacity below the real enrolled count must be rejected");
    }

    @Test
    void increasingCapacityAboveCurrentEnrolmentsSucceeds() {
        BootcampClass bc = new BootcampClass("BC001", BootcampType.FAT_BURN, "Mon 07:00", 10);
        bc.enrolMember(new Member("P001", "M001", "Alice", "a@a.com", "0"));

        bc.setMaxCapacity(20);

        assertEquals(20, bc.getMaxCapacity());
    }

    @Test
    void settingCapacityEqualToCurrentEnrolmentsSucceeds() {
        BootcampClass bc = new BootcampClass("BC001", BootcampType.FAT_BURN, "Mon 07:00", 10);
        bc.enrolMember(new Member("P001", "M001", "Alice", "a@a.com", "0"));

        bc.setMaxCapacity(1);

        assertEquals(1, bc.getMaxCapacity());
    }
}