import { Component, OnInit, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ReservationService } from '../../../core/services/reservation.service';
import { EventService } from '../../../core/services/event.service';
import { Reservation, ReservationStatus } from '../../../core/models/reservation.model';
import { EventItem, TicketType } from '../../../core/models/event.model';

const STATUS_LABEL: Record<ReservationStatus, string> = {
  PENDING_PAYMENT: 'Aguarda pagamento',
  CONFIRMED: 'Confirmada',
  PAYMENT_FAILED: 'Pagamento falhou',
  EXPIRED: 'Expirada',
  CANCELLED_REFUNDED: 'Cancelada (reembolsada)',
  CANCELLED_NO_REFUND: 'Cancelada',
};

const STATUS_CLASS: Record<ReservationStatus, string> = {
  PENDING_PAYMENT: 'status-pending',
  CONFIRMED: 'status-confirmed',
  PAYMENT_FAILED: 'status-failed',
  EXPIRED: 'status-expired',
  CANCELLED_REFUNDED: 'status-cancelled',
  CANCELLED_NO_REFUND: 'status-cancelled',
};

interface EditLine {
  ticketType: TicketType;
  quantity: number;
  /** quantidade que esta reserva ja detem deste tipo de bilhete, antes da alteracao */
  originalQuantity: number;
}

@Component({
  selector: 'app-reservation-history',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe],
  templateUrl: './reservation-history.component.html',
  styleUrl: './reservation-history.component.scss',
})
export class ReservationHistoryComponent implements OnInit {
  readonly reservations = signal<Reservation[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly actingId = signal<string | null>(null);

  // Estado da alteracao ("alterar reserva") em curso, se alguma
  readonly editingId = signal<string | null>(null);
  readonly editingEvent = signal<EventItem | null>(null);
  readonly editCart = signal<Record<string, number>>({});
  readonly editLoading = signal(false);
  readonly editSaving = signal(false);
  readonly editError = signal<string | null>(null);

  readonly editLines = computed<EditLine[]>(() => {
    const ev = this.editingEvent();
    const reservation = this.reservations().find((r) => r.id === this.editingId());
    if (!ev) return [];
    const originals: Record<string, number> = {};
    reservation?.items.forEach((item) => (originals[item.ticketTypeId] = item.quantity));
    return ev.ticketTypes.map((ticketType) => ({
      ticketType,
      quantity: this.editCart()[ticketType.id] ?? 0,
      originalQuantity: originals[ticketType.id] ?? 0,
    }));
  });

  readonly editTotal = computed(() =>
    this.editLines().reduce((sum, line) => sum + line.ticketType.price * line.quantity, 0)
  );

  constructor(
    private reservationService: ReservationService,
    private eventService: EventService
  ) {}

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.reservationService.history().subscribe({
      next: (reservations) => {
        this.reservations.set(reservations);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Não foi possível carregar o histórico.');
        this.loading.set(false);
      },
    });
  }

  statusLabel(status: ReservationStatus): string {
    return STATUS_LABEL[status];
  }

  statusClass(status: ReservationStatus): string {
    return STATUS_CLASS[status];
  }

  canCancel(reservation: Reservation): boolean {
    return reservation.status === 'CONFIRMED' || reservation.status === 'PENDING_PAYMENT';
  }

  canAlter(reservation: Reservation): boolean {
    return reservation.status === 'PENDING_PAYMENT';
  }

  canPay(reservation: Reservation): boolean {
    return reservation.status === 'PENDING_PAYMENT';
  }

  // --- Pagar (retomar pagamento de uma reserva ainda pendente) ---

  pay(reservation: Reservation): void {
    this.actingId.set(reservation.id);
    this.errorMessage.set(null);
    this.reservationService.pay(reservation.id).subscribe({
      next: () => {
        this.actingId.set(null);
        this.load();
      },
      error: (err) => {
        this.actingId.set(null);
        this.errorMessage.set(err?.error?.message ?? 'Não foi possível processar o pagamento.');
      },
    });
  }

  // --- Cancelar ---

  cancel(reservation: Reservation): void {
    const message =
      reservation.status === 'PENDING_PAYMENT'
        ? 'Cancelar esta reserva? Como ainda não foi paga, não há qualquer reembolso a processar.'
        : 'Cancelar esta reserva? A percentagem de reembolso depende da antecedência face à data do evento.';

    if (!confirm(message)) {
      return;
    }
    this.actingId.set(reservation.id);
    this.errorMessage.set(null);

    this.reservationService.cancel(reservation.id).subscribe({
      next: () => {
        this.actingId.set(null);
        this.load();
      },
      error: (err) => {
        this.actingId.set(null);
        this.errorMessage.set(err?.error?.message ?? 'Não foi possível cancelar a reserva.');
      },
    });
  }

  // --- Alterar (quantidades/tipos de bilhete, apenas antes do pagamento) ---

  startEdit(reservation: Reservation): void {
    this.editingId.set(reservation.id);
    this.editError.set(null);
    this.editLoading.set(true);
    this.editCart.set({});

    this.eventService.getById(reservation.eventId).subscribe({
      next: (event) => {
        this.editingEvent.set(event);
        const cart: Record<string, number> = {};
        reservation.items.forEach((item) => (cart[item.ticketTypeId] = item.quantity));
        this.editCart.set(cart);
        this.editLoading.set(false);
      },
      error: () => {
        this.editError.set('Não foi possível carregar os dados do evento.');
        this.editLoading.set(false);
      },
    });
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.editingEvent.set(null);
    this.editCart.set({});
    this.editError.set(null);
  }

  editMax(line: EditLine): number {
    // Os lugares ja atribuidos a esta reserva ainda contam como "vendidos" no evento
    // enquanto a reserva estiver pendente, por isso somam-se de volta ao disponivel.
    return line.ticketType.quantityAvailable + line.originalQuantity;
  }

  incrementEdit(line: EditLine): void {
    if (line.quantity >= this.editMax(line)) return;
    this.editCart.update((c) => ({ ...c, [line.ticketType.id]: line.quantity + 1 }));
  }

  decrementEdit(line: EditLine): void {
    if (line.quantity <= 0) return;
    this.editCart.update((c) => ({ ...c, [line.ticketType.id]: line.quantity - 1 }));
  }

  saveEdit(): void {
    const reservationId = this.editingId();
    if (!reservationId) return;

    const items = this.editLines()
      .filter((line) => line.quantity > 0)
      .map((line) => ({ ticketTypeId: line.ticketType.id, quantity: line.quantity }));

    if (items.length === 0) {
      this.editError.set('A reserva tem de ter pelo menos um bilhete. Para remover tudo, cancela a reserva.');
      return;
    }

    this.editSaving.set(true);
    this.editError.set(null);

    this.reservationService.update(reservationId, { items }).subscribe({
      next: () => {
        this.editSaving.set(false);
        this.cancelEdit();
        this.load();
      },
      error: (err) => {
        this.editSaving.set(false);
        this.editError.set(err?.error?.message ?? 'Não foi possível alterar a reserva.');
      },
    });
  }
}
