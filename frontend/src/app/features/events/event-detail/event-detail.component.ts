import { Component, OnInit, signal, computed } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { EventService } from '../../../core/services/event.service';
import { ReservationService } from '../../../core/services/reservation.service';
import { AuthService } from '../../../core/services/auth.service';
import { EventItem, TicketType } from '../../../core/models/event.model';

interface CartLine {
  ticketType: TicketType;
  quantity: number;
}

@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe],
  templateUrl: './event-detail.component.html',
  styleUrl: './event-detail.component.scss',
})
export class EventDetailComponent implements OnInit {
  readonly event = signal<EventItem | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly cart = signal<Record<string, number>>({});
  readonly submitting = signal(false);
  readonly reservationDone = signal<string | null>(null); // reservationId apos reserva criada

  readonly cartLines = computed<CartLine[]>(() => {
    const ev = this.event();
    if (!ev) return [];
    return ev.ticketTypes
      .map((ticketType) => ({ ticketType, quantity: this.cart()[ticketType.id] ?? 0 }))
      .filter((line) => line.quantity > 0);
  });

  readonly cartTotal = computed(() =>
    this.cartLines().reduce((sum, line) => sum + line.ticketType.price * line.quantity, 0)
  );

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private eventService: EventService,
    private reservationService: ReservationService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.eventService.getById(id).subscribe({
      next: (event) => {
        this.event.set(event);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Evento não encontrado.');
        this.loading.set(false);
      },
    });
  }

  increment(ticketType: TicketType): void {
    const current = this.cart()[ticketType.id] ?? 0;
    if (current >= ticketType.quantityAvailable) return;
    this.cart.update((c) => ({ ...c, [ticketType.id]: current + 1 }));
  }

  decrement(ticketType: TicketType): void {
    const current = this.cart()[ticketType.id] ?? 0;
    if (current <= 0) return;
    this.cart.update((c) => ({ ...c, [ticketType.id]: current - 1 }));
  }

  reserveAndPay(): void {
    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/login']);
      return;
    }
    if (this.auth.role() !== 'CLIENTE') {
      this.errorMessage.set('Apenas contas de cliente podem reservar bilhetes.');
      return;
    }

    const ev = this.event();
    if (!ev || this.cartLines().length === 0) return;

    this.submitting.set(true);
    this.errorMessage.set(null);

    const request = {
      eventId: ev.id,
      items: this.cartLines().map((line) => ({
        ticketTypeId: line.ticketType.id,
        quantity: line.quantity,
      })),
    };

    this.reservationService.create(request).subscribe({
      next: (reservation) => {
        // RF3: reserva criada -> de seguida efetua-se o pagamento (RF4)
        this.reservationService.pay(reservation.id).subscribe({
          next: () => {
            this.submitting.set(false);
            this.reservationDone.set(reservation.id);
          },
          error: (err) => {
            this.submitting.set(false);
            this.errorMessage.set(
              err?.error?.message ?? 'A reserva foi criada mas o pagamento falhou. Consulta "Minhas reservas".'
            );
          },
        });
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(err?.error?.message ?? 'Não foi possível criar a reserva.');
      },
    });
  }

  goToHistory(): void {
    this.router.navigate(['/minhas-reservas']);
  }
}
