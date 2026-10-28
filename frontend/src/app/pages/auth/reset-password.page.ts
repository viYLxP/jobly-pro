import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { ApiError, toApiError } from '../../core/api/api-error';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { PASSWORD_PATTERN, passwordsMatch } from '../../core/passwords';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { ErrorAlertComponent } from '../../shared/error-alert.component';

/**
 * Opened from the reset email: /reset-password#<token>. A used or expired link shows the server's error with a
 * link to request a new one.
 * يُفتح من إيميل الاستعادة: /reset-password#<token>. الرابط المستخدم أو المنتهي يعرض خطأ الخادم مع رابط لطلب جديد.
 */
@Component({
  selector: 'app-reset-password',
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
      <h1>{{ 'auth.reset.title' | t }}</h1>
      @if (!token) {
        <p class="alert alert--danger">{{ 'auth.reset.missingToken' | t }}</p>
        <a routerLink="/forgot-password">{{ 'auth.reset.requestNew' | t }}</a>
      } @else {
        <app-error-alert [error]="error()" />
        @if (error()?.code === 'TOKEN_INVALID' || error()?.code === 'TOKEN_EXPIRED') {
          <p>
            <a routerLink="/forgot-password" data-testid="request-new">{{
              'auth.reset.requestNew' | t
            }}</a>
          </p>
        }
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label for="password">{{ 'auth.password' | t }}</label>
            <input
              id="password"
              type="password"
              autocomplete="new-password"
              dir="ltr"
              formControlName="password"
            />
            <p class="hint">{{ 'auth.register.passwordHint' | t }}</p>
            <app-field-error
              [control]="form.controls.password"
              patternKey="validation.passwordRule"
            />
          </div>
          <div class="field">
            <label for="confirm">{{ 'auth.confirmPassword' | t }}</label>
            <input
              id="confirm"
              type="password"
              autocomplete="new-password"
              dir="ltr"
              formControlName="confirm"
            />
            <app-field-error [control]="form.controls.confirm" />
          </div>
          <div class="form-actions">
            <button
              type="submit"
              class="btn btn--primary btn--block"
              [disabled]="submitting()"
              data-testid="reset-submit"
            >
              {{ 'auth.reset.submit' | t }}
            </button>
          </div>
        </form>
      }
    </section>
  `,
})
export class ResetPasswordPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly token = location.hash.slice(1);
  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      password: [
        '',
        [
          Validators.required,
          Validators.minLength(8),
          Validators.maxLength(72),
          Validators.pattern(PASSWORD_PATTERN),
        ],
      ],
      confirm: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );
  protected readonly submitting = signal(false);
  protected readonly error = signal<ApiError | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.api
      .post('/auth/reset-password', {
        token: this.token,
        password: this.form.getRawValue().password,
      })
      .subscribe({
        next: () => void this.router.navigate(['/login'], { queryParams: { reset: 1 } }),
        error: (err: unknown) => {
          this.submitting.set(false);
          this.error.set(toApiError(err));
        },
      });
  }
}
