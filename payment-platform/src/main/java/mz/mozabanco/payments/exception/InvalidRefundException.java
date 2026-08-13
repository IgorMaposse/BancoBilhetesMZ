package mz.mozabanco.payments.exception;

public class InvalidRefundException extends RuntimeException {
    public InvalidRefundException(String message) {
        super(message);
    }
}
