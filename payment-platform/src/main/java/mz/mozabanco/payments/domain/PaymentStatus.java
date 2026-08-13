package mz.mozabanco.payments.domain;

public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED
}
