import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { Role } from '../../../core/models/user.model';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: '../auth.shared.scss',
})
export class RegisterComponent {
  private fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedRole = signal<Role>('CLIENTE');

  form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  constructor(private auth: AuthService, private router: Router) {}

  chooseRole(role: Role): void {
    this.selectedRole.set(role);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const payload = { ...(this.form.getRawValue() as { name: string; email: string; password: string }), role: this.selectedRole() };

    this.auth.register(payload).subscribe({
      next: () => this.router.navigate(['/eventos']),
      error: (err) => {
        this.errorMessage.set(err?.error?.message ?? 'Não foi possível criar a conta.');
        this.loading.set(false);
      },
    });
  }
}
