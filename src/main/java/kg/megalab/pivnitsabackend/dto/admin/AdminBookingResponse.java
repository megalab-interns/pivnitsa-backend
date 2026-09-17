package kg.megalab.pivnitsabackend.dto.admin;

import kg.megalab.pivnitsabackend.entity.BookingStatus;
import kg.megalab.pivnitsabackend.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AdminBookingResponse(
        Long id,
        String tableNumber,
        OffsetDateTime bookingAt,
        String firstName,
        String lastName,
        String guestPhone,
        Integer guestsCount,
        BigDecimal amount,
        BookingStatus bookingStatus,
        PaymentStatus paymentStatus,
        String cancellationReason,
        String comment
) {
}
