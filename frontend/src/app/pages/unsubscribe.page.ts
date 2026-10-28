import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { ApiError, toApiError } from '../core/api/api-error';
import { UnsubscribeResponse } from '../core/api/models';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { ErrorAlertComponent } from '../shared/error-alert.component';

/**
 * Opened from the link at the bottom of every alert email: /unsubscribe#<token>. The token is in the URL fragment,
 * so it never reaches web-server logs, and nothing happens until the person presses the button (mail scanners
 * that open links cannot unsubscribe anyone by accident).
 * يُفتح من الرابط في أسفل كل إيميل تنبيه: /unsubscribe#<token>. الرمز في جزء الـ fragment فما يوصل لسجلات الخادم،
 * وما يصير شي لين يضغط الشخص الزر (فاحصات البريد اللي تفتح الروابط ما تقدر توقف اشتراك أحد بالغلط).
 */
@Component({
  selector: 'app-unsubscribe',
  imports: [RouterLink, TranslatePipe, ErrorAlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card page-narrow">
      <h1>{{ 'unsubscribe.title' | t }}</h1>
      @if (done()) {
        <p class="alert alert--success" data-testid="unsubscribe-done">
          {{ 'unsubscribe.done' | t }}
        </p>
        <a routerLink="/candidate/alerts">{{ 'unsubscribe.login' | t }}</a>
      } @else if (!token) {
        <p class="alert alert--danger">{{ 'unsubscribe.invalid' | t }}</p>
      } @else {
        <p>{{ 'unsubscribe.text' | t }}</p>
        <app-error-alert [error]="error()" />
        <button
          type="button"
          class="btn btn--primary"
          [disabled]="working()"
          (click)="confirm()"
          data-testid="unsubscribe-confirm"
        >
          {{ 'unsubscribe.confirm' | t }}
        </button>
      }
    </section>
  `,
})
export class UnsubscribePage {
  private readonly api = inject(ApiService);

  protected readonly token = location.hash.slice(1);
  protected readonly working = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal<ApiError | null>(null);

  protected confirm(): void {
    this.working.set(true);
    this.error.set(null);
    this.api.post<UnsubscribeResponse>('/public/unsubscribe', { token: this.token }).subscribe({
      next: () => {
        this.working.set(false);
        this.done.set(true);
      },
      error: (err: unknown) => {
        this.working.set(false);
        this.error.set(toApiError(err));
      },
    });
  }
}
