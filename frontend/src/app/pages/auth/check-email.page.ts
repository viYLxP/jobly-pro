import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { ApiError, toApiError } from '../../core/api/api-error';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ErrorAlertComponent } from '../../shared/error-alert.component';

/**
 * "Check your email" after sign-up. The resend button is rate limited by the server (cooldown and a daily limit).
 * "شوف بريدك" بعد التسجيل. زر إعادة الإرسال محدود من الخادم (فترة انتظار وحد يومي).
 */
@Component({
  selector: 'app-check-email',
  imports: [RouterLink, TranslatePipe, ErrorAlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card page-narrow auth-card">
      <h1>{{ 'auth.checkEmail.title' | t }}</h1>
      <p data-testid="check-email-text">
        {{ 'auth.checkEmail.text' | t: { email: email() ?? '' } }}
      </p>
      @if (resent()) {
        <p class="alert alert--success" role="status">{{ 'auth.checkEmail.resent' | t }}</p>
      }
      <app-error-alert [error]="error()" />
      <div class="form-actions">
        @if (email()) {
          <button
            type="button"
            class="btn"
            [disabled]="working()"
            (click)="resend()"
            data-testid="resend"
          >
            {{ 'auth.checkEmail.resend' | t }}
          </button>
        }
        <a class="btn" routerLink="/login">{{ 'auth.checkEmail.back' | t }}</a>
      </div>
    </section>
  `,
})
export class CheckEmailPage {
  private readonly api = inject(ApiService);

  readonly email = input<string | undefined>();
  protected readonly working = signal(false);
  protected readonly resent = signal(false);
  protected readonly error = signal<ApiError | null>(null);

  protected resend(): void {
    const email = this.email();
    if (!email) return;
    this.working.set(true);
    this.error.set(null);
    this.resent.set(false);
    this.api.post('/auth/resend-verification', { email }).subscribe({
      next: () => {
        this.working.set(false);
        this.resent.set(true);
      },
      error: (err: unknown) => {
        this.working.set(false);
        this.error.set(toApiError(err));
      },
    });
  }
}
