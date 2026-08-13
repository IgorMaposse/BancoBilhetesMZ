package mz.mozabanco.ticketing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.mozabanco.ticketing.domain.Reservation;
import mz.mozabanco.ticketing.domain.enums.ReservationStatus;
import mz.mozabanco.ticketing.repository.ReservationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RF3/RF5 - garante que reservas PENDING_PAYMENT nao pagas a tempo (15 min, ver
 * Reservation.onCreate) libertam automaticamente o inventario de bilhetes, evitando
 * bloquear lugares indefinidamente por clientes que desistem sem cancelar.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpirationJob {

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    @Scheduled(fixedDelayString = "${reservations.expiration-check-interval-ms:60000}")
    public void expireStaleReservations() {
        List<Reservation> expired = reservationRepository.findByStatusAndExpiresAtBefore(
                ReservationStatus.PENDING_PAYMENT, LocalDateTime.now());

        if (expired.isEmpty()) {
            return;
        }

        log.info("A expirar {} reserva(s) pendente(s) sem pagamento", expired.size());
        expired.forEach(reservationService::expireAndRelease);
    }
}
