package mz.mozabanco.payments.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Representa uma transacao de debito processada pela Plataforma de Pagamentos.
 * A "idempotencyKey" e fornecida pelo sistema cliente (ex.: ticketing-platform)
 * para garantir que um pedido de debito repetido (ex.: por timeout/retry) nao
 * resulta em cobranca duplicada.
 */
@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotencyKey")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 100)
    private String clientReference; // referencia do sistema chamador (ex: reservationId)

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RefundRecord> refunds = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public BigDecimal totalRefunded() {
        return refunds.stream()
                .map(RefundRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
