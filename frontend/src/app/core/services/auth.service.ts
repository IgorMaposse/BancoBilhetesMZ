import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RegisterRequest, Role } from '../models/user.model';

const STORAGE_KEY = 'bancobilhetes.auth';

interface StoredAuth {
  token: string;
  userId: string;
  name: string;
  role: Role;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = `${environment.apiUrl}/api/v1/auth`;

  private readonly authState = signal<StoredAuth | null>(this.readFromStorage());

  readonly isAuthenticated = computed(() => this.authState() !== null);
  readonly currentUser = computed(() => this.authState());
  readonly role = computed(() => this.authState()?.role ?? null);

  constructor(private http: HttpClient) {}

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/login`, request)
      .pipe(tap((response) => this.persist(response)));
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/register`, request)
      .pipe(tap((response) => this.persist(response)));
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.authState.set(null);
  }

  getToken(): string | null {
    return this.authState()?.token ?? null;
  }

  private persist(response: AuthResponse): void {
    const stored: StoredAuth = {
      token: response.token,
      userId: response.userId,
      name: response.name,
      role: response.role,
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
    this.authState.set(stored);
  }

  private readFromStorage(): StoredAuth | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as StoredAuth;
    } catch {
      return null;
    }
  }
}
