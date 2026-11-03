package kg.megalab.pivnitsabackend.service;

import kg.megalab.pivnitsabackend.config.ManualPaymentProperties;
import kg.megalab.pivnitsabackend.dto.booking.ManualPaymentInstructionsResponse;
import kg.megalab.pivnitsabackend.entity.*;
import kg.megalab.pivnitsabackend.repository.*;
import org.springframework.data.domain.Pageable;
import kg.megalab.pivnitsabackend.service.notifications.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManualPaymentServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClubTableRepository clubTableRepository;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private NotificationService notificationService;

    private ManualPaymentService service;

    @BeforeEach
    void setUp() {
        service = new ManualPaymentService(
                bookingRepository,
                paymentRepository,
                userRepository,
                clubTableRepository,
                staffRepository,
                notificationService,
                new ManualPaymentProperties(
                        Duration.ofMinutes(15),
                        "https://cdn.example.test/payment-qr.png",
                        "+996 555 123 456"
                )
        );
    }

    @Test
    void shouldCreateSinglePendingManualPayment() {
        Booking booking = booking(BookingStatus.PENDING_PAYMENT);
        when(paymentRepository.existsByBookingIdAndProvider(booking.getId(), ManualPaymentService.PROVIDER))
                .thenReturn(false);

        service.createPendingPayment(booking);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment payment = captor.getValue();
        assertThat(payment.getBookingId()).isEqualTo(booking.getId());
        assertThat(payment.getProvider()).isEqualTo(ManualPaymentService.PROVIDER);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo(booking.getAmount());
    }

    @Test
    void shouldBuildQrAndPrefilledWhatsappInstructions() {
        Booking booking = booking(BookingStatus.PENDING_PAYMENT);
        User user = User.builder()
                .id(7L)
                .firstName("Асан")
                .lastName("Ибраев")
                .phone("+996700000007")
                .build();
        ClubTable table = ClubTable.builder().id(3L).tableNumber("VIP-3").build();

        ManualPaymentInstructionsResponse response = service.buildInstructions(booking, user, table);
        String decodedWhatsappUrl = URLDecoder.decode(response.whatsappUrl(), StandardCharsets.UTF_8);

        assertThat(response.bookingId()).isEqualTo(booking.getId());
        assertThat(response.paymentWindowElapsed()).isTrue();
        assertThat(response.qrImageUrl()).isEqualTo("https://cdn.example.test/payment-qr.png");
        assertThat(decodedWhatsappUrl).contains("wa.me/996555123456");
        assertThat(decodedWhatsappUrl).contains("Оплата брони #42");
        assertThat(decodedWhatsappUrl).contains("Стол: VIP-3");
        assertThat(decodedWhatsappUrl).contains("Асан Ибраев");
        assertThat(decodedWhatsappUrl).contains("+996700000007");
    }

    @Test
    void shouldAllowAdminToConfirmAfterPaymentWindowElapsed() {
        Booking booking = booking(BookingStatus.PENDING_PAYMENT);
        Payment payment = pendingPayment(booking);
        Staff staff = Staff.builder().id(5L).email("admin@example.test").active(true).build();

        when(bookingRepository.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(paymentRepository.findTopByBookingIdAndProviderOrderByIdDesc(booking.getId(), ManualPaymentService.PROVIDER))
                .thenReturn(Optional.of(payment));
        when(staffRepository.findByEmail(staff.getEmail())).thenReturn(Optional.of(staff));

        service.confirmPayment(staff.getEmail(), booking.getId());

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getPaymentReviewedByStaffId()).isEqualTo(staff.getId());
        assertThat(booking.getPaymentReviewedAt()).isNotNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getPaidAt()).isNotNull();
        verify(notificationService).createForUsers(
                any(),
                eq(NotificationType.BOOKING_CONFIRMED),
                any(),
                any(),
                eq(NotificationTargetType.BOOKING),
                eq(booking.getId()),
                eq("booking-confirmed-42")
        );
    }

    @Test
    void shouldRejectPaymentAndReleaseBooking() {
        Booking booking = booking(BookingStatus.PENDING_PAYMENT);
        Payment payment = pendingPayment(booking);
        Staff staff = Staff.builder().id(5L).email("admin@example.test").active(true).build();

        when(bookingRepository.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(paymentRepository.findTopByBookingIdAndProviderOrderByIdDesc(booking.getId(), ManualPaymentService.PROVIDER))
                .thenReturn(Optional.of(payment));
        when(staffRepository.findByEmail(staff.getEmail())).thenReturn(Optional.of(staff));

        service.rejectPayment(staff.getEmail(), booking.getId(), "Чек не найден");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(booking.getCancellationReason()).isEqualTo("Чек не найден");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(bookingRepository).save(booking);
    }

    @Test
    void shouldNotCreateAnotherPaymentOnRepeatedConfirmation() {
        Booking booking = booking(BookingStatus.CONFIRMED);
        when(bookingRepository.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));

        service.confirmPayment("admin@example.test", booking.getId());

        verifyNoInteractions(paymentRepository, staffRepository, notificationService);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void shouldSearchByBookingIdAndNormalizedName() {
        service.searchAdminBookings("  Асан   Ибраев  ");
        verify(bookingRepository).searchAdminBookings(eq(null), eq("%асан ибраев%"), any(Pageable.class));

        service.searchAdminBookings("42");
        verify(bookingRepository).searchAdminBookings(eq(42L), eq("%42%"), any(Pageable.class));
    }

    private Booking booking(BookingStatus status) {
        return Booking.builder()
                .id(42L)
                .userId(7L)
                .clubTableId(3L)
                .bookingAt(OffsetDateTime.now().plusDays(1))
                .status(status)
                .amount(new BigDecimal("1500.00"))
                .paymentDeadline(OffsetDateTime.now().minusMinutes(1))
                .build();
    }

    private Payment pendingPayment(Booking booking) {
        return Payment.builder()
                .id(8L)
                .bookingId(booking.getId())
                .provider(ManualPaymentService.PROVIDER)
                .amount(booking.getAmount())
                .status(PaymentStatus.PENDING)
                .build();
    }
}
