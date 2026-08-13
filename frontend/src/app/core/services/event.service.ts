import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateEventRequest, EventItem } from '../models/event.model';

@Injectable({ providedIn: 'root' })
export class EventService {
  private readonly baseUrl = `${environment.apiUrl}/api/v1/events`;

  constructor(private http: HttpClient) {}

  listPublished(category?: string): Observable<EventItem[]> {
    let params = new HttpParams();
    if (category) {
      params = params.set('category', category);
    }
    return this.http.get<EventItem[]>(this.baseUrl, { params });
  }

  getById(id: string): Observable<EventItem> {
    return this.http.get<EventItem>(`${this.baseUrl}/${id}`);
  }

  listMine(): Observable<EventItem[]> {
    return this.http.get<EventItem[]>(`${this.baseUrl}/mine/list`);
  }

  create(request: CreateEventRequest): Observable<EventItem> {
    return this.http.post<EventItem>(this.baseUrl, request);
  }

  publish(id: string): Observable<EventItem> {
    return this.http.put<EventItem>(`${this.baseUrl}/${id}/publish`, {});
  }

  cancel(id: string): Observable<EventItem> {
    return this.http.put<EventItem>(`${this.baseUrl}/${id}/cancel`, {});
  }
}
