package kg.megalab.pivnitsabackend.service;

import kg.megalab.pivnitsabackend.config.ManualPaymentProperties;
import kg.megalab.pivnitsabackend.dto.admin.AdminBookingResponse;
import kg.megalab.pivnitsabackend.dto.booking.ManualPaymentInstructionsResponse;
import kg.megalab.pivnitsabackend.entity.*;
import kg.megalab.pivnitsabackend.exception.BookingNotFoundException;
import kg.megalab.pivnitsabackend.exception.InvalidBookingStateException;
import kg.megalab.pivnitsabackend.exception.UserNotFoundException;
import kg.megalab.pivnitsabackend.exception.booking.BookingNotOwnedException;
import kg.megalab.pivnitsabackend.exception.staffexception.StaffNotFoundException;
import kg.megalab.pivnitsabackend.exception.tables.TableNotFoundException;
import kg.megalab.pivnitsabackend.repository.*;
import kg.megalab.pivnitsabackend.service.notifications.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ManualPaymentService {
    public static final String PROVIDER = "MANUAL_QR";

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ClubTableRepository clubTableRepository;
    private final StaffRepository staffRepository;
    private final NotificationService notificationService;
    private final ManualPaymentProperties properties;

    public OffsetDateTime newPaymentDeadline() {
        return OffsetDateTime.now().plus(properties.paymentWindow());
    }

    @Transactional
    public void createPendingPayment(Booking booking) {
        if (paymentRepository.existsByBookingIdAndProvider(booking.getId(), PROVIDER)) {
            return;
        }

        paymentRepository.save(Payment.builder()
                .bookingId(booking.getId())
                .provider(PROVIDER)
                .amount(booking.getAmount())
                .status(PaymentStatus.PENDING)
                .build());
    }

    @Transactional(readOnly = true)
    public ManualPaymentInstructionsResponse getInstructionsForGuest(String phone, Long bookingId) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));

        Booking booking = bookingRepository.findByIdAndUserId(bookingId, user.getId())
                .orElseThrow(() -> new BookingNotOwnedException("Бронь не найдена или вам не принадлежит"));

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidBookingStateException("Инструкции оплаты доступны только для брони, ожидающей оплату");
        }

        ClubTable table = clubTableRepository.findById(booking.getClubTableId())
                .orElseThrow(() -> new TableNotFoundException("Столик не найден"));

        return buildInstructions(booking, user, table);
    }

    public ManualPaymentInstructionsResponse buildInstructions(Booking booking, User user, ClubTable table) {
        String message = String.format(
                Locale.ROOT,
                "Здравствуйте! Оплата брони #%d%nСтол: %s%nФИО: %s %s%nТелефон: %s%nСумма: %s KGS%nПрикрепляю чек оплаты.",
                booking.getId(),
                table.getTableNumber(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                booking.getAmount().toPlainString()
        );

        return new ManualPaymentInstructionsResponse(
                booking.getId(),
                table.getTableNumber(),
                booking.getAmount(),
                booking.getPaymentDeadline(),
                OffsetDateTime.now().isAfter(booking.getPaymentDeadline()),
                properties.qrImageUrl(),
                buildWhatsappUrl(message)
        );
    }

    @Transactional(readOnly = true)
    public List<AdminBookingResponse> getPendingPaymentBookings() {
        return bookingRepository.findPendingPaymentBookings();
    }

    @Transactional
    public void confirmPayment(String staffEmail, Long bookingId) {
        Booking booking = getBookingForReview(bookingId);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return;
        }
        requirePendingPayment(booking);

        Payment payment = getManualPayment(booking);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(OffsetDateTime.now());
        paymentRepository.save(payment);

        Staff reviewer = getActiveStaff(staffEmail);
        booking.setStatus(BookingStatus.CONFIRMED);
        recordReview(booking, reviewer);
        bookingRepository.save(booking);

        notifyGuest(
                booking,
                NotificationType.BOOKING_CONFIRMED,
                "Бронь подтверждена",
                "Администратор подтвердил оплату брони #" + booking.getId(),
                "booking-confirmed-" + booking.getId()
        );
    }

    @Transactional
    public void rejectPayment(String staffEmail, Long bookingId, String reason) {
        Booking booking = getBookingForReview(bookingId);
        if (booking.getStatus() == BookingStatus.EXPIRED) {
            return;
        }
        requirePendingPayment(booking);

        Payment payment = getManualPayment(booking);
        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        Staff reviewer = getActiveStaff(staffEmail);
        booking.setStatus(BookingStatus.EXPIRED);
        booking.setCancellationReason(reason);
        recordReview(booking, reviewer);
        bookingRepository.save(booking);

        notifyGuest(
                booking,
                NotificationType.PAYMENT_REJECTED,
                "Оплата не подтверждена",
                "Администратор отклонил оплату брони #" + booking.getId() + ". Причина: " + reason,
                "payment-rejected-" + booking.getId()
        );
    }

    @Transactional
    public void failPendingPayment(Long bookingId) {
        paymentRepository.findTopByBookingIdAndProviderOrderByIdDesc(bookingId, PROVIDER)
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .ifPresent(payment -> {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                });
    }

    private String buildWhatsappUrl(String message) {
        String number = properties.whatsappNumber().replaceAll("\\D", "");
        if (number.isBlank()) {
            return "";
        }

        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "https://wa.me/" + number + "?text=" + encodedMessage;
    }

    private Booking getBookingForReview(Long bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Бронь не найдена"));
    }

    private Payment getManualPayment(Booking booking) {
        return paymentRepository.findTopByBookingIdAndProviderOrderByIdDesc(booking.getId(), PROVIDER)
                .orElseThrow(() -> new InvalidBookingStateException("Для брони не найден ручной QR-платеж"));
    }

    private Staff getActiveStaff(String email) {
        return staffRepository.findByEmail(email)
                .filter(Staff::isActive)
                .orElseThrow(() -> new StaffNotFoundException("Сотрудник не найден"));
    }

    private void requirePendingPayment(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidBookingStateException("Решение по оплате уже принято или бронь закрыта");
        }
    }

    private void recordReview(Booking booking, Staff reviewer) {
        booking.setPaymentReviewedAt(OffsetDateTime.now());
        booking.setPaymentReviewedByStaffId(reviewer.getId());
    }

    private void notifyGuest(
            Booking booking,
            NotificationType type,
            String title,
            String body,
            String deduplicationKey
    ) {
        notificationService.createForUsers(
                List.of(booking.getUserId()),
                type,
                title,
                body,
                NotificationTargetType.BOOKING,
                booking.getId(),
                deduplicationKey
        );
    }
}
