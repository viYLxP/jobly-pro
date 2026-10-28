import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { ApiError, errorMessage } from '../core/api/api-error';
import { I18nService } from '../core/i18n/i18n.service';

/**
 * Shows an API error as a translated alert. It keeps the raw error, so the text follows a language switch.
 * يعرض خطأ الـ API كتنبيه مترجم. يحتفظ بالخطأ الخام، فالنص يتبع تبديل اللغة.
 */
@Component({
  selector: 'app-error-alert',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (text(); as message) {
    <p class="alert alert--danger" role="alert" data-testid="error-alert">{{ message }}</p>
  }`,
})
export class ErrorAlertComponent {
  private readonly i18n = inject(I18nService);

  readonly error = input<ApiError | null>(null);
  protected readonly text = computed(() => {
    const err = this.error();
    return err ? errorMessage(this.i18n, err) : null;
  });
}
