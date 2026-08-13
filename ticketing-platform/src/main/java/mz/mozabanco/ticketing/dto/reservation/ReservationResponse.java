package mz.mozabanco.ticketing.dto.reservation;

import mz.mozabanco.ticketing.domain.Reservation;
import mz.mozabanco.ticketing.domain.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id, UUID eventId, String eventName, ReservationStatus status, BigDecimal totalAmount,
        UUID paymentId, LocalDateTime createdAt, LocalDateTime expiresAt,
        List<ReservationItemResponse> items
) {
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(), r.getEvent().getId(), r.getEvent().getName(), r.getStatus(), r.getTotalAmount(),
                r.getPaymentId(), r.getCreatedAt(), r.getExpiresAt(),
                r.getItems().stream().map(ReservationItemResponse::from).toList()
        );
    }
}
