package mz.mozabanco.ticketing.dto.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record CreateEventRequest(
        @NotBlank String name,
        String description,
        @NotBlank String category,
        @NotBlank String venue,
        @NotBlank String address,
        @NotNull @Future LocalDateTime eventDate,
        @NotEmpty @Valid List<TicketTypeRequest> ticketTypes
) {}
