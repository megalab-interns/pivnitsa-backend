package kg.megalab.pivnitsabackend.repository;

import kg.megalab.pivnitsabackend.dto.admin.AdminBookingResponse;
import kg.megalab.pivnitsabackend.dto.admin.BookingReportItemResponse;
import kg.megalab.pivnitsabackend.dto.admin.BookingReportSummaryResponse;
import kg.megalab.pivnitsabackend.entity.Booking;
import kg.megalab.pivnitsabackend.entity.BookingStatus;
import kg.megalab.pivnitsabackend.entity.ClubTable;
import kg.megalab.pivnitsabackend.entity.Hall;
import kg.megalab.pivnitsabackend.entity.User;
import kg.megalab.pivnitsabackend.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class BookingRepositoryTest {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClubTableRepository clubTableRepository;

    @Autowired
    private HallRepository hallRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private User defaultUser;
    private ClubTable defaultTable;
    private OffsetDateTime now;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;

    @BeforeEach
    void setUp() {
        now = OffsetDateTime.now(ZoneId.of("Asia/Bishkek"));
        startDate = now.minusDays(7);
        endDate = now.plusDays(1);

        defaultUser = userRepository.save(
                User.builder()
                        .firstName("Jane")
                        .lastName("Doe")
                        .phone("+996500112233")
                        .phoneVerified(true)
                        .build()
        );

        Hall hall = hallRepository.save(
                Hall.builder().name("Main hall").build()
        );

        defaultTable = clubTableRepository.save(
                ClubTable.builder()
                        .tableNumber("T-1")
                        .hallId(hall.getId())
                        .capacity(4)
                        .active(true)
                        .build()
        );
    }

    @Test
    @DisplayName("Должен возвращать список броней по дате визита (bookingAt)")
    void shouldReturnAdminBookingsByDateWithPaymentStatus() {
        // ARRANGE
        Booking booking = createBooking(now, BookingStatus.CONFIRMED, "1500.00");
        createPayment(booking.getId(), "1500.00", PaymentStatus.SUCCEEDED);
        entityManager.flush();

        // ACT
        List<AdminBookingResponse> result = bookingRepository.findAdminBookingsByDate(
                now.minusHours(1),
                now.plusHours(23)
        );

        // ASSERT
        assertEquals(1, result.size());
        AdminBookingResponse dto = result.getFirst();
        assertEquals(booking.getId(), dto.id());
        assertEquals("T-1", dto.tableNumber());
        assertEquals("Jane", dto.firstName());
        assertEquals("Doe", dto.lastName());
        assertEquals(BookingStatus.CONFIRMED, dto.bookingStatus());
        assertEquals(PaymentStatus.SUCCEEDED, dto.paymentStatus());
    }

    @Test
    void shouldSearchBookingByIdPhoneAndFullName() {
        Booking booking = createBooking(now, BookingStatus.PENDING_PAYMENT, "1500.00");
        createPayment(booking.getId(), "1500.00", PaymentStatus.PENDING);
        entityManager.flush();

        assertEquals(booking.getId(), bookingRepository.searchAdminBookings(
                booking.getId(), "%unmatched%", PageRequest.of(0, 50)).getFirst().id());
        assertEquals(booking.getId(), bookingRepository.searchAdminBookings(
                null, "%5001122%", PageRequest.of(0, 50)).getFirst().id());
        assertEquals(booking.getId(), bookingRepository.searchAdminBookings(
                null, "%jane doe%", PageRequest.of(0, 50)).getFirst().id());
    }

    @Test
    @DisplayName("Должен возвращать ровно одну строку и статус ПОСЛЕДНЕГО платежа при нескольких попытках оплаты")
    void shouldReturnOnlyLatestPaymentStatusWhenMultiplePaymentsExist() {
        // ARRANGE
        Booking booking = createBooking(now, BookingStatus.CONFIRMED, "2000.00");
        createPayment(booking.getId(), "2000.00", PaymentStatus.FAILED);
        createPayment(booking.getId(), "2000.00", PaymentStatus.SUCCEEDED);
        entityManager.flush();

        // ACT
        List<AdminBookingResponse> result = bookingRepository.findAdminBookingsByDate(
                now.minusHours(1),
                now.plusHours(1)
        );

        // ASSERT
        assertEquals(1, result.size());
        assertEquals(PaymentStatus.SUCCEEDED, result.getFirst().paymentStatus());
    }

    @Test
    @DisplayName("Должен корректно считать сводку (getReportSummary) по createdAt")
    void shouldCalculateReportSummaryCorrectly() {
        // ARRANGE
        // Бронь 1: Подтверждена, 2 успешных платежа (1000 + 1500 = 2500)
        Booking booking1 = createBooking(now.plusDays(3), BookingStatus.CONFIRMED, "2500.00");
        createPayment(booking1.getId(), "1000.00", PaymentStatus.SUCCEEDED);
        createPayment(booking1.getId(), "1500.00", PaymentStatus.SUCCEEDED);

        // Бронь 2: Отменена, оплата была FAILED
        Booking booking2 = createBooking(now.plusDays(4), BookingStatus.CANCELLED, "3000.00");
        createPayment(booking2.getId(), "3000.00", PaymentStatus.FAILED);

        entityManager.flush();

        // ACT
        BookingReportSummaryResponse summary = bookingRepository.getReportSummary(
                startDate,
                endDate,
                PaymentStatus.SUCCEEDED,
                BookingStatus.CANCELLED
        );

        // ASSERT
        assertNotNull(summary);
        assertEquals(2L, summary.totalBookings());
        assertEquals(0, new BigDecimal("2500.00").compareTo(summary.totalPrepayment()));
        assertEquals(1L, summary.totalCancellations());
    }

    @Test
    @DisplayName("Должен возвращать детализированные строки (getReportItems) и суммировать платежи")
    void shouldReturnReportItemsWithAggregatedPayments() {
        // ARRANGE
        Booking booking = createBooking(now.plusDays(1), BookingStatus.CONFIRMED, "4000.00");
        createPayment(booking.getId(), "2000.00", PaymentStatus.SUCCEEDED);
        createPayment(booking.getId(), "2000.00", PaymentStatus.SUCCEEDED);
        entityManager.flush();

        // ACT
        List<BookingReportItemResponse> items = bookingRepository.getReportItems(
                startDate,
                endDate,
                PaymentStatus.SUCCEEDED
        );

        // ASSERT
        assertEquals(1, items.size());
        BookingReportItemResponse item = items.getFirst();

        assertEquals(booking.getId(), item.bookingId());
        assertEquals("T-1", item.tableNumber());
        assertEquals(BookingStatus.CONFIRMED, item.status());
        assertEquals(0, new BigDecimal("4000.00").compareTo(item.bookingAmount()));
        assertEquals(0, new BigDecimal("4000.00").compareTo(item.paidAmount())); // 2000 + 2000 = 4000
    }

    @Test
    @DisplayName("Должен фильтровать по createdAt и игнорировать старые брони")
    void shouldIgnoreBookingsOutsideCreatedAtRange() {
        // ARRANGE
        Booking oldBooking = createBooking(now, BookingStatus.CONFIRMED, "1000.00");
        entityManager.flush();

        entityManager.getEntityManager()
                .createQuery("UPDATE Booking b SET b.createdAt = :oldDate WHERE b.id = :id")
                .setParameter("oldDate", now.minusDays(20))
                .setParameter("id", oldBooking.getId())
                .executeUpdate();

        entityManager.clear();

        // ACT
        BookingReportSummaryResponse summary = bookingRepository.getReportSummary(
                startDate,
                endDate,
                PaymentStatus.SUCCEEDED,
                BookingStatus.CANCELLED
        );

        // ASSERT
        assertNotNull(summary);
        assertEquals(0L, summary.totalBookings());
    }


    private Booking createBooking(OffsetDateTime bookingAt, BookingStatus status, String amount) {
        return bookingRepository.save(
                Booking.builder()
                        .userId(defaultUser.getId())
                        .clubTableId(defaultTable.getId())
                        .guestsCount(4)
                        .bookingAt(bookingAt)
                        .status(status)
                        .amount(new BigDecimal(amount))
                        .paymentDeadline(OffsetDateTime.now().plusMinutes(15))
                        .build()
        );
    }

    private Payment createPayment(Long bookingId, String amount, PaymentStatus status) {
        Payment payment = Payment.builder()
                .bookingId(bookingId)
                .provider("BANK")
                .amount(new BigDecimal(amount))
                .status(status)
                .build();
        entityManager.persist(payment);
        return payment;
    }
}
