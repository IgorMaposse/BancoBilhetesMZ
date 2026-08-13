package mz.mozabanco.ticketing.dto.event;

import mz.mozabanco.ticketing.domain.Event;
import mz.mozabanco.ticketing.domain.enums.EventStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EventResponse(
        UUID id, String name, String description, String category, String venue, String address,
        LocalDateTime eventDate, EventStatus status, UUID organizerId, List<TicketTypeResponse> ticketTypes
) {
    public static EventResponse from(Event e) {
        return new EventResponse(
                e.getId(), e.getName(), e.getDescription(), e.getCategory(), e.getVenue(), e.getAddress(),
                e.getEventDate(), e.getStatus(), e.getOrganizerId(),
                e.getTicketTypes().stream().map(TicketTypeResponse::from).toList()
        );
    }
}
