package kg.megalab.pivnitsabackend.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import kg.megalab.pivnitsabackend.config.OpenApiConfig;
import kg.megalab.pivnitsabackend.dto.booking.BookingResponse;
import kg.megalab.pivnitsabackend.dto.booking.ManualPaymentInstructionsResponse;
import kg.megalab.pivnitsabackend.dto.booking.CreateBookingRequest;
import kg.megalab.pivnitsabackend.service.BookingService;
import kg.megalab.pivnitsabackend.service.ManualPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bookings")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class BookingController {
    private final BookingService bookingService;
    private final ManualPaymentService manualPaymentService;

    @GetMapping("/active")
    public ResponseEntity<List<BookingResponse>> getActiveBookings(@AuthenticationPrincipal String phone) {
        return ResponseEntity.ok(bookingService.getActiveBookings(phone));
    }

    @GetMapping("/history")
    public ResponseEntity<List<BookingResponse>> getBookingHistory(@AuthenticationPrincipal String phone) {
        return ResponseEntity.ok(bookingService.getBookingHistory(phone));
    }

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

    @GetMapping("{id}/payment-instructions")
    public ResponseEntity<ManualPaymentInstructionsResponse> getPaymentInstructions(
            @AuthenticationPrincipal String phone,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(manualPaymentService.getInstructionsForGuest(phone, id));
    }


}
