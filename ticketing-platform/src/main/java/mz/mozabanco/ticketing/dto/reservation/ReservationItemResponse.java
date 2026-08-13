package mz.mozabanco.ticketing.dto.reservation;

import mz.mozabanco.ticketing.domain.ReservationItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReservationItemResponse(
        UUID ticketTypeId, String ticketTypeName, int quantity, BigDecimal unitPrice, BigDecimal subtotal,
        List<String> ticketCodes
) {
    public static ReservationItemResponse from(ReservationItem item) {
        return new ReservationItemResponse(
                item.getTicketType().getId(), item.getTicketType().getName(), item.getQuantity(),
                item.getUnitPrice(), item.subtotal(),
                item.getTickets().stream().map(t -> t.getCode()).toList()
        );
    }
}
