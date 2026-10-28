import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { TranslatePipe } from '../../core/i18n/translate.pipe';

/**
 * Admin shell: a title, three tabs and the selected page below.
 * هيكل الإدارة: عنوان وثلاث تبويبات والصفحة المختارة تحتها.
 */
@Component({
  selector: 'app-admin',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>{{ 'admin.title' | t }}</h1>
    <nav class="tabs" [attr.aria-label]="'admin.title' | t">
      <a
        class="tab"
        routerLink="/admin"
        routerLinkActive="site-link--active"
        [routerLinkActiveOptions]="{ exact: true }"
        ariaCurrentWhenActive="page"
        data-testid="tab-stats"
        >{{ 'admin.tabs.stats' | t }}</a
      >
      <a
        class="tab"
        routerLink="/admin/users"
        routerLinkActive="site-link--active"
        ariaCurrentWhenActive="page"
        data-testid="tab-users"
        >{{ 'admin.tabs.users' | t }}</a
      >
      <a
        class="tab"
        routerLink="/admin/emails"
        routerLinkActive="site-link--active"
        ariaCurrentWhenActive="page"
        data-testid="tab-emails"
        >{{ 'admin.tabs.emails' | t }}</a
      >
    </nav>
    <router-outlet />
  `,
})
export class AdminPage {}
