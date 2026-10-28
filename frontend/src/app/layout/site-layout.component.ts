import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { IconComponent } from '../core/ui/icon.component';

/**
 * The only layout: header with role-based links, the language switch, the page and a footer.
 * التخطيط الوحيد: هيدر بروابط حسب الدور، زر اللغة، الصفحة، وفوتر.
 */
@Component({
  selector: 'app-site-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="skip-link" href="#main">{{ 'app.skipToContent' | t }}</a>
    <header class="site-header">
      <a class="site-header__brand" routerLink="/">
        <span class="logo" aria-hidden="true">J</span>
        <strong>{{ 'app.name' | t }}</strong>
      </a>
      <nav class="site-header__nav" [attr.aria-label]="'app.nav' | t">
        <a
          class="site-link"
          routerLink="/jobs"
          routerLinkActive="site-link--active"
          ariaCurrentWhenActive="page"
          data-testid="nav-jobs"
          ><app-icon name="briefcase" /> <span>{{ 'nav.jobs' | t }}</span></a
        >
        @if (auth.isCandidate()) {
          <a
            class="site-link"
            routerLink="/candidate/applications"
            routerLinkActive="site-link--active"
            ariaCurrentWhenActive="page"
            data-testid="nav-applications"
            ><app-icon name="note" /> <span>{{ 'nav.myApplications' | t }}</span></a
          >
          <a
            class="site-link"
            routerLink="/candidate/profile"
            routerLinkActive="site-link--active"
            ariaCurrentWhenActive="page"
            ><app-icon name="user" /> <span>{{ 'nav.profile' | t }}</span></a
          >
          <a
            class="site-link"
            routerLink="/candidate/alerts"
            routerLinkActive="site-link--active"
            ariaCurrentWhenActive="page"
            data-testid="nav-alerts"
            ><app-icon name="mail" /> <span>{{ 'nav.alerts' | t }}</span></a
          >
        }
        @if (auth.isEmployer()) {
          <a
            class="site-link"
            routerLink="/employer/jobs"
            routerLinkActive="site-link--active"
            ariaCurrentWhenActive="page"
            data-testid="nav-my-jobs"
            ><app-icon name="building" /> <span>{{ 'nav.myJobs' | t }}</span></a
          >
        }
        @if (auth.isAdmin()) {
          <a
            class="site-link"
            routerLink="/admin"
            routerLinkActive="site-link--active"
            ariaCurrentWhenActive="page"
            data-testid="nav-admin"
            ><app-icon name="shield" /> <span>{{ 'nav.admin' | t }}</span></a
          >
        }
      </nav>
      <div class="site-header__tools">
        <button
          type="button"
          class="btn btn--small lang-switch"
          (click)="i18n.toggle()"
          [attr.aria-label]="'app.switchToLabel' | t"
          [attr.lang]="i18n.lang() === 'ar' ? 'en' : 'ar'"
          data-testid="lang-switch"
        >
          <app-icon name="globe" /> {{ 'app.switchTo' | t }}
        </button>
        @if (auth.user(); as user) {
          <span class="muted" data-testid="user-name">{{ user.fullName }}</span>
          <button type="button" class="btn btn--small" (click)="auth.logout()" data-testid="logout">
            <app-icon name="logout" /> {{ 'nav.logout' | t }}
          </button>
        } @else {
          <a class="btn btn--small" routerLink="/login" data-testid="nav-login">{{
            'nav.login' | t
          }}</a>
          <a
            class="btn btn--small btn--primary"
            routerLink="/register"
            data-testid="nav-register"
            >{{ 'nav.register' | t }}</a
          >
        }
      </div>
    </header>
    <main id="main" class="site-main" tabindex="-1">
      <router-outlet />
    </main>
    <footer class="site-footer">
      <p>{{ 'app.footer' | t }}</p>
    </footer>
  `,
})
export class SiteLayoutComponent {
  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(I18nService);
}
