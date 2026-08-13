package mz.mozabanco.ticketing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.mozabanco.ticketing.client.PaymentClient;
import mz.mozabanco.ticketing.client.PaymentClientException;
import mz.mozabanco.ticketing.domain.*;
import mz.mozabanco.ticketing.domain.enums.EventStatus;
import mz.mozabanco.ticketing.domain.enums.ReservationStatus;
import mz.mozabanco.ticketing.domain.enums.TicketStatus;
import mz.mozabanco.ticketing.dto.reservation.CreateReservationRequest;
import mz.mozabanco.ticketing.dto.reservation.ReservationItemRequest;
import mz.mozabanco.ticketing.dto.reservation.ReservationResponse;
import mz.mozabanco.ticketing.dto.reservation.UpdateReservationRequest;
import mz.mozabanco.ticketing.exception.BusinessException;
import mz.mozabanco.ticketing.exception.ResourceNotFoundException;
import mz.mozabanco.ticketing.repository.EventRepository;
import mz.mozabanco.ticketing.repository.ReservationRepository;
import mz.mozabanco.ticketing.repository.TicketTypeRepository;
import mz.mozabanco.ticketing.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * RF3 + RF4 + RF5.
 *
 * Consistencia reserva <-> pagamento (RF5): a reserva so e criada depois de o lote
 * de bilhetes ser reservado atomicamente (lock pessimista em TicketType), e a
 * confirmacao so acontece depois de o debito na Plataforma de Pagamentos ter
 * sucesso. Se o debito falhar, a reserva passa a PAYMENT_FAILED e os lugares sao
 * libertados na mesma transacao - nao ha bilhetes "presos" sem correspondencia
 * a um pagamento valido.
 *
 * Premissa: dado o prazo do exercicio, a chamada ao payment-platform e sincrona
 * dentro da mesma requisicao HTTP. Em producao recomendar-se-ia um padrao outbox
 * + fila (ex.: Kafka/RabbitMQ) para tolerar indisponibilidade temporaria do
 * payment-platform sem bloquear o utilizador - documentado no ARCHITECTURE.md.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final PaymentClient paymentClient;

    @Transactional
    public ReservationResponse create(CreateReservationRequest request, AuthenticatedUser client) {
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Evento nao encontrado"));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BusinessException("Evento nao esta disponivel para venda", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Reservation reservation = Reservation.builder()
                .clientId(client.id())
                .event(event)
                .status(ReservationStatus.PENDING_PAYMENT)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = reserveItems(reservation, request.items(), event);
        reservation.setTotalAmount(total);
        Reservation saved = reservationRepository.save(reservation);
        return ReservationResponse.from(saved);
    }

    /**
     * RF3 - alteracao de uma reserva ainda nao paga: substitui as quantidades/tipos de
     * bilhete escolhidos antes de o pagamento ser efetuado.
     *
     * Consistencia: o inventario reservado anteriormente e libertado e o novo pedido e
     * validado/reservado atomicamente (mesmo lock pessimista de create()) dentro da mesma
     * transacao - nao ha uma janela em que os lugares antigos estejam libertos mas os novos
     * ainda nao garantidos ficarem visiveis a outros clientes, porque tudo corre numa unica
     * transacao de base de dados.
     */
    @Transactional
    public ReservationResponse update(UUID reservationId, UpdateReservationRequest request, AuthenticatedUser client) {
        Reservation reservation = getOwnedReservation(reservationId, client);

        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new BusinessException(
                    "Apenas reservas pendentes de pagamento podem ser alteradas. "
                            + "Para reservas confirmadas, cancele e crie uma nova reserva.",
                    HttpStatus.CONFLICT);
        }
        if (reservation.getExpiresAt().isBefore(LocalDateTime.now())) {
            expireAndRelease(reservation);
            throw new BusinessException("Reserva expirou; os bilhetes foram libertados", HttpStatus.GONE);
        }

        // Liberta o inventario atualmente associado a esta reserva antes de aplicar o novo pedido
        releaseInventory(reservation);
        reservation.getItems().clear();

        BigDecimal total = reserveItems(reservation, request.items(), reservation.getEvent());
        reservation.setTotalAmount(total);

        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    /** Reserva (com lock pessimista) os itens pedidos contra o inventario do evento e devolve o total. */
    private BigDecimal reserveItems(Reservation reservation, List<ReservationItemRequest> items, Event event) {
        BigDecimal total = BigDecimal.ZERO;

        for (ReservationItemRequest itemReq : items) {
            // Lock pessimista: impede que dois clientes reservem os ultimos lugares em simultaneo
            TicketType ticketType = ticketTypeRepository.findByIdForUpdate(itemReq.ticketTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tipo de bilhete nao encontrado"));

            if (!ticketType.getEvent().getId().equals(event.getId())) {
                throw new BusinessException("Tipo de bilhete nao pertence a este evento", HttpStatus.BAD_REQUEST);
            }
            if (ticketType.quantityAvailable() < itemReq.quantity()) {
                throw new BusinessException(
                        "Quantidade indisponivel para " + ticketType.getName()
                                + " (disponiveis: " + ticketType.quantityAvailable() + ")",
                        HttpStatus.CONFLICT);
            }

            ticketType.setQuantitySold(ticketType.getQuantitySold() + itemReq.quantity());
            ticketTypeRepository.save(ticketType);

            ReservationItem item = ReservationItem.builder()
                    .reservation(reservation)
                    .ticketType(ticketType)
                    .quantity(itemReq.quantity())
                    .unitPrice(ticketType.getPrice())
                    .build();
            reservation.getItems().add(item);
            total = total.add(item.subtotal());
        }

        return total;
    }

    @Transactional
    public ReservationResponse pay(UUID reservationId, AuthenticatedUser client) {
        Reservation reservation = getOwnedReservation(reservationId, client);

        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new BusinessException("Reserva nao esta pendente de pagamento", HttpStatus.CONFLICT);
        }
        if (reservation.getExpiresAt().isBefore(LocalDateTime.now())) {
            expireAndRelease(reservation);
            throw new BusinessException("Reserva expirou; os bilhetes foram libertados", HttpStatus.GONE);
        }

        try {
            PaymentClient.PaymentClientResponse payment = paymentClient.debit(
                    reservation.getId().toString(),
                    reservation.getTotalAmount(),
                    reservation.getId().toString(),
                    "BancoBilhetes - " + reservation.getEvent().getName()
            );

            reservation.setPaymentId(payment.paymentId());

            if ("COMPLETED".equals(payment.status())) {
                reservation.setStatus(ReservationStatus.CONFIRMED);
                reservation.setConfirmedAt(LocalDateTime.now());
                reservation.getItems().forEach(item ->
                        item.getTickets().addAll(generateTickets(item)));
            } else {
                reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
                releaseInventory(reservation);
            }
        } catch (PaymentClientException ex) {
            log.error("Falha ao comunicar com payment-platform para reserva {}", reservationId, ex);
            reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
            releaseInventory(reservation);
            throw new BusinessException(
                    "Nao foi possivel processar o pagamento. Tente novamente.", HttpStatus.BAD_GATEWAY);
        }

        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    @Transactional
    public ReservationResponse cancel(UUID reservationId, String customReason, AuthenticatedUser client) {
        Reservation reservation = getOwnedReservation(reservationId, client);

        if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
            // Nada foi cobrado ainda, entao nao ha reembolso a processar - so libertar o inventario.
            reservation.setStatus(ReservationStatus.CANCELLED_NO_REFUND);
            reservation.setCancelledAt(LocalDateTime.now());
            releaseInventory(reservation);
            return ReservationResponse.from(reservationRepository.save(reservation));
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BusinessException(
                    "Esta reserva nao pode ser cancelada no estado atual (" + reservation.getStatus() + ")",
                    HttpStatus.CONFLICT);
        }

        applyRefundAndCancel(reservation, refundPercentageFor(reservation), customReason);
        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    /**
     * Cancelamento em cascata quando o ORGANIZADOR cancela o evento (RF2/RF5).
     * Ao contrario do cancelamento por iniciativa do cliente, a falha e do organizador/banco,
     * pelo que o reembolso e sempre de 100% independentemente da antecedencia, e reservas
     * ainda nao pagas sao simplesmente libertadas.
     * Chamado por EventService.cancel() para cada reserva "viva" associada ao evento.
     */
    @Transactional
    public void cancelDueToEventCancellation(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
            reservation.setStatus(ReservationStatus.CANCELLED_NO_REFUND);
            reservation.setCancelledAt(LocalDateTime.now());
            releaseInventory(reservation);
            reservationRepository.save(reservation);
            return;
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            return; // ja cancelada/expirada/falhada - nada a fazer
        }
        applyRefundAndCancel(reservation, BigDecimal.ONE, "Evento cancelado pelo organizador - reembolso integral");
        reservationRepository.save(reservation);
    }

    private BigDecimal refundPercentageFor(Reservation reservation) {
        // RF5: politica de reembolso baseada na antecedencia face a data do evento
        long daysToEvent = ChronoUnit.DAYS.between(LocalDateTime.now(), reservation.getEvent().getEventDate());
        return daysToEvent > 30 ? new BigDecimal("0.80") : new BigDecimal("0.50");
    }

    private void applyRefundAndCancel(Reservation reservation, BigDecimal refundPercentage, String customReason) {
        BigDecimal refundAmount = reservation.getTotalAmount()
                .multiply(refundPercentage)
                .setScale(2, RoundingMode.HALF_UP);

        String reason = customReason != null && !customReason.isBlank()
                ? customReason
                : "Cancelamento (" + refundPercentage.multiply(BigDecimal.valueOf(100)) + "% reembolso)";

        try {
            paymentClient.refund(reservation.getPaymentId(), refundAmount, reason);
        } catch (PaymentClientException ex) {
            log.error("Falha ao reembolsar reserva {}", reservation.getId(), ex);
            throw new BusinessException(
                    "Nao foi possivel processar o reembolso neste momento. Tente novamente.", HttpStatus.BAD_GATEWAY);
        }

        reservation.setStatus(ReservationStatus.CANCELLED_REFUNDED);
        reservation.setCancelledAt(LocalDateTime.now());
        reservation.getItems().forEach(item ->
                item.getTickets().forEach(t -> t.setStatus(TicketStatus.CANCELLED)));
        releaseInventory(reservation);
    }

    public List<ReservationResponse> history(AuthenticatedUser client) {
        return reservationRepository.findByClientIdOrderByCreatedAtDesc(client.id())
                .stream().map(ReservationResponse::from).toList();
    }

    public ReservationResponse getById(UUID id, AuthenticatedUser client) {
        return ReservationResponse.from(getOwnedReservation(id, client));
    }

    /** Job agendado (ver ReservationExpirationJob) chama isto para libertar reservas nao pagas a tempo. */
    @Transactional
    public void expireAndRelease(Reservation reservation) {
        reservation.setStatus(ReservationStatus.EXPIRED);
        releaseInventory(reservation);
        reservationRepository.save(reservation);
    }

    private void releaseInventory(Reservation reservation) {
        reservation.getItems().forEach(item -> {
            TicketType tt = item.getTicketType();
            tt.setQuantitySold(Math.max(0, tt.getQuantitySold() - item.getQuantity()));
            ticketTypeRepository.save(tt);
        });
    }

    private List<Ticket> generateTickets(ReservationItem item) {
        return java.util.stream.IntStream.range(0, item.getQuantity())
                .mapToObj(i -> Ticket.builder().reservationItem(item).status(TicketStatus.VALID).build())
                .toList();
    }

    private Reservation getOwnedReservation(UUID id, AuthenticatedUser client) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva nao encontrada"));
        boolean isAdmin = "ADMIN".equals(client.role());
        if (!isAdmin && !reservation.getClientId().equals(client.id())) {
            throw new BusinessException("Esta reserva nao pertence ao cliente autenticado", HttpStatus.FORBIDDEN);
        }
        return reservation;
    }
}
