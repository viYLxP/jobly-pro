import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiError, toApiError } from '../../core/api/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { PASSWORD_PATTERN, passwordsMatch } from '../../core/passwords';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { ErrorAlertComponent } from '../../shared/error-alert.component';

/**
 * Sign-up for candidates and employers. The server always answers "check your email", whether or not the email
 * already had an account, so this page cannot be used to find out who is registered.
 * التسجيل للمرشحين وأصحاب العمل. الخادم يرد دائمًا "شوف بريدك" سواء كان للإيميل حساب أو لا،
 * فهذي الصفحة ما تنفع لمعرفة مين مسجّل.
 */
@Component({
  selector: 'app-register',
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
      <h1>{{ 'auth.register.title' | t }}</h1>
      <app-error-alert [error]="error()" />
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field">
          <span class="field__label" id="role-label">{{ 'auth.iAm' | t }}</span>
          <div class="role-choice" role="radiogroup" aria-labelledby="role-label">
            <label>
              <input
                type="radio"
                formControlName="role"
                value="CANDIDATE"
                data-testid="role-candidate"
              />
              {{ 'auth.asCandidate' | t }}
            </label>
            <label>
              <input
                type="radio"
                formControlName="role"
                value="EMPLOYER"
                data-testid="role-employer"
              />
              {{ 'auth.asEmployer' | t }}
            </label>
          </div>
        </div>
        <div class="field">
          <label for="fullName">{{ 'auth.fullName' | t }}</label>
          <input id="fullName" maxlength="120" autocomplete="name" formControlName="fullName" />
          <app-field-error [control]="form.controls.fullName" [server]="fields()['fullName']" />
        </div>
        @if (form.controls.role.value === 'EMPLOYER') {
          <div class="field">
            <label for="companyName">{{ 'auth.companyName' | t }}</label>
            <input
              id="companyName"
              maxlength="150"
              autocomplete="organization"
              formControlName="companyName"
            />
            <app-field-error
              [control]="form.controls.companyName"
              [server]="fields()['companyName']"
            />
          </div>
        }
        <div class="field">
          <label for="email">{{ 'auth.email' | t }}</label>
          <input id="email" type="email" autocomplete="email" dir="ltr" formControlName="email" />
          <app-field-error [control]="form.controls.email" [server]="fields()['email']" />
        </div>
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
            [server]="fields()['password']"
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
            data-testid="register-submit"
          >
            {{ 'auth.register.submit' | t }}
          </button>
        </div>
      </form>
      <div class="auth-links">
        <a routerLink="/login">{{ 'auth.register.haveAccount' | t }}</a>
      </div>
    </section>
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      role: ['CANDIDATE' as 'CANDIDATE' | 'EMPLOYER'],
      fullName: ['', [Validators.required, Validators.maxLength(120)]],
      companyName: ['', [Validators.maxLength(150)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
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
  protected readonly fields = signal<Record<string, string>>({});

  constructor() {
    // The company name is required only for employers; switching the role updates the rule.
    // اسم الشركة مطلوب لأصحاب العمل بس؛ وتبديل الدور يحدّث القاعدة.
    this.form.controls.role.valueChanges.subscribe((role) => {
      const company = this.form.controls.companyName;
      company.setValidators(
        role === 'EMPLOYER'
          ? [Validators.required, Validators.maxLength(150)]
          : [Validators.maxLength(150)],
      );
      company.updateValueAndValidity();
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.fields.set({});
    const value = this.form.getRawValue();
    const email = value.email.trim();
    this.auth
      .register({
        email,
        password: value.password,
        fullName: value.fullName.trim(),
        role: value.role,
        companyName: value.companyName.trim() || null,
      })
      .subscribe({
        next: () => void this.router.navigate(['/check-email'], { queryParams: { email } }),
        error: (err: unknown) => {
          this.submitting.set(false);
          const apiError = toApiError(err);
          this.error.set(apiError);
          this.fields.set(apiError.fields);
        },
      });
  }
}
