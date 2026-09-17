package kg.megalab.pivnitsabackend.service;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.Duration;

@Service
public class RefundPolicyCalculator {
    public int calculateRefundPercentage(OffsetDateTime bookingAt, OffsetDateTime cancelledAt) {
        long hoursBeforeCancelling = Duration.between(cancelledAt, bookingAt).toHours();
        return hoursBeforeCancelling >= 24 ? 100 : 50;
    }
}
