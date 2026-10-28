import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { errorMessage, toApiError } from '../../core/api/api-error';
import { Loadable } from '../../core/api/loadable';
import { EmployerJob } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { ToastService } from '../../core/ui/toast.service';
import { ErrorAlertComponent } from '../../shared/error-alert.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

/**
 * The employer's own jobs in every status, with publish, close and delete actions.
 * وظائف صاحب العمل بكل الحالات، مع النشر والإغلاق والحذف.
 */
@Component({
  selector: 'app-employer-jobs',
  imports: [RouterLink, TranslatePipe, ErrorAlertComponent, StatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <h1>{{ 'employer.jobs.title' | t }}</h1>
      <div class="page-header__actions">
        <a class="btn btn--primary" routerLink="/employer/jobs/new" data-testid="job-new">{{
          'employer.jobs.add' | t
        }}</a>
      </div>
    </div>
    @if (list.loading()) {
      <p class="loading"><span class="spinner"></span> {{ 'common.loading' | t }}</p>
    }
    <app-error-alert [error]="list.error()" />
    @if (list.value(); as jobs) {
      @if (jobs.length === 0) {
        <p class="empty-state" data-testid="jobs-empty">{{ 'employer.jobs.empty' | t }}</p>
      }
      <div class="table-wrap">
        @if (jobs.length > 0) {
          <table>
            <thead>
              <tr>
                <th>{{ 'employer.form.title' | t }}</th>
                <th>{{ 'admin.users.status' | t }}</th>
                <th>{{ 'employer.jobs.applicants' | t }}</th>
                <th>{{ 'admin.users.actions' | t }}</th>
              </tr>
            </thead>
            <tbody>
              @for (job of jobs; track job.id) {
                <tr data-testid="employer-job">
                  <td>
                    {{ job.title }}
                    <div class="muted">{{ job.city }} · {{ 'jobType.' + job.type | t }}</div>
                  </td>
                  <td><app-status-badge group="jobStatus" [value]="job.status" /></td>
                  <td class="num">
                    <a [routerLink]="['/employer/jobs', job.id, 'applicants']">{{
                      'employer.jobs.applicantCount'
                        | t: { count: format.number(job.applicantCount) }
                    }}</a>
                  </td>
                  <td>
                    <div class="inline-form">
                      @if (job.status !== 'CLOSED') {
                        <a
                          class="btn btn--small"
                          [routerLink]="['/employer/jobs', job.id, 'edit']"
                          >{{ 'common.edit' | t }}</a
                        >
                      }
                      @if (job.status === 'DRAFT') {
                        <button
                          type="button"
                          class="btn btn--small btn--primary"
                          (click)="publish(job)"
                          data-testid="job-publish"
                        >
                          {{ 'employer.jobs.publish' | t }}
                        </button>
                        <button
                          type="button"
                          class="btn btn--small btn--danger"
                          (click)="remove(job)"
                        >
                          {{ 'common.delete' | t }}
                        </button>
                      }
                      @if (job.status === 'PUBLISHED') {
                        <button
                          type="button"
                          class="btn btn--small"
                          (click)="close(job)"
                          data-testid="job-close"
                        >
                          {{ 'employer.jobs.close' | t }}
                        </button>
                      }
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
    }
  `,
})
export class EmployerJobsPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirmDialog = inject(ConfirmService);
  protected readonly format = inject(FormatService);

  protected readonly list = new Loadable<EmployerJob[]>();

  constructor() {
    this.reload();
  }

  protected async publish(job: EmployerJob): Promise<void> {
    await this.act(
      'publishTitle',
      'publishText',
      'employer.jobs.publish',
      false,
      () => this.api.post(`/employer/jobs/${job.id}/publish`),
      'published',
    );
  }

  protected async close(job: EmployerJob): Promise<void> {
    await this.act(
      'closeTitle',
      'closeText',
      'employer.jobs.close',
      true,
      () => this.api.post(`/employer/jobs/${job.id}/close`),
      'closed',
    );
  }

  protected async remove(job: EmployerJob): Promise<void> {
    await this.act(
      'deleteTitle',
      'deleteText',
      'common.delete',
      true,
      () => this.api.delete(`/employer/jobs/${job.id}`),
      'deleted',
    );
  }

  // Confirm, call the server, show a translated toast and refresh the list.
  // يطلب تأكيد، ينادي الخادم، يعرض إشعار مترجم، ويحدّث القائمة.
  private async act(
    title: string,
    text: string,
    confirmKey: string,
    danger: boolean,
    call: () => ReturnType<ApiService['post']>,
    done: string,
  ): Promise<void> {
    const ok = await this.confirmDialog.ask({
      title: this.i18n.t(`employer.jobs.${title}`),
      message: this.i18n.t(`employer.jobs.${text}`),
      confirmLabel: this.i18n.t(confirmKey),
      danger,
    });
    if (!ok) return;
    call().subscribe({
      next: () => {
        this.toast.success(this.i18n.t(`employer.jobs.${done}`));
        this.reload();
      },
      error: (err: unknown) => this.toast.error(errorMessage(this.i18n, toApiError(err))),
    });
  }

  private reload(): void {
    this.list.load(this.api.get<EmployerJob[]>('/employer/jobs'));
  }
}
