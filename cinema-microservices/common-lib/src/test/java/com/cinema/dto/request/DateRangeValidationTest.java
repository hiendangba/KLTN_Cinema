package com.cinema.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DateRangeValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldAllowOnlyFrom() {
        DateRange range = DateRange.builder()
                .from(LocalDateTime.of(2026, 5, 29, 9, 0))
                .build();

        assertTrue(validator.validate(range).isEmpty());
    }

    @Test
    void shouldAllowOnlyTo() {
        DateRange range = DateRange.builder()
                .to(LocalDateTime.of(2026, 5, 29, 18, 0))
                .build();

        assertTrue(validator.validate(range).isEmpty());
    }
}
