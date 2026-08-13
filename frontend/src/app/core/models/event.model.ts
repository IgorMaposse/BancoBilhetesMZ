export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'FINISHED';

export interface TicketType {
  id: string;
  name: string;
  price: number;
  quantityTotal: number;
  quantityAvailable: number;
}

export interface EventItem {
  id: string;
  name: string;
  description: string;
  category: string;
  venue: string;
  address: string;
  eventDate: string;
  status: EventStatus;
  organizerId: string;
  ticketTypes: TicketType[];
}

export interface TicketTypeRequest {
  name: string;
  price: number;
  quantityTotal: number;
}

export interface CreateEventRequest {
  name: string;
  description: string;
  category: string;
  venue: string;
  address: string;
  eventDate: string;
  ticketTypes: TicketTypeRequest[];
}
