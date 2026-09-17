package kg.megalab.pivnitsabackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "club_table_id", nullable = false)
    private Long clubTableId;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "booking_at", nullable = false)
    private OffsetDateTime bookingAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "refund_percentage")
    private Integer refundPercentage;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Builder.Default
    @Column(name = "guests_count", nullable = false)
    private Integer guestsCount = 1;

    @Column(name = "comment")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
