package com.gymmanagement.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers Payment.setPaymentDateFromDb(), the persistence helper added
 * because rows loaded from the database used to come back dated "today"
 * (the constructor's default) instead of the date actually stored.
 */
class PaymentTest {

    private Payment newPayment() {
        Member member = new Member("P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        return new Payment("PAY-1", 40.0, "Membership renewal", member);
    }

    @Test
    void newPaymentIsDatedToday() {
        assertEquals(LocalDate.now(), newPayment().getPaymentDate());
    }

    @Test
    void restoringFromDbReplacesTodayWithTheStoredDate() {
        Payment payment = newPayment();
        LocalDate stored = LocalDate.of(2026, 3, 14);

        payment.setPaymentDateFromDb(stored);

        assertEquals(stored, payment.getPaymentDate());
    }

    @Test
    void restoringANullDateKeepsTheExistingDate() {
        Payment payment = newPayment();

        payment.setPaymentDateFromDb(null);

        assertEquals(LocalDate.now(), payment.getPaymentDate());
    }
}
