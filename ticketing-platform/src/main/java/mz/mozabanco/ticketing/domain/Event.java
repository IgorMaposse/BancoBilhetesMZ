package mz.mozabanco.ticketing.domain;

import jakarta.persistence.*;
import lombok.*;
import mz.mozabanco.ticketing.domain.enums.EventStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RF2 - Informacao necessaria para representar um evento.
 * Categoria e um texto livre (assumido) para simplificar; poderia ser uma tabela
 * de referencia separada numa versao mais completa.
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 80)
    private String category; // concerto, conferencia, espetaculo, desporto, etc.

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    @Column(nullable = false)
    private UUID organizerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TicketType> ticketTypes = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = EventStatus.DRAFT;
        }
    }
}
