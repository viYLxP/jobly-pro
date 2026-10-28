import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { I18nService } from '../core/i18n/i18n.service';

/**
 * A coloured status pill. The text comes from the dictionary group (for example "appStatus"), the colour from the code.
 * شارة حالة ملوّنة. النص من مجموعة في القاموس (مثل "appStatus") واللون من رمز الحالة.
 */
@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [class]="'badge badge--' + value()" [attr.data-status]="value()">{{
    label()
  }}</span>`,
})
export class StatusBadgeComponent {
  private readonly i18n = inject(I18nService);

  readonly group = input.required<string>();
  readonly value = input.required<string>();
  protected readonly label = computed(() => this.i18n.t(`${this.group()}.${this.value()}`));
}
