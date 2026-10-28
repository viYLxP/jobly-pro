import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiService } from '../../core/api/api.service';
import { ApiError, toApiError } from '../../core/api/api-error';
import { StatusResponse } from '../../core/api/models';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { ErrorAlertComponent } from '../../shared/error-alert.component';

type Step = 'working' | 'verified' | 'already' | 'expired' | 'invalid' | 'sent';

/**
 * Opened from the activation email: /verify-email#<token>. The token is in the URL fragment (never sent to servers
 * or logged). An expired link offers to send a new one.
 * يُفتح من إيميل التفعيل: /verify-email#<token>. الرمز في الـ fragment (ما ينرسل للخوادم ولا ينسجّل).
 * الرابط المنتهي يعرض إرسال رابط جديد.
 */
@Component({
  selector: 'app-verify-email',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    TranslatePipe,
    FieldErrorComponent,
    ErrorAlertComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card page-narrow auth-card" aria-live="polite">
      <h1>{{ 'auth.verify.title' | t }}</h1>
      @switch (step()) {
        @case ('working') {
          <p class="loading"><span class="spinner"></span> {{ 'auth.verify.working' | t }}</p>
        }
        @case ('verified') {
          <p class="alert alert--success" data-testid="verify-done">{{ 'auth.verify.done' | t }}</p>
          <a class="btn btn--primary" routerLink="/login" [queryParams]="{ verified: 1 }">{{
            'auth.verify.goLogin' | t
          }}</a>
        }
        @case ('already') {
          <p class="alert alert--info" data-testid="verify-already">
            {{ 'auth.verify.already' | t }}
          </p>
          <a class="btn btn--primary" routerLink="/login">{{ 'auth.verify.goLogin' | t }}</a>
        }
        @case ('expired') {
          <p class="alert alert--warning" data-testid="verify-expired">
            {{ 'auth.verify.expired' | t }}
          </p>
          <app-error-alert [error]="error()" />
          <form [formGroup]="form" (ngSubmit)="requestNew()" novalidate>
            <div class="field">
              <label for="email">{{ 'auth.email' | t }}</label>
              <input id="email" type="email" dir="ltr" formControlName="email" />
              <app-field-error [control]="form.controls.email" />
            </div>
            <div class="form-actions">
              <button type="submit" class="btn btn--primary" [disabled]="sending()">
                {{ 'auth.verify.requestNew' | t }}
              </button>
            </div>
          </form>
        }
        @case ('sent') {
          <p class="alert alert--success">{{ 'auth.checkEmail.resent' | t }}</p>
        }
        @default {
          <p class="alert alert--danger" data-testid="verify-invalid">
            {{ 'auth.verify.invalid' | t }}
          </p>
          <app-error-alert [error]="error()" />
          <a class="btn" routerLink="/login">{{ 'auth.verify.goLogin' | t }}</a>
        }
      }
    </section>
  `,
})
export class VerifyEmailPage {
  private readonly api = inject(ApiService);

  protected readonly step = signal<Step>('working');
  protected readonly error = signal<ApiError | null>(null);
  protected readonly sending = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
  });

  constructor() {
    const token = location.hash.slice(1);
    if (!token) {
      this.step.set('invalid');
      return;
    }
    this.api.post<StatusResponse>('/auth/verify-email', { token }).subscribe({
      next: (response) =>
        this.step.set(response.status === 'ALREADY_VERIFIED' ? 'already' : 'verified'),
      error: (err: unknown) => {
        const apiError = toApiError(err);
        if (apiError.code === 'TOKEN_EXPIRED') {
          this.step.set('expired');
        } else {
          this.step.set('invalid');
          // Keep the message only for unexpected errors (network, rate limit); "invalid" already says it.
          // نخلي الرسالة فقط للأخطاء غير المتوقعة؛ حالة "غير صالح" تقولها بنفسها.
          if (apiError.code !== 'TOKEN_INVALID') this.error.set(apiError);
        }
      },
    });
  }

  protected requestNew(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.api
      .post('/auth/resend-verification', { email: this.form.getRawValue().email.trim() })
      .subscribe({
        next: () => this.step.set('sent'),
        error: (err: unknown) => {
          this.sending.set(false);
          this.error.set(toApiError(err));
        },
      });
  }
}
