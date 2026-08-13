package mz.mozabanco.ticketing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.mozabanco.ticketing.dto.reservation.CancelReservationRequest;
import mz.mozabanco.ticketing.dto.reservation.CreateReservationRequest;
import mz.mozabanco.ticketing.dto.reservation.ReservationResponse;
import mz.mozabanco.ticketing.dto.reservation.UpdateReservationRequest;
import mz.mozabanco.ticketing.security.AuthenticatedUser;
import mz.mozabanco.ticketing.service.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** RF3 - reservar, pagar, cancelar e consultar historico de reservas. */
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request,
                                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.create(request, user));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<ReservationResponse> pay(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(reservationService.pay(id, user));
    }

    // RF3: alterar quantidades/tipos de bilhete de uma reserva ainda pendente de pagamento
    @PutMapping("/{id}")
    public ResponseEntity<ReservationResponse> update(@PathVariable UUID id,
                                                        @Valid @RequestBody UpdateReservationRequest request,
                                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(reservationService.update(id, request, user));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancel(@PathVariable UUID id,
                                                        @RequestBody(required = false) CancelReservationRequest request,
                                                        @AuthenticationPrincipal AuthenticatedUser user) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(reservationService.cancel(id, reason, user));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ReservationResponse>> history(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(reservationService.history(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getById(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(reservationService.getById(id, user));
    }
}
