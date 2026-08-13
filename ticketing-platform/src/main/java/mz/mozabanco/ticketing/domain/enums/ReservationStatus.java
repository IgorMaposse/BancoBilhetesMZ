package mz.mozabanco.ticketing.domain.enums;

public enum ReservationStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PAYMENT_FAILED,
    EXPIRED,
    CANCELLED_REFUNDED,
    CANCELLED_NO_REFUND
}
