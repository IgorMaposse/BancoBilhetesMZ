package mz.mozabanco.ticketing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.mozabanco.ticketing.domain.Event;
import mz.mozabanco.ticketing.domain.Reservation;
import mz.mozabanco.ticketing.domain.TicketType;
import mz.mozabanco.ticketing.domain.enums.EventStatus;
import mz.mozabanco.ticketing.domain.enums.ReservationStatus;
import mz.mozabanco.ticketing.dto.event.CreateEventRequest;
import mz.mozabanco.ticketing.dto.event.EventResponse;
import mz.mozabanco.ticketing.exception.BusinessException;
import mz.mozabanco.ticketing.exception.ResourceNotFoundException;
import mz.mozabanco.ticketing.repository.EventRepository;
import mz.mozabanco.ticketing.repository.ReservationRepository;
import mz.mozabanco.ticketing.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    @Transactional
    public EventResponse create(CreateEventRequest request, AuthenticatedUser organizer) {
        Event event = Event.builder()
                .name(request.name())
                .description(request.description())
                .category(request.category())
                .venue(request.venue())
                .address(request.address())
                .eventDate(request.eventDate())
                .organizerId(organizer.id())
                .status(EventStatus.DRAFT)
                .build();

        request.ticketTypes().forEach(tt -> event.getTicketTypes().add(
                TicketType.builder()
                        .event(event)
                        .name(tt.name())
                        .price(tt.price())
                        .quantityTotal(tt.quantityTotal())
                        .quantitySold(0)
                        .build()
        ));
        Event savedEvent = eventRepository.save(event);
        return EventResponse.from(savedEvent);
    }

    @Transactional
    public EventResponse publish(UUID eventId, AuthenticatedUser organizer) {
        Event event = getOwnedEvent(eventId, organizer);
        event.setStatus(EventStatus.PUBLISHED);
        return EventResponse.from(eventRepository.save(event));
    }

    /**
     * RF2 + RF5: cancelar um evento despoleta o cancelamento/reembolso em cascata de todas
     * as reservas ainda "vivas" (CONFIRMED ou PENDING_PAYMENT) associadas a este evento.
     * Reembolso a 100% para reservas confirmadas (a culpa e do organizador, nao do cliente),
     * e simples libertacao de inventario para reservas ainda nao pagas.
     * Se o reembolso de alguma reserva individual falhar (ex.: payment-platform indisponivel),
     * o evento e as restantes reservas nao ficam bloqueados - o erro fica registado em log para
     * seguimento manual, em vez de reverter toda a operacao (uma reserva com falha de reembolso
     * nao deve impedir o cancelamento do evento nem o reembolso das restantes).
     */
    @Transactional
    public EventResponse cancel(UUID eventId, AuthenticatedUser organizer) {
        Event event = getOwnedEvent(eventId, organizer);
        event.setStatus(EventStatus.CANCELLED);
        Event saved = eventRepository.save(event);

        List<Reservation> liveReservations = reservationRepository.findByEvent_IdAndStatusIn(
                eventId, List.of(ReservationStatus.CONFIRMED, ReservationStatus.PENDING_PAYMENT));

        for (Reservation reservation : liveReservations) {
            try {
                reservationService.cancelDueToEventCancellation(reservation);
            } catch (Exception ex) {
                log.error("Falha ao cancelar/reembolsar reserva {} na sequencia do cancelamento do evento {}",
                        reservation.getId(), eventId, ex);
            }
        }

        return EventResponse.from(saved);
    }
    
    @Transactional(readOnly = true)
    public List<EventResponse> listPublished(String category) {

        List<Event> events = category == null || category.isBlank()
                ? eventRepository.findByStatus(EventStatus.PUBLISHED)
                : eventRepository.findByStatusAndCategoryIgnoreCase(
                        EventStatus.PUBLISHED,
                        category
                );

        return events.stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listMine(AuthenticatedUser organizer) {
        return eventRepository.findByOrganizerId(organizer.id())
                .stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse getById(UUID id) {
        return EventResponse.from(findEvent(id));
    }
    
    

    private Event getOwnedEvent(UUID eventId, AuthenticatedUser organizer) {
        Event event = findEvent(eventId);
        boolean isAdmin = "ADMIN".equals(organizer.role());
        if (!isAdmin && !event.getOrganizerId().equals(organizer.id())) {
            throw new BusinessException("Este evento nao pertence ao organizador autenticado", HttpStatus.FORBIDDEN);
        }
        return event;
    }

    private Event findEvent(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento nao encontrado: " + id));
    }
}
