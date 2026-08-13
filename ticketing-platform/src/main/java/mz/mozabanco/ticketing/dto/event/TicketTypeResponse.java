package mz.mozabanco.ticketing.dto.event;

import mz.mozabanco.ticketing.domain.TicketType;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketTypeResponse(UUID id, String name, BigDecimal price, int quantityTotal, int quantityAvailable) {
    public static TicketTypeResponse from(TicketType t) {
        return new TicketTypeResponse(t.getId(), t.getName(), t.getPrice(), t.getQuantityTotal(), t.quantityAvailable());
    }
}
