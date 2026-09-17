package kg.megalab.pivnitsabackend.repository;

import kg.megalab.pivnitsabackend.dto.admin.AdminBookingResponse;
import kg.megalab.pivnitsabackend.dto.admin.BookingReportItemResponse;
import kg.megalab.pivnitsabackend.dto.admin.BookingReportSummaryResponse;
import kg.megalab.pivnitsabackend.entity.Booking;
import kg.megalab.pivnitsabackend.entity.BookingStatus;
import kg.megalab.pivnitsabackend.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
            SELECT COUNT(b) > 0
            FROM Booking b
            JOIN Payment p ON p.bookingId = b.id
            WHERE b.userId = :userId
              AND b.status = :status
              AND b.bookingAt > CURRENT_TIMESTAMP
              AND p.status = kg.megalab.pivnitsabackend.entity.PaymentStatus.SUCCEEDED
            """)
    boolean existsActivePaidBookingByUserId(
            @Param("userId") Long userId,
            @Param("status") BookingStatus status
    );

    @Query("""
            SELECT COUNT(b) > 0
            FROM Booking b
            WHERE b.clubTableId = :tableId
              AND b.status IN (kg.megalab.pivnitsabackend.entity.BookingStatus.CONFIRMED,
                                kg.megalab.pivnitsabackend.entity.BookingStatus.PENDING_PAYMENT)
              AND b.bookingAt > CURRENT_TIMESTAMP
            """)
    boolean existsActiveBookingByTableId(@Param("tableId") Long tableId);

    @Query("""
            SELECT b.clubTableId FROM Booking b
            WHERE b.status IN (kg.megalab.pivnitsabackend.entity.BookingStatus.CONFIRMED,
            kg.megalab.pivnitsabackend.entity.BookingStatus.PENDING_PAYMENT)
            AND b.bookingAt >= :startOfDay AND b.bookingAt < :endOfDay
            """)
    List<Long> findClubTableIdsWithActiveBookingOnDate(@Param("startOfDay") OffsetDateTime startOfDay, @Param("endOfDay") OffsetDateTime endOfDay);

    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, OffsetDateTime threshold);

    @Query("""
            SELECT COUNT(b) > 0
            FROM Booking b
            WHERE b.clubTableId = :tableId
              AND b.status IN (kg.megalab.pivnitsabackend.entity.BookingStatus.CONFIRMED,
                                kg.megalab.pivnitsabackend.entity.BookingStatus.PENDING_PAYMENT)
              AND b.bookingAt >= :startOfDay AND b.bookingAt < :endOfDay
            """)
    boolean existsActiveBookingForTableOnDate(
            @Param("tableId") Long tableId,
            @Param("startOfDay") OffsetDateTime startOfDay,
            @Param("endOfDay") OffsetDateTime endOfDay
    );

    @Query("""
            SELECT new kg.megalab.pivnitsabackend.dto.admin.AdminBookingResponse(
                b.id,
                t.tableNumber,
                b.bookingAt,
                u.firstName,
                u.lastName,
                u.phone,
                b.guestsCount,
                b.amount,
                b.status,
                p.status,
                b.cancellationReason,
                b.comment
            )
            FROM Booking b
            JOIN ClubTable t ON t.id = b.clubTableId
            JOIN User u ON u.id = b.userId
            LEFT JOIN Payment p ON p.bookingId = b.id
               AND p.id = (SELECT MAX(p2.id) FROM Payment p2 WHERE p2.bookingId = b.id)
            WHERE b.bookingAt >= :startOfDay AND b.bookingAt < :endOfDay
            ORDER BY b.bookingAt ASC
            """)
    List<AdminBookingResponse> findAdminBookingsByDate(
            @Param("startOfDay") OffsetDateTime startOfDay,
            @Param("endOfDay") OffsetDateTime endOfDay
    );

    @Query("""
                SELECT new kg.megalab.pivnitsabackend.dto.admin.BookingReportSummaryResponse(
                    COUNT(DISTINCT b.id),
                    SUM(CASE WHEN p.status = :paidStatus THEN p.amount ELSE NULL END),
                    COUNT(CASE WHEN b.status = :cancelledStatus THEN b.id ELSE NULL END)
                )
                FROM Booking b
                LEFT JOIN Payment p ON p.bookingId = b.id
                WHERE b.createdAt >= :startDate AND b.createdAt <= :endDate
            """)
    BookingReportSummaryResponse getReportSummary(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            @Param("paidStatus") PaymentStatus paidStatus,
            @Param("cancelledStatus") BookingStatus cancelledStatus
    );

    @Query("""
                SELECT new kg.megalab.pivnitsabackend.dto.admin.BookingReportItemResponse(
                    b.id,
                    b.bookingAt,
                    ct.tableNumber,
                    b.status,
                    b.amount,
                    SUM(p.amount),
                    b.cancellationReason
                )
                FROM Booking b
                LEFT JOIN ClubTable ct ON b.clubTableId = ct.id
                LEFT JOIN Payment p ON p.bookingId = b.id AND p.status = :paidStatus
                WHERE b.createdAt >= :startDate AND b.createdAt <= :endDate
                GROUP BY b.id, b.bookingAt, ct.tableNumber, b.status, b.amount, b.cancellationReason, b.createdAt
                ORDER BY b.createdAt DESC
            """)
    List<BookingReportItemResponse> getReportItems(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            @Param("paidStatus") PaymentStatus paidStatus
    );
}