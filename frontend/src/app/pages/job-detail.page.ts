import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { ApiError, toApiError } from '../core/api/api-error';
import { Loadable } from '../core/api/loadable';
import { JobDetail, MyApplication } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { FormatService } from '../core/i18n/format.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../core/ui/field-error.component';
import { ErrorAlertComponent } from '../shared/error-alert.component';
import { salaryText } from '../shared/salary';
import { StatusBadgeComponent } from '../shared/status-badge.component';

/**
 * One job with its full description and, for a signed-in candidate, the apply form.
 * وظيفة واحدة بوصفها الكامل، ولمرشح مسجّل دخول نموذج التقديم.
 */
@Component({
  selector: 'app-job-detail',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    FieldErrorComponent,
    ErrorAlertComponent,
    StatusBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="breadcrumb" routerLink="/jobs">← {{ 'nav.jobs' | t }}</a>
    @if (job.loading()) {
      <p class="loading"><span class="spinner"></span> {{ 'common.loading' | t }}</p>
    }
    @if (job.error(); as err) {
      @if (err.status === 404) {
        <p class="empty-state" data-testid="job-missing">{{ 'job.notFound' | t }}</p>
      } @else {
        <app-error-alert [error]="err" />
      }
    }
    @if (job.value(); as detail) {
      <article class="card">
        <h1 data-testid="job-title">{{ detail.title }}</h1>
        <div class="job-meta">
          <span>{{ detail.companyName }}</span>
          <span>{{ detail.city }}</span>
          <span>{{ 'jobType.' + detail.type | t }}</span>
          <span class="num">{{ salary(detail) }}</span>
          <span>{{ 'jobs.posted' | t: { date: format.date(detail.publishedAt) } }}</span>
        </div>
        <h2>{{ 'job.about' | t }}</h2>
        <p class="job-description">{{ detail.description }}</p>
      </article>

      <section class="card" aria-live="polite">
        @if (submitted(); as application) {
          <p class="alert alert--success" data-testid="apply-success">{{ 'job.success' | t }}</p>
          <p>
            <app-status-badge group="appStatus" [value]="application.status" />
            <a routerLink="/candidate/applications">{{ 'job.viewApplication' | t }}</a>
          </p>
        } @else if (detail.status === 'CLOSED') {
          <p class="alert alert--info">{{ 'job.closed' | t }}</p>
        } @else if (!auth.user()) {
          <p>{{ 'job.signInToApply' | t }}</p>
          <p>
            <a class="btn btn--primary" routerLink="/login" data-testid="apply-login">{{
              'nav.login' | t
            }}</a>
            <a class="btn" routerLink="/register">{{ 'nav.register' | t }}</a>
          </p>
        } @else if (!auth.isCandidate()) {
          <p class="muted">{{ 'job.employerCannotApply' | t }}</p>
        } @else {
          <h2>{{ 'job.apply' | t }}</h2>
          <app-error-alert [error]="applyError()" />
          @if (applyError()?.code === 'ALREADY_APPLIED') {
            <p>
              <a routerLink="/candidate/applications">{{ 'job.viewApplication' | t }}</a>
            </p>
          }
          <form [formGroup]="form" (ngSubmit)="apply(detail.id)" novalidate>
            <div class="field">
              <label for="cover">{{ 'job.coverNote' | t }}</label>
              <textarea id="cover" rows="5" maxlength="2000" formControlName="coverNote"></textarea>
              <p class="hint">{{ 'job.coverNoteHint' | t }}</p>
              <app-field-error [control]="form.controls.coverNote" />
            </div>
            <div class="form-actions">
              <button
                type="submit"
                class="btn btn--primary"
                [disabled]="submitting()"
                data-testid="apply-submit"
              >
                {{ 'job.submit' | t }}
              </button>
            </div>
          </form>
        }
      </section>
    }
  `,
})
export class JobDetailPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  protected readonly format = inject(FormatService);
  protected readonly auth = inject(AuthService);

  // The :id from the URL (withComponentInputBinding).
  // الـ :id من الرابط (withComponentInputBinding).
  readonly id = input.required<string>();

  protected readonly job = new Loadable<JobDetail>();
  protected readonly form = inject(NonNullableFormBuilder).group({
    coverNote: ['', [Validators.maxLength(2000)]],
  });
  protected readonly submitting = signal(false);
  protected readonly applyError = signal<ApiError | null>(null);
  protected readonly submitted = signal<MyApplication | null>(null);

  constructor() {
    effect(() => {
      this.submitted.set(null);
      this.applyError.set(null);
      this.job.load(this.api.get<JobDetail>(`/jobs/${this.id()}`));
    });
  }

  protected salary(job: JobDetail): string {
    return salaryText(this.i18n, this.format, job.salaryMin, job.salaryMax);
  }

  protected apply(jobId: number): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.applyError.set(null);
    const note = this.form.getRawValue().coverNote.trim();
    this.api
      .post<MyApplication>(`/candidate/jobs/${jobId}/applications`, { coverNote: note || null })
      .subscribe({
        next: (application) => {
          this.submitting.set(false);
          this.submitted.set(application);
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          this.applyError.set(toApiError(err));
        },
      });
  }
}
