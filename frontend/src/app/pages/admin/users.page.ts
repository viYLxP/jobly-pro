import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../../core/api/api.service';
import { errorMessage, toApiError } from '../../core/api/api-error';
import { Loadable } from '../../core/api/loadable';
import { AdminUser, Page, ROLES, Role, USER_STATUSES, UserStatus } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { ToastService } from '../../core/ui/toast.service';
import { ErrorAlertComponent } from '../../shared/error-alert.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

/**
 * Users with search and filters, and suspend / restore actions. An admin cannot suspend their own account.
 * المستخدمون مع بحث وفلاتر وإيقاف / استعادة. الأدمن ما يقدر يوقف حسابه.
 */
@Component({
  selector: 'app-users',
  imports: [
    FormsModule,
    TranslatePipe,
    PaginationComponent,
    ErrorAlertComponent,
    StatusBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="toolbar" (ngSubmit)="apply()" role="search">
      <input
        name="q"
        type="search"
        class="search"
        maxlength="100"
        [(ngModel)]="qText"
        [placeholder]="'admin.users.search' | t"
        [attr.aria-label]="'admin.users.search' | t"
        data-testid="users-search"
      />
      <select
        name="role"
        [(ngModel)]="roleFilter"
        (ngModelChange)="apply()"
        [attr.aria-label]="'admin.users.role' | t"
      >
        <option value="">{{ 'admin.users.role' | t }}: {{ 'common.all' | t }}</option>
        @for (role of roles; track role) {
          <option [value]="role">{{ 'role.' + role | t }}</option>
        }
      </select>
      <select
        name="status"
        [(ngModel)]="statusFilter"
        (ngModelChange)="apply()"
        [attr.aria-label]="'admin.users.status' | t"
      >
        <option value="">{{ 'admin.users.status' | t }}: {{ 'common.all' | t }}</option>
        @for (status of statuses; track status) {
          <option [value]="status">{{ 'userStatus.' + status | t }}</option>
        }
      </select>
      <button type="submit" class="btn">{{ 'common.search' | t }}</button>
    </form>

    @if (list.loading()) {
      <p class="loading"><span class="spinner"></span> {{ 'common.loading' | t }}</p>
    }
    <app-error-alert [error]="list.error()" />
    @if (list.value(); as result) {
      @if (result.content.length === 0) {
        <p class="empty-state">{{ 'admin.users.empty' | t }}</p>
      } @else {
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ 'admin.users.name' | t }}</th>
                <th>{{ 'admin.users.email' | t }}</th>
                <th>{{ 'admin.users.role' | t }}</th>
                <th>{{ 'admin.users.status' | t }}</th>
                <th>{{ 'admin.users.joined' | t }}</th>
                <th>{{ 'admin.users.actions' | t }}</th>
              </tr>
            </thead>
            <tbody>
              @for (user of result.content; track user.id) {
                <tr data-testid="user-row">
                  <td>
                    {{ user.fullName }}
                    @if (user.companyName) {
                      <div class="muted">{{ user.companyName }}</div>
                    }
                  </td>
                  <td dir="ltr" class="cell-end">{{ user.email }}</td>
                  <td>{{ 'role.' + user.role | t }}</td>
                  <td><app-status-badge group="userStatus" [value]="user.status" /></td>
                  <td>{{ format.date(user.createdAt) }}</td>
                  <td>
                    @if (user.id !== auth.user()?.id) {
                      @if (user.status === 'SUSPENDED') {
                        <button
                          type="button"
                          class="btn btn--small"
                          (click)="restore(user)"
                          data-testid="user-restore"
                        >
                          {{ 'admin.users.restore' | t }}
                        </button>
                      } @else {
                        <button
                          type="button"
                          class="btn btn--small btn--danger"
                          (click)="suspend(user)"
                          data-testid="user-suspend"
                        >
                          {{ 'admin.users.suspend' | t }}
                        </button>
                      }
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
export class UsersPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirmDialog = inject(ConfirmService);
  protected readonly auth = inject(AuthService);
  protected readonly format = inject(FormatService);

  protected readonly roles = ROLES;
  protected readonly statuses = USER_STATUSES;
  protected readonly list = new Loadable<Page<AdminUser>>();
  protected readonly page = signal(0);

  protected qText = '';
  protected roleFilter: Role | '' = '';
  protected statusFilter: UserStatus | '' = '';

  constructor() {
    this.load(0);
  }

  protected apply(): void {
    this.load(0);
  }

  protected load(page: number): void {
    this.page.set(page);
    this.list.load(
      this.api.get<Page<AdminUser>>('/admin/users', {
        q: this.qText.trim(),
        role: this.roleFilter,
        status: this.statusFilter,
        page,
        size: 20,
      }),
    );
  }

  protected async suspend(user: AdminUser): Promise<void> {
    const ok = await this.confirmDialog.ask({
      title: this.i18n.t('admin.users.suspendTitle'),
      message: this.i18n.t('admin.users.suspendText'),
      confirmLabel: this.i18n.t('admin.users.suspend'),
      danger: true,
    });
    if (ok) this.change(user, 'suspend', 'admin.users.suspended');
  }

  protected async restore(user: AdminUser): Promise<void> {
    const ok = await this.confirmDialog.ask({
      title: this.i18n.t('admin.users.restoreTitle'),
      message: this.i18n.t('admin.users.restoreText'),
      confirmLabel: this.i18n.t('admin.users.restore'),
    });
    if (ok) this.change(user, 'restore', 'admin.users.restored');
  }

  private change(user: AdminUser, action: 'suspend' | 'restore', doneKey: string): void {
    this.api.post(`/admin/users/${user.id}/${action}`).subscribe({
      next: () => {
        this.toast.success(this.i18n.t(doneKey));
        this.load(this.page());
      },
      error: (err: unknown) => this.toast.error(errorMessage(this.i18n, toApiError(err))),
    });
  }
}
