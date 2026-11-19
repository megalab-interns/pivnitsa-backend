package kg.megalab.pivnitsabackend.dto.table;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateTableRequest(
        @NotBlank(message = "Номер столика должен быть указан")
        String tableNumber,

        @NotNull(message = "Вместимость должна быть заполнена")
        @Positive(message = "Вместимость должна быть больше 0")
        Integer capacity,

        @NotNull(message = "Должен быть указан зал")
        @Positive(message = "Некорректный id зала")
        Long hallId,

        @DecimalMin(value = "0.0", message = "Позиция X должна быть от 0 до 100")
        // необязательное поле, null проходит
        @DecimalMax(value = "100.0", message = "Позиция X должна быть от 0 до 100") // проценты от размера карты зала
        BigDecimal positionX,

        @DecimalMin(value = "0.0", message = "Позиция Y должна быть от 0 до 100")
        @DecimalMax(value = "100.0", message = "Позиция Y должна быть от 0 до 100")
        BigDecimal positionY,

        @Size(max = 50, message = "Категория не должна быть длиннее 50 символов")   // в БД колонка VARCHAR(50)
        String category,

        @Positive(message = "Сумма депозита должна быть больше 0")                  // без депозита стол нельзя забронировать
        BigDecimal depositAmount
) {
}
