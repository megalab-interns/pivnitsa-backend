package kg.megalab.pivnitsabackend.security;

import kg.megalab.pivnitsabackend.dto.admin.CancelBookingRequest;
import kg.megalab.pivnitsabackend.entity.Booking;
import kg.megalab.pivnitsabackend.entity.BookingStatus;
import kg.megalab.pivnitsabackend.exception.BookingNotFoundException;
import kg.megalab.pivnitsabackend.exception.InvalidBookingStateException;
import kg.megalab.pivnitsabackend.repository.BookingRepository;
import kg.megalab.pivnitsabackend.service.BookingService;
import kg.megalab.pivnitsabackend.service.ManualPaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ManualPaymentService manualPaymentService;

    @InjectMocks
    private BookingService bookingService;

    @Test
    @DisplayName("Если 'to' не передан, сервис должен подставить 'from + 1 день'")
    void shouldDefaultToPlusOneDayWhenToIsNull() {
        OffsetDateTime from = OffsetDateTime.parse("2026-08-25T00:00:00+06:00");

        when(bookingRepository.findAdminBookingsByDate(eq(from), eq(from.plusDays(1))))
                .thenReturn(List.of());

        bookingService.getAdminBookings(from, null);

        verify(bookingRepository, times(1)).findAdminBookingsByDate(from, from.plusDays(1));
    }

    @Test
    @DisplayName("Должен успешно отменить бронь со статусом CONFIRMED")
    void shouldCancelBookingSuccessfully() {
        Long bookingId = 10L;
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);

        CancelBookingRequest request = new CancelBookingRequest("Клиент попросил отменить");

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bookingService.cancelBookingByAdmin(bookingId, request.reason());

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationReason()).isEqualTo("Клиент попросил отменить");

        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("Должен выбросить ошибку при попытке отменить уже отмененную бронь")
    void shouldThrowExceptionWhenCancellingAlreadyCancelledBooking() {
        Long bookingId = 10L;
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setStatus(BookingStatus.CANCELLED);

        CancelBookingRequest request = new CancelBookingRequest("Повторная отмена");

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBookingByAdmin(bookingId, request.reason()))
                .isInstanceOf(InvalidBookingStateException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Должен выбросить BookingNotFoundException, если бронь не найдена")
    void shouldThrowNotFoundWhenBookingDoesNotExist() {
        Long bookingId = 99L;
        CancelBookingRequest request = new CancelBookingRequest("Причина");

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBookingByAdmin(bookingId, request.reason()))
                .isInstanceOf(BookingNotFoundException.class);
    }
}
