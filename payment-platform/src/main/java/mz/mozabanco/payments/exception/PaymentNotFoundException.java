package mz.mozabanco.payments.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID id) {
        super("Pagamento nao encontrado: " + id);
    }
}
