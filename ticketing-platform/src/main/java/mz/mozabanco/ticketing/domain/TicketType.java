package mz.mozabanco.ticketing.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Categoria/lote de bilhetes de um evento (ex.: "Plateia", "VIP", "Geral").
 * quantitySold e mantido de forma otimista com controlo de concorrencia (@Version)
 * para evitar overbooking quando varios clientes reservam em simultaneo.
 */
@Entity
@Table(name = "ticket_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int quantityTotal;

    @Column(nullable = false)
    private int quantitySold;

    @Version
    private Long version;

    public int quantityAvailable() {
        return quantityTotal - quantitySold;
    }
}
