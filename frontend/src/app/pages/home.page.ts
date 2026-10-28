import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { Loadable } from '../core/api/loadable';
import { JobSummary, Page } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { FormatService } from '../core/i18n/format.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { IconComponent } from '../core/ui/icon.component';
import { salaryText } from '../shared/salary';

/**
 * Landing page: what the platform does and the five newest jobs.
 * الصفحة الرئيسية: وش تسوي المنصة وأحدث خمس وظائف.
 */
@Component({
  selector: 'app-home',
  imports: [RouterLink, TranslatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero">
      <span class="hero__eyebrow">{{ 'home.eyebrow' | t }}</span>
      <h1>{{ 'home.title' | t }}</h1>
      <p class="hero__lead">{{ 'home.lead' | t }}</p>
      <div class="hero__actions">
        <a class="btn btn--primary" routerLink="/jobs" data-testid="home-browse">{{
          'home.browse' | t
        }}</a>
        @if (!auth.user()) {
          <a class="btn" routerLink="/register">{{ 'home.createAccount' | t }}</a>
        }
      </div>
    </section>

    <section class="grid grid--stats" aria-label="features">
      <div class="card">
        <h2><app-icon name="shield" /> {{ 'home.featureEmail' | t }}</h2>
        <p class="muted">{{ 'home.featureEmailText' | t }}</p>
      </div>
      <div class="card">
        <h2><app-icon name="mail" /> {{ 'home.featureStatus' | t }}</h2>
        <p class="muted">{{ 'home.featureStatusText' | t }}</p>
      </div>
      <div class="card">
        <h2><app-icon name="send" /> {{ 'home.featureAlerts' | t }}</h2>
        <p class="muted">{{ 'home.featureAlertsText' | t }}</p>
      </div>
    </section>

    <section class="card">
      <div class="card__header">
        <h2>{{ 'home.latest' | t }}</h2>
        <a routerLink="/jobs">{{ 'home.viewAll' | t }}</a>
      </div>
      @if (latest.loading()) {
        <p class="loading"><span class="spinner"></span> {{ 'common.loading' | t }}</p>
      }
      @if (latest.value(); as page) {
        <ul class="job-list">
          @for (job of page.content; track job.id) {
            <li>
              <a class="job-card" [routerLink]="['/jobs', job.id]">
                <h3>{{ job.title }}</h3>
                <div class="job-meta">
                  <span>{{ job.companyName }}</span>
                  <span>{{ job.city }}</span>
                  <span>{{ 'jobType.' + job.type | t }}</span>
                  <span class="num">{{ salary(job) }}</span>
                </div>
              </a>
            </li>
          }
        </ul>
      }
    </section>
  `,
})
export class HomePage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly format = inject(FormatService);
  protected readonly auth = inject(AuthService);

  protected readonly latest = new Loadable<Page<JobSummary>>();

  constructor() {
    this.latest.load(this.api.get<Page<JobSummary>>('/jobs', { size: 5 }));
  }

  protected salary(job: JobSummary): string {
    return salaryText(this.i18n, this.format, job.salaryMin, job.salaryMax);
  }
}
