import { ChangeDetectionStrategy, Component, inject, input, signal, effect } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { ApiError, toApiError } from '../../core/api/api-error';
import { EmployerJob, JOB_TYPES, JobRequest, JobType } from '../../core/api/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { ToastService } from '../../core/ui/toast.service';
import { ErrorAlertComponent } from '../../shared/error-alert.component';

// The maximum salary cannot be below the minimum (the server enforces the same rule).
// الحد الأعلى للراتب لا يقل عن الأدنى (والخادم يفرض نفس القاعدة).
function salaryRange(group: AbstractControl): ValidationErrors | null {
  const min = group.get('salaryMin')?.value as number | null;
  const max = group.get('salaryMax')?.value as number | null;
  return min !== null && max !== null && max < min ? { salaryRange: true } : null;
}

/**
 * Create a job (route /employer/jobs/new) or edit one (/employer/jobs/:id/edit). New jobs are saved as drafts.
 * إنشاء وظيفة أو تعديلها. الوظائف الجديدة تنحفظ كمسودة.
 */
@Component({
  selector: 'app-job-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    FieldErrorComponent,
    ErrorAlertComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="breadcrumb" routerLink="/employer/jobs">← {{ 'employer.jobs.title' | t }}</a>
    <h1>{{ (id() ? 'employer.form.editTitle' : 'employer.form.newTitle') | t }}</h1>
    <app-error-alert [error]="error()" />
    <form class="card" [formGroup]="form" (ngSubmit)="save()" novalidate>
      <div class="form-grid">
        <div class="field">
          <label for="title">{{ 'employer.form.title' | t }}</label>
          <input id="title" maxlength="150" formControlName="title" data-testid="job-title-input" />
          <app-field-error [control]="form.controls.title" [server]="fields()['title']" />
        </div>
        <div class="field">
          <label for="city">{{ 'employer.form.city' | t }}</label>
          <input id="city" maxlength="80" formControlName="city" data-testid="job-city-input" />
          <app-field-error [control]="form.controls.city" [server]="fields()['city']" />
        </div>
        <div class="field">
          <label for="type">{{ 'employer.form.type' | t }}</label>
          <select id="type" formControlName="type">
            @for (type of types; track type) {
              <option [value]="type">{{ 'jobType.' + type | t }}</option>
            }
          </select>
        </div>
        <div class="field">
          <label for="salaryMin">{{ 'employer.form.salaryMin' | t }}</label>
          <input id="salaryMin" type="number" min="0" dir="ltr" formControlName="salaryMin" />
          <app-field-error [control]="form.controls.salaryMin" [server]="fields()['salaryMin']" />
        </div>
        <div class="field">
          <label for="salaryMax">{{ 'employer.form.salaryMax' | t }}</label>
          <input id="salaryMax" type="number" min="0" dir="ltr" formControlName="salaryMax" />
          <app-field-error [control]="form.controls.salaryMax" [server]="fields()['salaryMax']" />
          @if (form.hasError('salaryRange') && form.controls.salaryMax.touched) {
            <p class="field__error" role="alert">{{ 'validation.salaryRange' | t }}</p>
          }
        </div>
      </div>
      <div class="field">
        <label for="description">{{ 'employer.form.description' | t }}</label>
        <textarea
          id="description"
          rows="10"
          maxlength="8000"
          formControlName="description"
          data-testid="job-description-input"
        ></textarea>
        <app-field-error [control]="form.controls.description" [server]="fields()['description']" />
      </div>
      <div class="form-actions">
        <a class="btn" routerLink="/employer/jobs">{{ 'common.cancel' | t }}</a>
        <button type="submit" class="btn btn--primary" [disabled]="saving()" data-testid="job-save">
          {{ (id() ? 'employer.form.saveChanges' : 'employer.form.saveDraft') | t }}
        </button>
      </div>
    </form>
  `,
})
export class JobFormPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(I18nService);

  // Present on /employer/jobs/:id/edit, absent on /employer/jobs/new.
  // موجود في مسار التعديل وغائب في مسار الإنشاء.
  readonly id = input<string | undefined>();

  protected readonly types = JOB_TYPES;
  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      title: ['', [Validators.required, Validators.maxLength(150)]],
      description: ['', [Validators.required, Validators.maxLength(8000)]],
      city: ['', [Validators.required, Validators.maxLength(80)]],
      type: ['FULL_TIME' as JobType, [Validators.required]],
      salaryMin: [null as number | null, [Validators.min(0)]],
      salaryMax: [null as number | null, [Validators.min(0)]],
    },
    { validators: salaryRange },
  );
  protected readonly saving = signal(false);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly fields = signal<Record<string, string>>({});

  constructor() {
    effect(() => {
      const id = this.id();
      if (!id) return;
      this.api.get<EmployerJob>(`/employer/jobs/${id}`).subscribe({
        next: (job) =>
          this.form.setValue({
            title: job.title,
            description: job.description,
            city: job.city,
            type: job.type,
            salaryMin: job.salaryMin,
            salaryMax: job.salaryMax,
          }),
        error: (err: unknown) => this.error.set(toApiError(err)),
      });
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.fields.set({});
    const value = this.form.getRawValue();
    const body: JobRequest = {
      title: value.title.trim(),
      description: value.description.trim(),
      city: value.city.trim(),
      type: value.type,
      salaryMin: value.salaryMin,
      salaryMax: value.salaryMax,
    };
    const id = this.id();
    const request = id
      ? this.api.put<EmployerJob>(`/employer/jobs/${id}`, body)
      : this.api.post<EmployerJob>('/employer/jobs', body);
    request.subscribe({
      next: () => {
        this.toast.success(this.i18n.t(id ? 'common.saved' : 'employer.form.created'));
        void this.router.navigateByUrl('/employer/jobs');
      },
      error: (err: unknown) => {
        this.saving.set(false);
        const apiError = toApiError(err);
        this.error.set(apiError);
        this.fields.set(apiError.fields);
      },
    });
  }
}
