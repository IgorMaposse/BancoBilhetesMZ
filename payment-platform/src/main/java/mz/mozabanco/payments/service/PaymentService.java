package mz.mozabanco.payments.service;

import lombok.RequiredArgsConstructor;
import mz.mozabanco.payments.domain.Payment;
import mz.mozabanco.payments.domain.PaymentStatus;
import mz.mozabanco.payments.domain.RefundRecord;
import mz.mozabanco.payments.dto.DebitRequest;
import mz.mozabanco.payments.dto.PaymentResponse;
import mz.mozabanco.payments.dto.RefundRequest;
import mz.mozabanco.payments.exception.InvalidRefundException;
import mz.mozabanco.payments.exception.PaymentNotFoundException;
import mz.mozabanco.payments.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Nucleo da Plataforma de Pagamentos do Moza Banco (RF4).
 *
 * Premissa assumida: nao existe integracao real com um processador de cartoes/rede
 * de pagamentos (fora do ambito do exercicio). O debito e "processado" de forma
 * determinística e sincrona. Em producao, este servico chamaria o core bancario /
 * switch de pagamentos e passaria PENDING ate confirmacao assincrona.
 *
 * Idempotencia: o client (ticketing-platform) envia uma "idempotencyKey" (ex.: o
 * ID da reserva). Se o mesmo pedido chegar duas vezes (retry por timeout de rede),
 * devolvemos o pagamento ja existente em vez de debitar novamente.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResponse debit(DebitRequest request) {
        return paymentRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(PaymentResponse::from)
                .orElseGet(() -> processNewDebit(request));
    }

    private PaymentResponse processNewDebit(DebitRequest request) {
        Payment payment = Payment.builder()
                .idempotencyKey(request.idempotencyKey())
                .amount(request.amount())
                .currency(request.currency())
                .clientReference(request.clientReference())
                .description(request.description())
                .status(PaymentStatus.COMPLETED) // simulado: debito sempre bem-sucedido
                .build();

        Payment saved = paymentRepository.save(payment);
        return PaymentResponse.from(saved);
    }

    @Transactional
    public PaymentResponse refund(UUID paymentId, RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (payment.getStatus() != PaymentStatus.COMPLETED
                && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new InvalidRefundException(
                    "Nao e possivel reembolsar um pagamento com estado " + payment.getStatus());
        }

        BigDecimal alreadyRefunded = payment.totalRefunded();
        BigDecimal maxRefundable = payment.getAmount().subtract(alreadyRefunded);

        if (request.amount().compareTo(maxRefundable) > 0) {
            throw new InvalidRefundException(
                    "Valor de reembolso (%s) excede o valor disponivel (%s)"
                            .formatted(request.amount(), maxRefundable));
        }

        RefundRecord refund = RefundRecord.builder()
                .payment(payment)
                .amount(request.amount())
                .reason(request.reason())
                .build();
        payment.getRefunds().add(refund);

        BigDecimal newTotalRefunded = alreadyRefunded.add(request.amount());
        payment.setStatus(newTotalRefunded.compareTo(payment.getAmount()) == 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED);

        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getStatus(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
