package kg.megalab.pivnitsabackend.service;

import kg.megalab.pivnitsabackend.entity.BookingStatus;
import kg.megalab.pivnitsabackend.entity.User;
import kg.megalab.pivnitsabackend.entity.Booking;
import kg.megalab.pivnitsabackend.repository.BookingRepository;
import kg.megalab.pivnitsabackend.repository.ClubTableRepository;
import kg.megalab.pivnitsabackend.repository.UserRepository;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void shouldThrowWhenGuestCancelsAfterBookingTimePassed() {
        String phone = "+996123456789";

        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);

        Booking booking = Booking.builder()
                .userId(1L)
                .status(BookingStatus.CONFIRMED)
                .bookingAt(OffsetDateTime.now().minusMinutes(10))
                .build();
    }
}