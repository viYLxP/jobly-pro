import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { Loadable } from '../../core/api/loadable';
import { MyApplication, Page } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { ErrorAlertComponent } from '../../shared/error-alert.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

/**
 * The candidate's own applications, each with the timeline of its status changes.
 * طلبات المرشح، وكل طلب مع خط زمني لتغيّرات حالته.
 */
@Component({
  selector: 'app-applications',
  imports: [
    RouterLink,
    TranslatePipe,
    PaginationComponent,
    ErrorAlertComponent,
    StatusBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>{{ 'candidate.applications.title' | t }}</h1>
    @if (list.loading()) {
      <p class="loading"><span class="spinner"></span> {{ 'common.loading' | t }}</p>
    }
    <app-error-alert [error]="list.error()" />
    @if (list.value(); as result) {
      @if (result.content.length === 0) {
        <div class="empty-state" data-testid="applications-empty">
          <p>{{ 'candidate.applications.empty' | t }}</p>
          <a class="btn btn--primary" routerLink="/jobs">{{
            'candidate.applications.browse' | t
          }}</a>
        </div>
      }
      @for (application of result.content; track application.id) {
        <article class="card application" data-testid="application">
          <div class="card__header">
            <h2>
              <a [routerLink]="['/jobs', application.jobId]">{{ application.jobTitle }}</a>
            </h2>
            <app-status-badge group="appStatus" [value]="application.status" />
          </div>
          <div class="job-meta">
            <span>{{ application.companyName }}</span>
            <span>{{
              'candidate.applications.applied' | t: { date: format.date(application.createdAt) }
            }}</span>
          </div>
          <p class="cover-note">
            {{ application.coverNote || ('candidate.applications.noNote' | t) }}
          </p>
          <ol class="timeline" [attr.aria-label]="application.jobTitle">
            @for (entry of application.timeline; track entry.at + entry.status) {
              <li>
                <app-status-badge group="appStatus" [value]="entry.status" />
                <time [attr.datetime]="entry.at">{{ format.dateTime(entry.at) }}</time>
              </li>
            }
          </ol>
        </article>
      }
      <app-pagination
        [page]="result.page"
        [totalPages]="result.totalPages"
        (pageChange)="go($event)"
      />
    }
  `,
})
export class ApplicationsPage {
  private readonly api = inject(ApiService);
  protected readonly format = inject(FormatService);

  protected readonly list = new Loadable<Page<MyApplication>>();
  protected readonly page = signal(0);

  constructor() {
    this.load(0);
  }

  protected go(page: number): void {
    this.load(page);
  }

  private load(page: number): void {
    this.page.set(page);
    this.list.load(
      this.api.get<Page<MyApplication>>('/candidate/applications', { page, size: 10 }),
    );
  }
}
