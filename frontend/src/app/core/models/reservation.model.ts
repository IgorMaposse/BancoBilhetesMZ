export type ReservationStatus =
  | 'PENDING_PAYMENT'
  | 'CONFIRMED'
  | 'PAYMENT_FAILED'
  | 'EXPIRED'
  | 'CANCELLED_REFUNDED'
  | 'CANCELLED_NO_REFUND';

export interface ReservationItemResponse {
  ticketTypeId: string;
  ticketTypeName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
  ticketCodes: string[];
}

export interface Reservation {
  id: string;
  eventId: string;
  eventName: string;
  status: ReservationStatus;
  totalAmount: number;
  paymentId: string | null;
  createdAt: string;
  expiresAt: string;
  items: ReservationItemResponse[];
}

export interface ReservationItemRequest {
  ticketTypeId: string;
  quantity: number;
}

export interface CreateReservationRequest {
  eventId: string;
  items: ReservationItemRequest[];
}

export interface UpdateReservationRequest {
  items: ReservationItemRequest[];
}
