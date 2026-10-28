import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { ApiError, toApiError } from '../../core/api/api-error';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { ErrorAlertComponent } from '../../shared/error-alert.component';

/**
 * Asks for the email and sends a reset link. The answer is the same whether or not the account exists.
 * يطلب الإيميل ويرسل رابط الاستعادة. الرد نفسه سواء كان الحساب موجود أو لا.
 */
@Component({
  selector: 'app-forgot-password',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    FieldErrorComponent,
    ErrorAlertComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card page-narrow auth-card">
      <h1>{{ 'auth.forgot.title' | t }}</h1>
      @if (sentTo(); as email) {
        <p class="alert alert--success" role="status" data-testid="forgot-sent">
          {{ 'auth.forgot.sent' | t: { email } }}
        </p>
      } @else {
        <p>{{ 'auth.forgot.text' | t }}</p>
        <app-error-alert [error]="error()" />
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label for="email">{{ 'auth.email' | t }}</label>
            <input id="email" type="email" autocomplete="email" dir="ltr" formControlName="email" />
            <app-field-error [control]="form.controls.email" />
          </div>
          <div class="form-actions">
            <button
              type="submit"
              class="btn btn--primary btn--block"
              [disabled]="submitting()"
              data-testid="forgot-submit"
            >
              {{ 'auth.forgot.submit' | t }}
            </button>
          </div>
        </form>
      }
      <div class="auth-links">
        <a routerLink="/login">{{ 'auth.checkEmail.back' | t }}</a>
      </div>
    </section>
  `,
})
export class ForgotPasswordPage {
  private readonly api = inject(ApiService);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });
  protected readonly submitting = signal(false);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly sentTo = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const email = this.form.getRawValue().email.trim();
    this.api.post('/auth/forgot-password', { email }).subscribe({
      next: () => this.sentTo.set(email),
      error: (err: unknown) => {
        this.submitting.set(false);
        this.error.set(toApiError(err));
      },
    });
  }
}
