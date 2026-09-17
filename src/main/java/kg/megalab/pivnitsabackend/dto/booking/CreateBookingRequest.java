package kg.megalab.pivnitsabackend.dto.booking;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CreateBookingRequest(
        @NotNull(message = "Не передан id столика")
        Long clubTableId,

        @NotNull(message = "Дата должна быть заполнена")
        @FutureOrPresent(message = "Дата брони должна быть не раньше сегодняшней даты")
        OffsetDateTime bookingAt,

        @NotNull(message = "Не передано количество гостей")
        @Min(1)
        Integer guestsCount,

        @Size(max = 300, message = "Комментарий не должен превышать 300 символов")
        String comment
) {
}
