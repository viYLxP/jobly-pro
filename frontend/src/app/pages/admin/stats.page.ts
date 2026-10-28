import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ApiService } from '../../core/api/api.service';
import { Loadable } from '../../core/api/loadable';
import { Stats } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ErrorAlertComponent } from '../../shared/error-alert.component';

interface Group {
  titleKey: string;
  prefix: string;
  counts: Record<string, number>;
}

/**
 * Platform health: counts of users, jobs, applications and emails by status. A failed email count is highlighted.
 * صحة المنصة: أعداد المستخدمين والوظائف والطلبات والإيميلات حسب الحالة. عدد الإيميلات الفاشلة يظهر بلون تحذير.
 */
@Component({
  selector: 'app-stats',
  imports: [TranslatePipe, ErrorAlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (stats.loading()) {
      <p class="loading"><span class="spinner"></span> {{ 'common.loading' | t }}</p>
    }
    <app-error-alert [error]="stats.error()" />
    @if (stats.value(); as s) {
      <div class="grid grid--stats">
        <div class="stat-card">
          <div class="stat-card__label">{{ 'admin.stats.alerts' | t }}</div>
          <div class="stat-card__value num" data-testid="stat-alerts">
            {{ format.number(s.activeAlerts) }}
          </div>
        </div>
        <div class="stat-card" [class.stat-card--danger]="failed(s) > 0">
          <div class="stat-card__label">{{ 'emailStatus.FAILED' | t }}</div>
          <div class="stat-card__value num" data-testid="stat-failed">
            {{ format.number(failed(s)) }}
          </div>
        </div>
      </div>
      <div class="grid grid--2">
        @for (group of groups(s); track group.titleKey) {
          <section class="card">
            <h2>{{ group.titleKey | t }}</h2>
            <dl class="detail-list">
              @for (entry of entries(group); track entry[0]) {
                <dt>{{ group.prefix + '.' + entry[0] | t }}</dt>
                <dd class="num">{{ format.number(entry[1]) }}</dd>
              }
            </dl>
          </section>
        }
      </div>
    }
  `,
})
export class StatsPage {
  private readonly api = inject(ApiService);
  protected readonly format = inject(FormatService);
  protected readonly stats = new Loadable<Stats>();

  constructor() {
    this.stats.load(this.api.get<Stats>('/admin/stats'));
  }

  // Record lookups are typed as always present, but a status with no emails may be missing from the map.
  // البحث في الـ Record نوعه دائمًا موجود، لكن حالة بدون إيميلات ممكن تغيب من الخريطة.
  protected failed(s: Stats): number {
    const count: number | undefined = s.emailsByStatus['FAILED'];
    return count ?? 0;
  }

  protected groups(s: Stats): Group[] {
    return [
      { titleKey: 'admin.stats.users', prefix: 'userStatus', counts: s.usersByStatus },
      { titleKey: 'admin.users.role', prefix: 'role', counts: s.usersByRole },
      { titleKey: 'admin.stats.jobs', prefix: 'jobStatus', counts: s.jobsByStatus },
      { titleKey: 'admin.stats.applications', prefix: 'appStatus', counts: s.applicationsByStatus },
      { titleKey: 'admin.stats.emails', prefix: 'emailStatus', counts: s.emailsByStatus },
    ];
  }

  protected entries(group: Group): [string, number][] {
    return Object.entries(group.counts);
  }
}
