package com.gymmanagement.model;

import com.gymmanagement.model.membership.BootcampType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the discount math in BootcampClass — no repository, no database,
 * no fakes needed. This is the simplest kind of unit test: pure logic in,
 * checked value out. Start here before anything involving a fake repository.
 */
class BootcampFeeTest {

    @Test
    void oneClassChargesFullFee() {
        BootcampClass bc = new BootcampClass("BC001", BootcampType.FAT_BURN, "Mon 07:00", 10);

        double fee = bc.calcBootcampFee(1);

        // Note the third argument — a "delta" (tolerance). Doubles are never
        // exactly equal after arithmetic, so you compare "close enough,"
        // not "identical." Forgetting this is a classic first-test mistake.
        assertEquals(35.50, fee, 0.001, "One class should charge the full base fee");
    }

    @Test
    void twoOrMoreClassesApplySevenPercentDiscount() {
        BootcampClass bc = new BootcampClass("BC001", BootcampType.FAT_BURN, "Mon 07:00", 10);

        double fee = bc.calcBootcampFee(2);

        assertEquals(33.02, fee, 0.01, "Two classes should apply the 7% discount");
    }
}