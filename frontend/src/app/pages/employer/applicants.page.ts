import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { errorMessage, toApiError } from '../../core/api/api-error';
import { Loadable } from '../../core/api/loadable';
import {
  APPLICATION_STATUSES,
  Applicant,
  ApplicationStatus,
  EmployerJob,
  Page,
} from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { ToastService } from '../../core/ui/toast.service';
import { ErrorAlertComponent } from '../../shared/error-alert.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

// Allowed next steps, the same table as the backend's ApplicationStatus.next(). The server checks it again.
// الخطوات التالية المسموحة، نفس جدول ApplicationStatus.next() في الخادم. والخادم يفحصها مرة ثانية.
const NEXT: Record<ApplicationStatus, ApplicationStatus[]> = {
  SUBMITTED: ['REVIEWING', 'REJECTED'],
  REVIEWING: ['INTERVIEW', 'REJECTED'],
  INTERVIEW: ['OFFER', 'REJECTED'],
  OFFER: [],
  REJECTED: [],
};

/**
 * The people who applied to one job, filterable by status. Moving an application emails the candidate.
 * الناس اللي قدّموا على وظيفة واحدة، مع فلتر بالحالة. نقل الطلب يرسل إيميل للمرشح.
 */
@Component({
  selector: 'app-applicants',
  imports: [
    RouterLink,
    TranslatePipe,
    PaginationComponent,
    ErrorAlertComponent,
    StatusBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="breadcrumb" routerLink="/employer/jobs">← {{ 'employer.jobs.title' | t }}</a>
    <h1>
      {{ 'employer.applicants.title' | t }}
      @if (job.value(); as j) {
        · {{ j.title }}
      }
    </h1>

    <div class="tabs" role="group" [attr.aria-label]="'admin.users.status' | t">
      <button
        type="button"
        class="tab"
        [attr.aria-pressed]="status() === ''"
        (click)="setStatus('')"
      >
        {{ 'common.all' | t }}
      </button>
      @for (s of statuses; track s) {
        <button
          type="button"
          class="tab"
          [attr.aria-pressed]="status() === s"
          (click)="setStatus(s)"
          [attr.data-testid]="'filter-' + s"
        >
          {{ 'appStatus.' + s | t }}
        </button>
      }
    </div>

    @if (list.loading()) {
      <p class="loading"><span class="spinner"></span> {{ 'common.loading' | t }}</p>
    }
    <app-error-alert [error]="list.error()" />
    @if (list.value(); as result) {
      @if (result.content.length === 0) {
        <p class="empty-state" data-testid="applicants-empty">
          {{ 'employer.applicants.empty' | t }}
        </p>
      }
      @for (applicant of result.content; track applicant.id) {
        <article class="card application" data-testid="applicant">
          <div class="card__header">
            <h2>{{ applicant.candidate.fullName }}</h2>
            <app-status-badge group="appStatus" [value]="applicant.status" />
          </div>
          <div class="job-meta">
            <a [href]="'mailto:' + applicant.candidate.email" dir="ltr">{{
              applicant.candidate.email
            }}</a>
            @if (applicant.candidate.headline) {
              <span>{{ applicant.candidate.headline }}</span>
            }
            @if (applicant.candidate.city) {
              <span>{{ applicant.candidate.city }}</span>
            }
            @if (applicant.candidate.yearsExperience !== null) {
              <span>{{
                'employer.applicants.years' | t: { count: applicant.candidate.yearsExperience }
              }}</span>
            }
            <span>{{
              'employer.applicants.applied' | t: { date: format.date(applicant.createdAt) }
            }}</span>
          </div>
          @if (applicant.candidate.summary) {
            <p class="cover-note">{{ applicant.candidate.summary }}</p>
          }
          <p class="cover-note">
            {{ applicant.coverNote || ('employer.applicants.noNote' | t) }}
          </p>
          <div class="inline-form">
            @if (next(applicant.status).length === 0) {
              <span class="muted">{{ 'employer.applicants.final' | t }}</span>
            } @else {
              <span class="muted">{{ 'employer.applicants.moveTo' | t }}</span>
              @for (target of next(applicant.status); track target) {
                <button
                  type="button"
                  class="btn btn--small"
                  [class.btn--danger]="target === 'REJECTED'"
                  [disabled]="busy() === applicant.id"
                  (click)="move(applicant, target)"
                  [attr.data-testid]="'move-' + target"
                >
                  {{ 'appStatus.' + target | t }}
                </button>
              }
            }
          </div>
        </article>
      }
      <app-pagination
        [page]="result.page"
        [totalPages]="result.totalPages"
        (pageChange)="goTo($event)"
      />
    }
  `,
})
export class ApplicantsPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  protected readonly format = inject(FormatService);

  readonly id = input.required<string>();

  protected readonly statuses = APPLICATION_STATUSES;
  protected readonly job = new Loadable<EmployerJob>();
  protected readonly list = new Loadable<Page<Applicant>>();
  protected readonly status = signal<ApplicationStatus | ''>('');
  protected readonly page = signal(0);
  protected readonly busy = signal<number | null>(null);

  constructor() {
    effect(() => this.job.load(this.api.get<EmployerJob>(`/employer/jobs/${this.id()}`)));
    effect(() => this.load(this.id(), this.status(), this.page()));
  }

  protected next(status: ApplicationStatus): ApplicationStatus[] {
    return NEXT[status];
  }

  protected setStatus(status: ApplicationStatus | ''): void {
    this.page.set(0);
    this.status.set(status);
  }

  protected goTo(page: number): void {
    this.page.set(page);
  }

  protected move(applicant: Applicant, target: ApplicationStatus): void {
    this.busy.set(applicant.id);
    this.api
      .patch<Applicant>(`/employer/applications/${applicant.id}/status`, { status: target })
      .subscribe({
        next: () => {
          this.busy.set(null);
          this.toast.success(this.i18n.t('employer.applicants.moved'));
          this.load(this.id(), this.status(), this.page());
        },
        error: (err: unknown) => {
          this.busy.set(null);
          this.toast.error(errorMessage(this.i18n, toApiError(err)));
          this.load(this.id(), this.status(), this.page());
        },
      });
  }

  private load(id: string, status: ApplicationStatus | '', page: number): void {
    this.list.load(
      this.api.get<Page<Applicant>>(`/employer/jobs/${id}/applications`, {
        status,
        page,
        size: 20,
      }),
    );
  }
}
