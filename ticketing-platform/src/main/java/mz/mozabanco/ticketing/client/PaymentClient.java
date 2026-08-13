package mz.mozabanco.ticketing.client;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentClient {
    PaymentClientResponse debit(String idempotencyKey, BigDecimal amount, String clientReference, String description);
    PaymentClientResponse refund(UUID paymentId, BigDecimal amount, String reason);
    PaymentClientResponse getStatus(UUID paymentId);

    record PaymentClientResponse(UUID paymentId, String status, BigDecimal amount, BigDecimal totalRefunded) {}
}
