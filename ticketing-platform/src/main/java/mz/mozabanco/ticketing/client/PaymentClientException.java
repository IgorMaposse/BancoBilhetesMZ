package mz.mozabanco.ticketing.client;

public class PaymentClientException extends RuntimeException {
    public PaymentClientException(String message, Throwable cause) {
        super(message, cause);
    }
    public PaymentClientException(String message) {
        super(message);
    }
}
