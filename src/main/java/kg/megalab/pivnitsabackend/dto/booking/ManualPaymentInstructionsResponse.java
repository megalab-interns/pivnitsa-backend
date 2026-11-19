package kg.megalab.pivnitsabackend.dto.booking;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ManualPaymentInstructionsResponse(
        Long bookingId,
        String tableNumber,
        BigDecimal amount,
        OffsetDateTime paymentDeadline,
        boolean paymentWindowElapsed,
        String qrImageUrl,
        String whatsappUrl
) {
}
