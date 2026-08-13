package mz.mozabanco.ticketing.domain;

import jakarta.persistence.*;
import lombok.*;
import mz.mozabanco.ticketing.domain.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RF3 - ciclo de vida completo de uma reserva:
 *
 *   PENDING_PAYMENT --(alteracao de itens, ainda nao paga)--> PENDING_PAYMENT (novo total)
 *   PENDING_PAYMENT --(pagamento ok)--> CONFIRMED
 *   PENDING_PAYMENT --(pagamento falha)--> PAYMENT_FAILED
 *   PENDING_PAYMENT --(expira sem pagamento, 15 min)--> EXPIRED
 *   PENDING_PAYMENT --(cancelamento pelo cliente, nada cobrado ainda)--> CANCELLED_NO_REFUND
 *   CONFIRMED --(cancelamento > 30 dias do evento)--> CANCELLED_REFUNDED (80%)
 *   CONFIRMED --(cancelamento <= 30 dias do evento)--> CANCELLED_REFUNDED (50%)
 *   CONFIRMED|PENDING_PAYMENT --(organizador cancela o evento)--> CANCELLED_REFUNDED (100%) | CANCELLED_NO_REFUND
 *
 * paymentId liga a reserva ao registo correspondente na Plataforma de Pagamentos.
 */
@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    private UUID paymentId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReservationItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = ReservationStatus.PENDING_PAYMENT;
        }
        if (expiresAt == null) {
            expiresAt = createdAt.plusMinutes(15);
        }
    }
}
