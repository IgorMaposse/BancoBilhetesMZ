import { Component, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { EventService } from '../../../core/services/event.service';

@Component({
  selector: 'app-event-create',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './event-create.component.html',
  styleUrl: './event-create.component.scss',
})
export class EventCreateComponent {
  private fb = inject(FormBuilder);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  form = this.fb.group({
    name: ['', Validators.required],
    description: [''],
    category: ['concerto', Validators.required],
    venue: ['', Validators.required],
    address: ['', Validators.required],
    eventDate: ['', Validators.required],
    ticketTypes: this.fb.array([this.buildTicketTypeGroup()]),
  });

  constructor(private eventService: EventService, private router: Router) {}

  get ticketTypes(): FormArray {
    return this.form.get('ticketTypes') as FormArray;
  }

  private buildTicketTypeGroup() {
    return this.fb.group({
      name: ['', Validators.required],
      price: [0, [Validators.required, Validators.min(0.01)]],
      quantityTotal: [1, [Validators.required, Validators.min(1)]],
    });
  }

  addTicketType(): void {
    this.ticketTypes.push(this.buildTicketTypeGroup());
  }

  removeTicketType(index: number): void {
    if (this.ticketTypes.length > 1) {
      this.ticketTypes.removeAt(index);
    }
  }

  submit(publish: boolean): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const raw = this.form.getRawValue();
    const request = {
      name: raw.name!,
      description: raw.description ?? '',
      category: raw.category!,
      venue: raw.venue!,
      address: raw.address!,
      eventDate: new Date(raw.eventDate!).toISOString(),
      ticketTypes: (raw.ticketTypes ?? []).map((t) => ({
        name: t!.name!,
        price: Number(t!.price),
        quantityTotal: Number(t!.quantityTotal),
      })),
    };

    this.eventService.create(request).subscribe({
      next: (event) => {
        if (!publish) {
          this.submitting.set(false);
          this.router.navigate(['/eventos', event.id]);
          return;
        }
        this.eventService.publish(event.id).subscribe({
          next: () => {
            this.submitting.set(false);
            this.router.navigate(['/eventos', event.id]);
          },
          error: (err) => {
            this.submitting.set(false);
            this.errorMessage.set(err?.error?.message ?? 'Evento criado, mas não foi possível publicar.');
          },
        });
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(err?.error?.message ?? 'Não foi possível criar o evento.');
      },
    });
  }
}
