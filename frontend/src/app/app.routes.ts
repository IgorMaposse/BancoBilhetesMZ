import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'eventos', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'registar',
    loadComponent: () =>
      import('./features/auth/register/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'eventos',
    loadComponent: () =>
      import('./features/events/event-list/event-list.component').then((m) => m.EventListComponent),
  },
  {
    path: 'eventos/novo',
    canActivate: [authGuard, roleGuard(['ORGANIZADOR', 'ADMIN'])],
    loadComponent: () =>
      import('./features/events/event-create/event-create.component').then(
        (m) => m.EventCreateComponent
      ),
  },
  {
    path: 'eventos/:id',
    loadComponent: () =>
      import('./features/events/event-detail/event-detail.component').then(
        (m) => m.EventDetailComponent
      ),
  },
  {
    path: 'minhas-reservas',
    canActivate: [authGuard, roleGuard(['CLIENTE', 'ADMIN'])],
    loadComponent: () =>
      import('./features/reservations/reservation-history/reservation-history.component').then(
        (m) => m.ReservationHistoryComponent
      ),
  },
  { path: '**', redirectTo: 'eventos' },
];
