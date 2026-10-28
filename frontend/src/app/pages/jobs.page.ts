import { ChangeDetectionStrategy, Component, effect, inject, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { Loadable } from '../core/api/loadable';
import { JOB_TYPES, JobSummary, Page } from '../core/api/models';
import { FormatService } from '../core/i18n/format.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { IconComponent } from '../core/ui/icon.component';
import { PaginationComponent } from '../core/ui/pagination.component';
import { ErrorAlertComponent } from '../shared/error-alert.component';
import { salaryText } from '../shared/salary';

/**
 * Public job search. The filters live in the URL (?q=&city=&type=&page=), so a search can be shared or bookmarked
 * and the back button works.
 * بحث الوظائف العام. الفلاتر داخل الرابط، فيمكن مشاركة البحث أو حفظه وزر الرجوع يشتغل.
 */
@Component({
  selector: 'app-jobs',
  imports: [
    FormsModule,
    RouterLink,
    TranslatePipe,
    IconComponent,
    PaginationComponent,
    ErrorAlertComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>{{ 'jobs.title' | t }}</h1>
    <form class="search-bar" (ngSubmit)="search()" role="search">
      <input
        name="q"
        type="search"
        maxlength="100"
        [(ngModel)]="qText"
        [attr.aria-label]="'jobs.keyword' | t"
        [placeholder]="'jobs.keyword' | t"
        data-testid="search-q"
      />
      <input
        name="city"
        type="search"
        maxlength="80"
        [(ngModel)]="cityText"
        [attr.aria-label]="'jobs.city' | t"
        [placeholder]="'jobs.city' | t"
        data-testid="search-city"
      />
      <select name="type" [(ngModel)]="typeText" [attr.aria-label]="'jobs.type' | t">
        <option value="">{{ 'common.all' | t }}</option>
        @for (type of types; track type) {
          <option [value]="type">{{ 'jobType.' + type | t }}</option>
        }
      </select>
      <button type="submit" class="btn btn--primary" data-testid="search-submit">
        <app-icon name="search" /> {{ 'common.search' | t }}
      </button>
    </form>

    @if (results.loading()) {
      <p class="loading"><span class="spinner"></span> {{ 'common.loading' | t }}</p>
    }
    <app-error-alert [error]="results.error()" />
    @if (results.value(); as result) {
      <p class="muted" data-testid="job-count">
        {{ 'jobs.count' | t: { count: format.number(result.totalElements) } }}
      </p>
      @if (result.content.length === 0) {
        <p class="empty-state">{{ 'jobs.empty' | t }}</p>
      }
      <ul class="job-list">
        @for (job of result.content; track job.id) {
          <li>
            <a class="job-card" [routerLink]="['/jobs', job.id]" data-testid="job-card">
              <h2>{{ job.title }}</h2>
              <div class="job-meta">
                <span>{{ job.companyName }}</span>
                <span>{{ job.city }}</span>
                <span>{{ 'jobType.' + job.type | t }}</span>
                <span class="num">{{ salary(job) }}</span>
                <span>{{ 'jobs.posted' | t: { date: format.date(job.publishedAt) } }}</span>
              </div>
            </a>
          </li>
        }
      </ul>
      <app-pagination
        [page]="result.page"
        [totalPages]="result.totalPages"
        (pageChange)="go($event)"
      />
    }
  `,
})
export class JobsPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly format = inject(FormatService);

  // Route query parameters arrive as inputs (withComponentInputBinding).
  // باراميترات الرابط توصل كـ inputs (withComponentInputBinding).
  readonly q = input<string | undefined>();
  readonly city = input<string | undefined>();
  readonly type = input<string | undefined>();
  readonly page = input<string | undefined>();

  protected readonly types = JOB_TYPES;
  protected readonly results = new Loadable<Page<JobSummary>>();

  // Form fields; they follow the URL when it changes (back button) and are sent to the URL on submit.
  // حقول النموذج؛ تتبع الرابط لما يتغير (زر الرجوع) وتنرسل للرابط عند البحث.
  protected qText = '';
  protected cityText = '';
  protected typeText = '';

  constructor() {
    effect(() => {
      const q = this.q() ?? '';
      const city = this.city() ?? '';
      const type = this.type() ?? '';
      this.qText = q;
      this.cityText = city;
      this.typeText = type;
      this.results.load(
        this.api.get<Page<JobSummary>>('/jobs', {
          q,
          city,
          type,
          page: Number(this.page() ?? 0) || 0,
          size: 10,
        }),
      );
    });
  }

  protected search(): void {
    void this.router.navigate(['/jobs'], {
      queryParams: {
        q: this.qText.trim() || null,
        city: this.cityText.trim() || null,
        type: this.typeText || null,
      },
    });
  }

  protected go(page: number): void {
    void this.router.navigate(['/jobs'], {
      queryParams: { page: page || null },
      queryParamsHandling: 'merge',
    });
  }

  protected salary(job: JobSummary): string {
    return salaryText(this.i18n, this.format, job.salaryMin, job.salaryMax);
  }
}
