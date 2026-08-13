package mz.mozabanco.ticketing.repository;

import mz.mozabanco.ticketing.domain.Reservation;
import mz.mozabanco.ticketing.domain.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    List<Reservation> findByClientIdOrderByCreatedAtDesc(UUID clientId);
    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime now);

    // Usado no cancelamento de eventos (RF5): localizar todas as reservas ainda "vivas"
    // (confirmadas ou pendentes de pagamento) associadas a um evento para cancelar em cascata.
    List<Reservation> findByEvent_IdAndStatusIn(UUID eventId, List<ReservationStatus> statuses);
}
