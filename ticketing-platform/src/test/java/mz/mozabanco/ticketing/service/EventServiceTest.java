package mz.mozabanco.ticketing.service;

import mz.mozabanco.ticketing.domain.Event;
import mz.mozabanco.ticketing.domain.Reservation;
import mz.mozabanco.ticketing.domain.enums.EventStatus;
import mz.mozabanco.ticketing.domain.enums.ReservationStatus;
import mz.mozabanco.ticketing.repository.EventRepository;
import mz.mozabanco.ticketing.repository.ReservationRepository;
import mz.mozabanco.ticketing.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationService reservationService;

    private EventService eventService;
    private AuthenticatedUser organizer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        eventService = new EventService(eventRepository, reservationRepository, reservationService);
        organizer = new AuthenticatedUser(UUID.randomUUID(), "organizador@teste.mz", "ORGANIZADOR");
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void aoCancelarEventoDeveCancelarEmCascataTodasAsReservasVivas() {
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .status(EventStatus.PUBLISHED)
                .eventDate(LocalDateTime.now().plusDays(20))
                .organizerId(organizer.id())
                .build();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        Reservation confirmed = Reservation.builder().id(UUID.randomUUID()).status(ReservationStatus.CONFIRMED).build();
        Reservation pending = Reservation.builder().id(UUID.randomUUID()).status(ReservationStatus.PENDING_PAYMENT).build();
        when(reservationRepository.findByEvent_IdAndStatusIn(eq(event.getId()), any()))
                .thenReturn(List.of(confirmed, pending));

        eventService.cancel(event.getId(), organizer);

        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationService, times(2)).cancelDueToEventCancellation(captor.capture());
        assertThat(captor.getAllValues()).containsExactlyInAnyOrder(confirmed, pending);
    }

    @Test
    void naoDevePermitirOrganizadorCancelarEventoDeOutroOrganizador() {
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .status(EventStatus.PUBLISHED)
                .eventDate(LocalDateTime.now().plusDays(20))
                .organizerId(UUID.randomUUID()) // outro organizador
                .build();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        org.junit.jupiter.api.Assertions.assertThrows(
                mz.mozabanco.ticketing.exception.BusinessException.class,
                () -> eventService.cancel(event.getId(), organizer));
    }
}
