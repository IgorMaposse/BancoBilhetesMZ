package mz.mozabanco.ticketing.dto.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * RF3 - alteracao de uma reserva ainda nao paga (PENDING_PAYMENT).
 * Substitui integralmente a lista de itens da reserva (quantidades/tipos de bilhete);
 * o inventario antigo e libertado e o novo e reservado atomicamente, dentro da mesma
 * transacao, tal como em create().
 */
public record UpdateReservationRequest(
        @NotEmpty @Valid List<ReservationItemRequest> items
) {}
