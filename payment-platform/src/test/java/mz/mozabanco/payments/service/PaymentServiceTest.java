package mz.mozabanco.payments.service;

import mz.mozabanco.payments.domain.Payment;
import mz.mozabanco.payments.domain.PaymentStatus;
import mz.mozabanco.payments.dto.DebitRequest;
import mz.mozabanco.payments.dto.PaymentResponse;
import mz.mozabanco.payments.dto.RefundRequest;
import mz.mozabanco.payments.exception.InvalidRefundException;
import mz.mozabanco.payments.exception.PaymentNotFoundException;
import mz.mozabanco.payments.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        paymentService = new PaymentService(paymentRepository);
    }

    @Test
    void deveDebitarComSucessoQuandoNaoExisteIdempotencyKeyRepetida() {
        DebitRequest request = new DebitRequest("resv-123", new BigDecimal("500.00"), "MZN", "resv-123", "Bilhetes concerto");
        when(paymentRepository.findByIdempotencyKey("resv-123")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PaymentResponse response = paymentService.debit(request);

        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.amount()).isEqualByComparingTo("500.00");
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    void deveDevolverPagamentoExistenteQuandoIdempotencyKeyJaFoiUsada() {
        Payment existing = Payment.builder()
                .id(UUID.randomUUID())
                .idempotencyKey("resv-123")
                .amount(new BigDecimal("500.00"))
                .currency("MZN")
                .clientReference("resv-123")
                .status(PaymentStatus.COMPLETED)
                .build();

        DebitRequest request = new DebitRequest("resv-123", new BigDecimal("500.00"), "MZN", "resv-123", null);
        when(paymentRepository.findByIdempotencyKey("resv-123")).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.debit(request);

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void deveAplicarReembolsoParcialCorretamente() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .amount(new BigDecimal("1000.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        // Cancelamento com mais de 30 dias -> 80% de reembolso (regra RF5)
        PaymentResponse response = paymentService.refund(paymentId, new RefundRequest(new BigDecimal("800.00"), "Cancelamento > 30 dias"));

        assertThat(response.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(response.totalRefunded()).isEqualByComparingTo("800.00");
    }

    @Test
    void deveMarcarComoTotalmenteReembolsadoQuandoValorIgualaOriginal() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .amount(new BigDecimal("500.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.refund(paymentId, new RefundRequest(new BigDecimal("500.00"), "Cancelamento total"));

        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void deveRejeitarReembolsoAcimaDoValorDisponivel() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .amount(new BigDecimal("500.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refund(paymentId, new RefundRequest(new BigDecimal("600.00"), "invalido")))
                .isInstanceOf(InvalidRefundException.class);
    }

    @Test
    void deveLancarExcecaoQuandoPagamentoNaoExisteNaConsulta() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getStatus(paymentId))
                .isInstanceOf(PaymentNotFoundException.class);
    }
}
