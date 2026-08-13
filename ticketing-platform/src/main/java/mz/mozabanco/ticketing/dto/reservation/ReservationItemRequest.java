package mz.mozabanco.ticketing.dto.reservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReservationItemRequest(
        @NotNull UUID ticketTypeId,
        @Min(1) int quantity
) {}
