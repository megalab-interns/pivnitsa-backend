package kg.megalab.pivnitsabackend.dto.table;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateTableRequest(
        String tableNumber,

        @Positive(message = "Вместимость должна быть больше 0")
        Integer capacity,

        @Positive(message = "Некорректный id зала")
        Long hallId,

        Boolean active,

        @Size(max = 50, message = "Категория не должна быть длиннее 50 символов")   // защита от ошибки БД (500)
        String category,

        @DecimalMin(value = "0.0", message = "Позиция X должна быть от 0 до 100")   // раньше проверки не было
        @DecimalMax(value = "100.0", message = "Позиция X должна быть от 0 до 100")
        BigDecimal positionX,

        @DecimalMin(value = "0.0", message = "Позиция Y должна быть от 0 до 100")
        @DecimalMax(value = "100.0", message = "Позиция Y должна быть от 0 до 100")
        BigDecimal positionY,

        @Positive(message = "Сумма депозита должна быть больше 0")                  // опечатка «должны» исправлена на «должна»
        BigDecimal depositAmount
) {
}
