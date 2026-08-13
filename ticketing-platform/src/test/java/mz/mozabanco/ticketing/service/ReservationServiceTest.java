package mz.mozabanco.ticketing.service;

import mz.mozabanco.ticketing.client.PaymentClient;
import mz.mozabanco.ticketing.domain.*;
import mz.mozabanco.ticketing.domain.enums.EventStatus;
import mz.mozabanco.ticketing.domain.enums.ReservationStatus;
import mz.mozabanco.ticketing.dto.reservation.ReservationItemRequest;
import mz.mozabanco.ticketing.dto.reservation.UpdateReservationRequest;
import mz.mozabanco.ticketing.exception.BusinessException;
import mz.mozabanco.ticketing.repository.EventRepository;
import mz.mozabanco.ticketing.repository.ReservationRepository;
import mz.mozabanco.ticketing.repository.TicketTypeRepository;
import mz.mozabanco.ticketing.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private EventRepository eventRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private PaymentClient paymentClient;

    private ReservationService reservationService;
    private AuthenticatedUser client;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        reservationService = new ReservationService(reservationRepository, eventRepository, ticketTypeRepository, paymentClient);
        client = new AuthenticatedUser(UUID.randomUUID(), "cliente@teste.mz", "CLIENTE");
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Reservation confirmedReservation(LocalDateTime eventDate, BigDecimal total) {
        Event event = Event.builder().id(UUID.randomUUID()).name("Concerto Teste")
                .status(EventStatus.PUBLISHED).eventDate(eventDate).organizerId(UUID.randomUUID()).build();
        Reservation reservation = Reservation.builder()
                .id(UUID.randomUUID())
                .clientId(client.id())
                .event(event)
                .status(ReservationStatus.CONFIRMED)
                .totalAmount(total)
                .paymentId(UUID.randomUUID())
                .build();
        return reservation;
    }

    @Test
    void deveReembolsar80PorcentoQuandoCancelamentoComMaisDe30Dias() {
        Reservation reservation = confirmedReservation(LocalDateTime.now().plusDays(45), new BigDecimal("1000.00"));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(paymentClient.refund(any(), any(), any()))
                .thenReturn(new PaymentClient.PaymentClientResponse(reservation.getPaymentId(), "PARTIALLY_REFUNDED", new BigDecimal("1000.00"), new BigDecimal("800.00")));

        reservationService.cancel(reservation.getId(), null, client);

        assertRefundAmount(reservation, "800.00");
    }

    @Test
    void deveReembolsar50PorcentoQuandoCancelamentoAte30Dias() {
        Reservation reservation = confirmedReservation(LocalDateTime.now().plusDays(10), new BigDecimal("1000.00"));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(paymentClient.refund(any(), any(), any()))
                .thenReturn(new PaymentClient.PaymentClientResponse(reservation.getPaymentId(), "PARTIALLY_REFUNDED", new BigDecimal("1000.00"), new BigDecimal("500.00")));

        reservationService.cancel(reservation.getId(), null, client);

        assertRefundAmount(reservation, "500.00");
    }

    @Test
    void deveCancelarSemReembolsoQuandoReservaAindaNaoFoiPaga() {
        Reservation reservation = confirmedReservation(LocalDateTime.now().plusDays(45), new BigDecimal("1000.00"));
        reservation.setStatus(ReservationStatus.PENDING_PAYMENT);
        reservation.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        ReservationResponseStatusHolder result = new ReservationResponseStatusHolder(
                reservationService.cancel(reservation.getId(), null, client).status());

        assertThat(result.status).isEqualTo(ReservationStatus.CANCELLED_NO_REFUND);
        verify(paymentClient, never()).refund(any(), any(), any());
    }

    private record ReservationResponseStatusHolder(ReservationStatus status) {}

    private void assertRefundAmount(Reservation reservation, String expectedAmount) {
        var amountCaptor = org.mockito.ArgumentCaptor.forClass(BigDecimal.class);
        verify(paymentClient).refund(eq(reservation.getPaymentId()), amountCaptor.capture(), any());
        assertThat(amountCaptor.getValue()).isEqualByComparingTo(expectedAmount);
    }

    @Test
    void naoDeveCancelarReservaJaCanceladaOuExpirada() {
        Reservation reservation = confirmedReservation(LocalDateTime.now().plusDays(45), new BigDecimal("1000.00"));
        reservation.setStatus(ReservationStatus.EXPIRED);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(reservation.getId(), null, client))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void naoDevePermitirClienteCancelarReservaDeOutroCliente() {
        Reservation reservation = confirmedReservation(LocalDateTime.now().plusDays(45), new BigDecimal("1000.00"));
        reservation.setClientId(UUID.randomUUID()); // outro cliente
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(reservation.getId(), null, client))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void naoDeveAlterarReservaJaConfirmada() {
        Reservation reservation = confirmedReservation(LocalDateTime.now().plusDays(45), new BigDecimal("1000.00"));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        UpdateReservationRequest request = new UpdateReservationRequest(
                java.util.List.of(new ReservationItemRequest(UUID.randomUUID(), 1)));

        assertThatThrownBy(() -> reservationService.update(reservation.getId(), request, client))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cancelamentoDeEventoDeveReembolsar100PorcentoDeReservaConfirmada() {
        Reservation reservation = confirmedReservation(LocalDateTime.now().plusDays(10), new BigDecimal("1000.00"));
        when(paymentClient.refund(any(), any(), any()))
                .thenReturn(new PaymentClient.PaymentClientResponse(reservation.getPaymentId(), "REFUNDED", new BigDecimal("1000.00"), new BigDecimal("1000.00")));

        reservationService.cancelDueToEventCancellation(reservation);

        assertRefundAmount(reservation, "1000.00");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED_REFUNDED);
    }
}
