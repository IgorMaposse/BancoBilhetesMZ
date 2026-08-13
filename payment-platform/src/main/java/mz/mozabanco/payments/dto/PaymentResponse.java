package mz.mozabanco.payments.dto;

import mz.mozabanco.payments.domain.Payment;
import mz.mozabanco.payments.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String idempotencyKey,
        BigDecimal amount,
        BigDecimal totalRefunded,
        String currency,
        String clientReference,
        PaymentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getIdempotencyKey(), p.getAmount(), p.totalRefunded(),
                p.getCurrency(), p.getClientReference(), p.getStatus(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
