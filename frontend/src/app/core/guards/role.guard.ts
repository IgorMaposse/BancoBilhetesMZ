import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Uso: { path: '...', canActivate: [roleGuard(['ORGANIZADOR', 'ADMIN'])] } */
export function roleGuard(allowedRoles: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const role = auth.role();

    if (role && allowedRoles.includes(role)) {
      return true;
    }

    router.navigate(['/eventos']);
    return false;
  };
}
