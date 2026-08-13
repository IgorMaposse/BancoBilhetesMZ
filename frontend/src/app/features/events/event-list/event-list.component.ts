import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { EventService } from '../../../core/services/event.service';
import { EventItem } from '../../../core/models/event.model';

@Component({
  selector: 'app-event-list',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe],
  templateUrl: './event-list.component.html',
  styleUrl: './event-list.component.scss',
})
export class EventListComponent implements OnInit {
  readonly events = signal<EventItem[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  constructor(private eventService: EventService) {}

  ngOnInit(): void {
    this.eventService.listPublished().subscribe({
      next: (events) => {
        this.events.set(events);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Não foi possível carregar os eventos. Tenta novamente.');
        this.loading.set(false);
      },
    });
  }

  lowestPrice(event: EventItem): number | null {
    if (!event.ticketTypes.length) return null;
    return Math.min(...event.ticketTypes.map((t) => t.price));
  }

  totalAvailable(event: EventItem): number {
    return event.ticketTypes.reduce((sum, t) => sum + t.quantityAvailable, 0);
  }
}
