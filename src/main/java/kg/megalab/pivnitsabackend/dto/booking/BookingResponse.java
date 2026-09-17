package kg.megalab.pivnitsabackend.dto.booking;

import kg.megalab.pivnitsabackend.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BookingResponse(
        Long id,
        Long clubTableId,
        String tableNumber,
        Long eventId,
        OffsetDateTime bookingAt,
        OffsetDateTime createdAt,
        BookingStatus status,
        BigDecimal amount,
        Integer guestsCount,
        Integer refundPercentage,
        String comment
) {
}
