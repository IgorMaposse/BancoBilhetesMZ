export type Role = 'CLIENTE' | 'ORGANIZADOR' | 'ADMIN';

export interface AuthResponse {
  token: string;
  userId: string;
  name: string;
  role: Role;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  role: Role;
}

export interface LoginRequest {
  email: string;
  password: string;
}
