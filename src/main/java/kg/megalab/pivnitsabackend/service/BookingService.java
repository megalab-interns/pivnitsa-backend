package kg.megalab.pivnitsabackend.service;

import kg.megalab.pivnitsabackend.dto.admin.AdminBookingResponse;
import kg.megalab.pivnitsabackend.dto.booking.BookingResponse;
import kg.megalab.pivnitsabackend.entity.Booking;
import kg.megalab.pivnitsabackend.entity.BookingStatus;
import kg.megalab.pivnitsabackend.entity.ClubTable;
import kg.megalab.pivnitsabackend.entity.User;
import kg.megalab.pivnitsabackend.exception.BookingNotFoundException;
import kg.megalab.pivnitsabackend.exception.InvalidBookingStateException;
import kg.megalab.pivnitsabackend.exception.UserNotFoundException;
import kg.megalab.pivnitsabackend.repository.UserRepository;
import kg.megalab.pivnitsabackend.exception.tables.TableNotFoundException;
import kg.megalab.pivnitsabackend.exception.booking.*;
import kg.megalab.pivnitsabackend.repository.BookingRepository;
import kg.megalab.pivnitsabackend.repository.ClubTableRepository;
import kg.megalab.pivnitsabackend.dto.booking.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookingRepository;
    private final ClubTableRepository clubTableRepository;
    private final UserRepository userRepository;
    private final BookingDateValidator bookingDateValidator;
    private final TableUnavailabilityChecker unavailabilityChecker;
    private final RefundPolicyCalculator refundPolicyCalculator;

    @Transactional
    public BookingResponse createBooking(String phone, CreateBookingRequest request) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));

        bookingDateValidator.validateBookingDate(request.bookingAt());

        ClubTable bookingTable = clubTableRepository.findById(request.clubTableId())
                .orElseThrow(() -> new TableNotFoundException("Столик не найден"));

        OffsetDateTime bishkekBookingAt = request.bookingAt().withOffsetSameInstant(ZoneOffset.of("+06:00"));
        OffsetDateTime[] range = unavailabilityChecker.toDayRange(bishkekBookingAt.toLocalDate());

        if (unavailabilityChecker.isUnavailable(bookingTable, range[0], range[1])) {
            throw new TableUnavailableException("Столик недоступен на выбранную дату");
        }

        if (bookingTable.getDepositAmount() == null) {
            throw new DepositNotConfiguredException("Нету данных о депозите");
        }

        if (bookingTable.getCapacity() < request.guestsCount()) {
            throw new GuestsExceedCapacityException("Превышено допустимое число гостей");
        }

        if (bookingRepository.existsActiveBookingForTableOnDate(bookingTable.getId(), range[0], range[1])) {
            throw new TableNotAvailableException("Столик уже забронирован на эту дату");
        }

        try {
            Booking booking = Booking.builder()
                    .userId(user.getId())
                    .clubTableId(bookingTable.getId())
                    .bookingAt(request.bookingAt())
                    .status(BookingStatus.PENDING_PAYMENT)
                    .amount(bookingTable.getDepositAmount())
                    .guestsCount(request.guestsCount())
                    .build();

            booking = bookingRepository.save(booking);

            return toResponse(booking, bookingTable);

        } catch (DataIntegrityViolationException e) {
            throw new TableNotAvailableException("Столик уже забронирован");
        }
    }

    @Transactional
    public BookingResponse cancelBookingByGuest(String phone, Long bookingId) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Бронь не найдена"));

        if (!booking.getUserId().equals(user.getId())) {
            throw new BookingNotOwnedException("Эта бронь вам не принадлежит");
        }

        switch (booking.getStatus()) {
            case CANCELLED -> throw new InvalidBookingStateException("Бронь уже отменена");
            case COMPLETED -> throw new InvalidBookingStateException("Нельзя отменить завершенную бронь");
            case EXPIRED -> throw new InvalidBookingStateException("Нельзя отменить истекшую бронь");
            case PENDING_PAYMENT, CONFIRMED -> {
                int refundPercentage = refundPolicyCalculator.calculateRefundPercentage(booking.getBookingAt(), OffsetDateTime.now());
                booking.setStatus(BookingStatus.CANCELLED);
                booking.setRefundPercentage(refundPercentage);
            }
        }

        ClubTable bookingTable = clubTableRepository.findById(booking.getClubTableId())
                .orElseThrow(() -> new TableNotFoundException("Столик не найден"));

        booking = bookingRepository.save(booking);

        return toResponse(booking, bookingTable);
    }

    @Transactional(readOnly = true)
    public List<AdminBookingResponse> getAdminBookings(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime end = (to != null) ? to : from.plusDays(1);

        return bookingRepository.findAdminBookingsByDate(from, end);
    }

    @Transactional
    public void cancelBookingByAdmin(Long bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Бронь не найдена"));

        switch (booking.getStatus()) {
            case CANCELLED -> throw new InvalidBookingStateException("Бронь уже отменена");
            case COMPLETED -> throw new InvalidBookingStateException("Нельзя отменить завершенную бронь");
            case EXPIRED -> throw new InvalidBookingStateException("Нельзя отменить истекшую бронь");
            case PENDING_PAYMENT, CONFIRMED -> {
                booking.setStatus(BookingStatus.CANCELLED);
                booking.setCancellationReason(reason);
                bookingRepository.save(booking);
            }
        }
    }

    private BookingResponse toResponse(Booking booking, ClubTable bookingTable) {
        return new BookingResponse(
                booking.getId(),
                booking.getClubTableId(),
                bookingTable.getTableNumber(),
                booking.getEventId(),
                booking.getBookingAt(),
                booking.getCreatedAt(),
                booking.getStatus(),
                booking.getAmount(),
                booking.getGuestsCount(),
                booking.getRefundPercentage()
        );
    }
}