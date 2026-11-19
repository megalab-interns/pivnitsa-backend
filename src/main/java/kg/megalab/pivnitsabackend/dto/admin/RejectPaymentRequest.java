package kg.megalab.pivnitsabackend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectPaymentRequest(
        @NotBlank(message = "Причина отклонения обязательна")
        @Size(max = 500, message = "Причина отклонения не должна превышать 500 символов")
        String reason
) {
}
