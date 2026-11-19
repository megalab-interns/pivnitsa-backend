package kg.megalab.pivnitsabackend.dto.table;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldRejectPositionAbove100OnCreate() {
        CreateTableRequest request = new CreateTableRequest(
                "12", 4, 1L, new BigDecimal("150"), new BigDecimal("50"), null, null);

        assertEquals(1, validator.validate(request).size());   // нарушено только положение X
    }

    @Test
    void shouldAcceptBorderPositionsOnCreate() {
        CreateTableRequest request = new CreateTableRequest(
                "13", 4, 1L, BigDecimal.ZERO, new BigDecimal("100"), null, null);

        assertTrue(validator.validate(request).isEmpty());     // 0 и 100 допустимы
    }

    @Test
    void shouldRejectNegativePositionOnUpdate() {
        UpdateTableRequest request = new UpdateTableRequest(
                null, null, null, null, null, new BigDecimal("-1"), null, null);

        assertEquals(1, validator.validate(request).size());
    }
}
