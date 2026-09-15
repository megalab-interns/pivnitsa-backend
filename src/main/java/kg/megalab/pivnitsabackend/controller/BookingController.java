package kg.megalab.pivnitsabackend.controller;

import jakarta.validation.Valid;
import kg.megalab.pivnitsabackend.dto.booking.BookingResponse;
import kg.megalab.pivnitsabackend.dto.booking.CreateBookingRequest;
import kg.megalab.pivnitsabackend.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService bookingService;

    @PostMapping()
    public ResponseEntity<BookingResponse> createBooking(@AuthenticationPrincipal String phone, @Valid @RequestBody CreateBookingRequest request) {
        BookingResponse response = bookingService.createBooking(phone, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(@AuthenticationPrincipal String phone, @PathVariable Long id) {
        BookingResponse response = bookingService.cancelBookingByGuest(phone, id);
        return ResponseEntity.ok(response);
    }


}
