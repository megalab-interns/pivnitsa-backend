package kg.megalab.pivnitsabackend.service;

import kg.megalab.pivnitsabackend.dto.booking.BookingResponse;
import kg.megalab.pivnitsabackend.entity.BookingStatus;
import kg.megalab.pivnitsabackend.entity.ClubTable;
import kg.megalab.pivnitsabackend.entity.User;
import kg.megalab.pivnitsabackend.entity.Booking;
import kg.megalab.pivnitsabackend.exception.BookingNotFoundException;
import kg.megalab.pivnitsabackend.exception.InvalidBookingStateException;
import kg.megalab.pivnitsabackend.exception.UserNotFoundException;
import kg.megalab.pivnitsabackend.exception.booking.BookingNotOwnedException;
import kg.megalab.pivnitsabackend.exception.booking.BookingTimePassedException;
import kg.megalab.pivnitsabackend.exception.tables.TableNotFoundException;
import kg.megalab.pivnitsabackend.repository.BookingRepository;
import kg.megalab.pivnitsabackend.repository.ClubTableRepository;
import kg.megalab.pivnitsabackend.repository.UserRepository;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ClubTableRepository clubTableRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookingDateValidator bookingDateValidator;

    @Mock
    private TableUnavailabilityChecker unavailabilityChecker;

    @Mock
    private RefundPolicyCalculator refundPolicyCalculator;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void shouldThrowWhenUserNotFound() {
        String phone = "+996700123456";
        when(userRepository.findByPhone(phone)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> bookingService.cancelBookingByGuest(phone, 101L));
    }

    @Test
    void shouldThrowWhenBookingNotFound() {
        String phone = "+996700121212";
        User user = mock(User.class);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));
        when(bookingRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(BookingNotFoundException.class, () -> bookingService.cancelBookingByGuest(phone, 1L));

    }

    @Test
    void shouldThrowWhenBookingBelongsToAnotherUser() {
        String phone = "+996700111111";
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);

        Booking booking = Booking.builder().userId(2L).build();
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        assertThrows(BookingNotOwnedException.class, () -> bookingService.cancelBookingByGuest(phone, 1L));
    }

    @Test
    void shouldThrowWhenBookingAlreadyCancelled() {
        String phone = "+996700121212";
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));
        Booking booking = Booking.builder().userId(1L).status(BookingStatus.CANCELLED).build();
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
        assertThrows(InvalidBookingStateException.class, () -> bookingService.cancelBookingByGuest(phone, 100L));
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
    }

    @Test
    void shouldThrowWhenBookingAlreadyCompleted() {
        String phone = "+996700100100";
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));
        Booking booking = Booking.builder().status(BookingStatus.COMPLETED).userId(1L).build();
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
        assertThrows(InvalidBookingStateException.class, () -> bookingService.cancelBookingByGuest(phone, 100L));
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
    }

    @Test
    void shouldThrowWhenBookingAlreadyExpired() {
        String phone = "+996700456456";
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));
        Booking booking = Booking.builder().userId(1L).status(BookingStatus.EXPIRED).build();
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
        assertThrows(InvalidBookingStateException.class, () -> bookingService.cancelBookingByGuest(phone, 100L));
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
    }

    @Test
    void shouldThrowWhenGuestCancelsAfterBookingTimePassed() {
        String phone = "+996123456789";
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));
        Booking booking = Booking.builder()
                .userId(1L)
                .status(BookingStatus.CONFIRMED)
                .bookingAt(OffsetDateTime.now().minusMinutes(10))
                .build();
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
        assertThrows(BookingTimePassedException.class, () -> bookingService.cancelBookingByGuest(phone, 100L));
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
    }

    @Test
    void shouldThrowWhenTableNotFound() {
        String phone = "+996700808080";
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));
        Booking booking = Booking.builder()
                .userId(1L)
                .status(BookingStatus.CONFIRMED)
                .clubTableId(5L)
                .bookingAt(OffsetDateTime.now().plusDays(2))
                .build();
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
        assertThrows(TableNotFoundException.class, () -> bookingService.cancelBookingByGuest(phone, 100L));
    }

    @Test
    void shouldCancelBookingAndRefundPercentageIs100() {
        String phone = "+996700555555";
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));

        ClubTable clubTable = mock(ClubTable.class);
        when(clubTableRepository.findById(5L)).thenReturn(Optional.of(clubTable));

        Booking booking = Booking.builder()
                .userId(1L)
                .status(BookingStatus.CONFIRMED)
                .clubTableId(5L)
                .bookingAt(OffsetDateTime.now().plusDays(2))
                .build();
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));

        when(refundPolicyCalculator.calculateRefundPercentage(any(), any())).thenReturn(100);
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.cancelBookingByGuest(phone, 100L);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(100, booking.getRefundPercentage());
        verify(bookingRepository).save(booking);
    }
}
