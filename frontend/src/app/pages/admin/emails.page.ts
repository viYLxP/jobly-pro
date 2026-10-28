import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { ApiService } from '../../core/api/api.service';
import { errorMessage, toApiError } from '../../core/api/api-error';
import { Loadable } from '../../core/api/loadable';
import { AdminEmail, OutboxStatus, Page } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { ToastService } from '../../core/ui/toast.service';
import { ErrorAlertComponent } from '../../shared/error-alert.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

const FILTERS: (OutboxStatus | '')[] = ['', 'FAILED', 'PENDING', 'SENT'];

/**
 * The email outbox. Emails that failed five times can be queued for another attempt.
 * صندوق الإيميلات الصادرة. الإيميل اللي فشل خمس مرات يقدر الأدمن يعيده للمحاولة.
 */
@Component({
  selector: 'app-emails',
  imports: [TranslatePipe, PaginationComponent, ErrorAlertComponent, StatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="tabs" role="group" [attr.aria-label]="'admin.emails.status' | t">
      @for (filter of filters; track filter) {
        <button
          type="button"
          class="tab"
          [attr.aria-pressed]="status() === filter"
          (click)="setStatus(filter)"
          [attr.data-testid]="'email-filter-' + (filter || 'ALL')"
        >
          {{ filter ? ('emailStatus.' + filter | t) : ('common.all' | t) }}
        </button>
      }
    </div>
    @if (list.loading()) {
      <p class="loading"><span class="spinner"></span> {{ 'common.loading' | t }}</p>
    }
    <app-error-alert [error]="list.error()" />
    @if (list.value(); as result) {
      @if (result.content.length === 0) {
        <p class="empty-state">{{ 'admin.emails.empty' | t }}</p>
      } @else {
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ 'admin.emails.recipient' | t }}</th>
                <th>{{ 'admin.emails.template' | t }}</th>
                <th>{{ 'admin.emails.status' | t }}</th>
                <th>{{ 'admin.emails.attempts' | t }}</th>
                <th>{{ 'admin.emails.error' | t }}</th>
                <th>{{ 'admin.emails.created' | t }}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (email of result.content; track email.id) {
                <tr data-testid="email-row">
                  <td dir="ltr" class="cell-end">{{ email.to }}</td>
                  <td>{{ 'template.' + email.template | t }}</td>
                  <td><app-status-badge group="emailStatus" [value]="email.status" /></td>
                  <td class="num">{{ email.attempts }}</td>
                  <td class="error-detail" dir="ltr">{{ email.lastError }}</td>
                  <td>{{ format.dateTime(email.createdAt) }}</td>
                  <td>
                    @if (email.status === 'FAILED') {
                      <button
                        type="button"
                        class="btn btn--small"
                        (click)="retry(email)"
                        data-testid="email-retry"
                      >
                        {{ 'admin.emails.retry' | t }}
                      </button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
      <app-pagination
        [page]="result.page"
        [totalPages]="result.totalPages"
        (pageChange)="load($event)"
      />
    }
  `,
})
export class EmailsPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  protected readonly format = inject(FormatService);

  protected readonly filters = FILTERS;
  protected readonly status = signal<OutboxStatus | ''>('');
  protected readonly page = signal(0);
  protected readonly list = new Loadable<Page<AdminEmail>>();

  constructor() {
    this.load(0);
  }

  protected setStatus(status: OutboxStatus | ''): void {
    this.status.set(status);
    this.load(0);
  }

  protected load(page: number): void {
    this.page.set(page);
    this.list.load(
      this.api.get<Page<AdminEmail>>('/admin/emails', { status: this.status(), page, size: 20 }),
    );
  }

  protected retry(email: AdminEmail): void {
    this.api.post(`/admin/emails/${email.id}/retry`).subscribe({
      next: () => {
        this.toast.success(this.i18n.t('admin.emails.retried'));
        this.load(this.page());
      },
      error: (err: unknown) => this.toast.error(errorMessage(this.i18n, toApiError(err))),
    });
  }
}
