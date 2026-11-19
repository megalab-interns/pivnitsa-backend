package kg.megalab.pivnitsabackend.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RefundPolicyCalculatorTest {

    @Test
    void shouldReturn100PercentageWhenCancelledMoreThan24HoursBeforeBooking() {
        RefundPolicyCalculator calculator = new RefundPolicyCalculator();
        OffsetDateTime cancelledAt = OffsetDateTime.now();
        OffsetDateTime bookingAt = cancelledAt.plusHours(48);
        int result = calculator.calculateRefundPercentage(bookingAt, cancelledAt);
        assertEquals(100, result);
    }

    @Test
    void shouldReturn50PercentageWhenCancelledLessThan24HoursBeforeBooking() {
        RefundPolicyCalculator calculator = new RefundPolicyCalculator();
        OffsetDateTime cancelledAt = OffsetDateTime.now();
        OffsetDateTime bookingAt = cancelledAt.plusHours(20);
        int result = calculator.calculateRefundPercentage(bookingAt, cancelledAt);
        assertEquals(50, result);
    }
}
