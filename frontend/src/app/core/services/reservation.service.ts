import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateReservationRequest, Reservation, UpdateReservationRequest } from '../models/reservation.model';

@Injectable({ providedIn: 'root' })
export class ReservationService {
  private readonly baseUrl = `${environment.apiUrl}/api/v1/reservations`;

  constructor(private http: HttpClient) {}

  create(request: CreateReservationRequest): Observable<Reservation> {
    return this.http.post<Reservation>(this.baseUrl, request);
  }

  pay(id: string): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.baseUrl}/${id}/pay`, {});
  }

  // RF3: alterar quantidades/tipos de bilhete de uma reserva ainda pendente de pagamento
  update(id: string, request: UpdateReservationRequest): Observable<Reservation> {
    return this.http.put<Reservation>(`${this.baseUrl}/${id}`, request);
  }

  cancel(id: string, reason?: string): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.baseUrl}/${id}/cancel`, reason ? { reason } : {});
  }

  history(): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(`${this.baseUrl}/history`);
  }

  getById(id: string): Observable<Reservation> {
    return this.http.get<Reservation>(`${this.baseUrl}/${id}`);
  }
}
