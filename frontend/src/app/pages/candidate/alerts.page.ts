import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiService } from '../../core/api/api.service';
import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { Loadable } from '../../core/api/loadable';
import { JOB_TYPES, JobAlert, JobType } from '../../core/api/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { ToastService } from '../../core/ui/toast.service';
import { ErrorAlertComponent } from '../../shared/error-alert.component';

/**
 * Job alerts: a keyword, a city and/or a type. Each can be paused or deleted.
 * تنبيهات الوظائف: كلمة و/أو مدينة و/أو نوع. كل تنبيه يتوقف مؤقتًا أو ينحذف.
 */
@Component({
  selector: 'app-alerts',
  imports: [ReactiveFormsModule, TranslatePipe, ErrorAlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>{{ 'candidate.alerts.title' | t }}</h1>
    <p class="muted">{{ 'candidate.alerts.text' | t }}</p>

    <form class="card" [formGroup]="form" (ngSubmit)="add()" novalidate>
      <h2>{{ 'candidate.alerts.add' | t }}</h2>
      <app-error-alert [error]="error()" />
      @if (needOne()) {
        <p class="alert alert--warning" role="alert">{{ 'candidate.alerts.needOne' | t }}</p>
      }
      <div class="form-grid">
        <div class="field">
          <label for="keyword">{{ 'candidate.alerts.keyword' | t }}</label>
          <input
            id="keyword"
            maxlength="100"
            formControlName="keyword"
            data-testid="alert-keyword"
          />
        </div>
        <div class="field">
          <label for="city">{{ 'candidate.alerts.city' | t }}</label>
          <input id="city" maxlength="80" formControlName="city" data-testid="alert-city" />
        </div>
        <div class="field">
          <label for="type">{{ 'candidate.alerts.type' | t }}</label>
          <select id="type" formControlName="type">
            <option value="">{{ 'candidate.alerts.anyType' | t }}</option>
            @for (type of types; track type) {
              <option [value]="type">{{ 'jobType.' + type | t }}</option>
            }
          </select>
        </div>
      </div>
      <div class="form-actions">
        <button
          type="submit"
          class="btn btn--primary"
          [disabled]="saving()"
          data-testid="alert-add"
        >
          {{ 'candidate.alerts.add' | t }}
        </button>
      </div>
    </form>

    @if (list.loading()) {
      <p class="loading"><span class="spinner"></span> {{ 'common.loading' | t }}</p>
    }
    @if (list.value(); as alerts) {
      @if (alerts.length === 0) {
        <p class="empty-state" data-testid="alerts-empty">{{ 'candidate.alerts.empty' | t }}</p>
      }
      <ul class="job-list">
        @for (alert of alerts; track alert.id) {
          <li class="card" data-testid="alert-row">
            <div class="card__header">
              <h2>
                {{ alert.keyword || ('candidate.alerts.anything' | t) }}
              </h2>
              <span
                class="badge"
                [class]="'badge badge--' + (alert.active ? 'ACTIVE' : 'PENDING')"
                >{{
                  (alert.active ? 'candidate.alerts.active' : 'candidate.alerts.paused') | t
                }}</span
              >
            </div>
            <div class="job-meta">
              <span>{{ alert.city || ('candidate.alerts.anyCity' | t) }}</span>
              <span>{{
                alert.type ? ('jobType.' + alert.type | t) : ('candidate.alerts.anyType' | t)
              }}</span>
            </div>
            <div class="inline-form">
              <button type="button" class="btn btn--small" (click)="toggle(alert)">
                {{ (alert.active ? 'candidate.alerts.pause' : 'candidate.alerts.resume') | t }}
              </button>
              <button
                type="button"
                class="btn btn--small btn--danger"
                (click)="remove(alert)"
                data-testid="alert-delete"
              >
                {{ 'common.delete' | t }}
              </button>
            </div>
          </li>
        }
      </ul>
    }
  `,
})
export class AlertsPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirmDialog = inject(ConfirmService);

  protected readonly types = JOB_TYPES;
  protected readonly list = new Loadable<JobAlert[]>();
  protected readonly form = inject(NonNullableFormBuilder).group({
    keyword: ['', [Validators.maxLength(100)]],
    city: ['', [Validators.maxLength(80)]],
    type: ['' as JobType | ''],
  });
  protected readonly saving = signal(false);
  protected readonly needOne = signal(false);
  protected readonly error = signal<ApiError | null>(null);

  constructor() {
    this.reload();
  }

  protected add(): void {
    const value = this.form.getRawValue();
    const keyword = value.keyword.trim();
    const city = value.city.trim();
    this.error.set(null);
    // An alert with no filter would match every job, so at least one field is required.
    // تنبيه بدون أي فلتر يطابق كل الوظائف، فلازم حقل واحد على الأقل.
    this.needOne.set(!keyword && !city && !value.type);
    if (this.form.invalid || this.needOne()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.api
      .post<JobAlert>('/candidate/alerts', {
        keyword: keyword || null,
        city: city || null,
        type: value.type || null,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.form.reset();
          this.toast.success(this.i18n.t('candidate.alerts.created'));
          this.reload();
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(toApiError(err));
        },
      });
  }

  protected toggle(alert: JobAlert): void {
    this.api.patch<JobAlert>(`/candidate/alerts/${alert.id}`, { active: !alert.active }).subscribe({
      next: () => this.reload(),
      error: (err: unknown) => this.toast.error(errorMessage(this.i18n, toApiError(err))),
    });
  }

  protected async remove(alert: JobAlert): Promise<void> {
    const ok = await this.confirmDialog.ask({
      title: this.i18n.t('candidate.alerts.deleteTitle'),
      message: this.i18n.t('candidate.alerts.deleteText'),
      confirmLabel: this.i18n.t('common.delete'),
      danger: true,
    });
    if (!ok) return;
    this.api.delete(`/candidate/alerts/${alert.id}`).subscribe({
      next: () => {
        this.toast.success(this.i18n.t('candidate.alerts.deleted'));
        this.reload();
      },
      error: (err: unknown) => this.toast.error(errorMessage(this.i18n, toApiError(err))),
    });
  }

  private reload(): void {
    this.list.load(this.api.get<JobAlert[]>('/candidate/alerts'));
  }
}
