import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiError, toApiError } from '../../core/api/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { ErrorAlertComponent } from '../../shared/error-alert.component';

/**
 * Sign-in page. After the right password the server may still refuse: EMAIL_NOT_VERIFIED (we offer a new link)
 * or ACCOUNT_SUSPENDED.
 * صفحة الدخول. حتى بعد كلمة مرور صحيحة ممكن يرفض الخادم: EMAIL_NOT_VERIFIED (نعرض رابط جديد) أو ACCOUNT_SUSPENDED.
 */
@Component({
  selector: 'app-login',
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
      <h1>{{ 'auth.login.title' | t }}</h1>
      @if (expired()) {
        <p class="alert alert--warning">{{ 'auth.login.expired' | t }}</p>
      }
      @if (verified()) {
        <p class="alert alert--success" data-testid="verified-note">
          {{ 'auth.login.verifiedNow' | t }}
        </p>
      }
      @if (reset()) {
        <p class="alert alert--success" data-testid="reset-note">
          {{ 'auth.login.resetDone' | t }}
        </p>
      }
      <app-error-alert [error]="error()" />
      @if (error()?.code === 'EMAIL_NOT_VERIFIED') {
        <p>
          <a [routerLink]="['/check-email']" [queryParams]="{ email: form.controls.email.value }">{{
            'auth.login.resend' | t
          }}</a>
        </p>
      }
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field">
          <label for="email">{{ 'auth.email' | t }}</label>
          <input
            id="email"
            type="email"
            autocomplete="username"
            dir="ltr"
            formControlName="email"
          />
          <app-field-error [control]="form.controls.email" />
        </div>
        <div class="field">
          <label for="password">{{ 'auth.password' | t }}</label>
          <input
            id="password"
            type="password"
            autocomplete="current-password"
            dir="ltr"
            formControlName="password"
          />
          <app-field-error [control]="form.controls.password" />
        </div>
        <div class="form-actions">
          <button
            type="submit"
            class="btn btn--primary btn--block"
            [disabled]="submitting()"
            data-testid="login-submit"
          >
            {{ 'auth.login.submit' | t }}
          </button>
        </div>
      </form>
      <div class="auth-links">
        <a routerLink="/forgot-password" data-testid="forgot-link">{{ 'auth.login.forgot' | t }}</a>
        <a routerLink="/register">{{ 'auth.login.noAccount' | t }}</a>
      </div>
    </section>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  // Flags set in the URL by other pages: ?expired=1 (session ended), ?verified=1, ?reset=1.
  // علامات تضعها صفحات ثانية في الرابط.
  readonly expired = input<string | undefined>();
  readonly verified = input<string | undefined>();
  readonly reset = input<string | undefined>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.maxLength(200)]],
  });
  protected readonly submitting = signal(false);
  // The raw error is stored, not the text, so the message re-translates when the language changes.
  // نخزّن الخطأ الخام مو النص، حتى تتترجم الرسالة من جديد لو تغيّرت اللغة.
  protected readonly error = signal<ApiError | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email.trim(), password).subscribe({
      next: () => void this.router.navigateByUrl(this.auth.home()),
      error: (err: unknown) => {
        this.submitting.set(false);
        this.error.set(toApiError(err));
      },
    });
  }
}
