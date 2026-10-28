import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiService } from '../../core/api/api.service';
import { ApiError, toApiError } from '../../core/api/api-error';
import { Profile } from '../../core/api/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { ToastService } from '../../core/ui/toast.service';
import { ErrorAlertComponent } from '../../shared/error-alert.component';

/**
 * The profile employers see next to an application.
 * الملف اللي يشوفه صاحب العمل مع الطلب.
 */
@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, TranslatePipe, FieldErrorComponent, ErrorAlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>{{ 'candidate.profile.title' | t }}</h1>
    <p class="muted">{{ 'candidate.profile.text' | t }}</p>
    <app-error-alert [error]="error()" />
    <form class="card" [formGroup]="form" (ngSubmit)="save()" novalidate>
      <div class="form-grid">
        <div class="field">
          <label for="headline">{{ 'candidate.profile.headline' | t }}</label>
          <input
            id="headline"
            maxlength="150"
            formControlName="headline"
            [placeholder]="'candidate.profile.headlinePlaceholder' | t"
          />
          <app-field-error [control]="form.controls.headline" />
        </div>
        <div class="field">
          <label for="city">{{ 'candidate.profile.city' | t }}</label>
          <input id="city" maxlength="80" formControlName="city" />
          <app-field-error [control]="form.controls.city" />
        </div>
        <div class="field">
          <label for="years">{{ 'candidate.profile.years' | t }}</label>
          <input id="years" type="number" min="0" max="60" dir="ltr" formControlName="years" />
          <app-field-error [control]="form.controls.years" />
        </div>
      </div>
      <div class="field">
        <label for="summary">{{ 'candidate.profile.summary' | t }}</label>
        <textarea id="summary" rows="6" maxlength="2000" formControlName="summary"></textarea>
        <app-field-error [control]="form.controls.summary" />
      </div>
      <div class="form-actions">
        <button
          type="submit"
          class="btn btn--primary"
          [disabled]="saving()"
          data-testid="profile-save"
        >
          {{ 'common.save' | t }}
        </button>
      </div>
    </form>
  `,
})
export class ProfilePage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(I18nService);

  protected readonly form = inject(NonNullableFormBuilder).group({
    headline: ['', [Validators.maxLength(150)]],
    city: ['', [Validators.maxLength(80)]],
    years: [null as number | null, [Validators.min(0), Validators.max(60)]],
    summary: ['', [Validators.maxLength(2000)]],
  });
  protected readonly saving = signal(false);
  protected readonly error = signal<ApiError | null>(null);

  constructor() {
    this.api.get<Profile>('/candidate/profile').subscribe({
      next: (profile) =>
        this.form.setValue({
          headline: profile.headline ?? '',
          city: profile.city ?? '',
          years: profile.yearsExperience,
          summary: profile.summary ?? '',
        }),
      error: (err: unknown) => this.error.set(toApiError(err)),
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    const body: Profile = {
      headline: value.headline.trim() || null,
      city: value.city.trim() || null,
      yearsExperience: value.years,
      summary: value.summary.trim() || null,
    };
    this.api.put<Profile>('/candidate/profile', body).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(this.i18n.t('common.saved'));
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(toApiError(err));
      },
    });
  }
}
