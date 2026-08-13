package mz.mozabanco.payments.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DebitRequest(
        @NotBlank String idempotencyKey,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String clientReference,
        String description
) {}
