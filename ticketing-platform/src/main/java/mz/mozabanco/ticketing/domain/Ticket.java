package mz.mozabanco.ticketing.domain;

import jakarta.persistence.*;
import lombok.*;
import mz.mozabanco.ticketing.domain.enums.TicketStatus;

import java.util.UUID;

/**
 * Bilhete individual e nominativo, gerado apos confirmacao do pagamento da reserva.
 * O "code" e o codigo unico (ex.: representado num QR code na app) usado para
 * validacao de entrada no evento.
 */
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_item_id", nullable = false)
    private ReservationItem reservationItem;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @PrePersist
    void onCreate() {
        if (code == null) {
            code = "TCK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        }
        if (status == null) {
            status = TicketStatus.VALID;
        }
    }
}
