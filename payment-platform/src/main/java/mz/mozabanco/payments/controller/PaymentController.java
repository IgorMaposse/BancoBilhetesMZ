package mz.mozabanco.payments.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.mozabanco.payments.dto.DebitRequest;
import mz.mozabanco.payments.dto.PaymentResponse;
import mz.mozabanco.payments.dto.RefundRequest;
import mz.mozabanco.payments.service.PaymentService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * API propria da Plataforma de Pagamentos do Moza Banco (RF4):
 * debito, reembolso e consulta do estado da transacao.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

	
    private final PaymentService paymentService;

    @PostMapping("/debit")
    public ResponseEntity<PaymentResponse> debit(@Valid @RequestBody DebitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.debit(request));
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentResponse> refund(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(paymentService.refund(paymentId, request));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getStatus(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.getStatus(paymentId));
    }
}
