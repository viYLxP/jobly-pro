import { I18nService } from '../core/i18n/i18n.service';
import { FormatService } from '../core/i18n/format.service';

/**
 * One line for a salary range, e.g. "8,000 – 12,000 SAR / month". Both ends are optional.
 * سطر واحد لنطاق الراتب. أي طرف منه ممكن يكون فاضي.
 */
export function salaryText(
  i18n: I18nService,
  format: FormatService,
  min: number | null,
  max: number | null,
): string {
  const unit = i18n.t('app.currency');
  const fmt = (value: number) => `${format.number(value)} ${unit}`;
  if (min !== null && max !== null) {
    return i18n.t('jobs.salary', { min: fmt(min), max: fmt(max) });
  }
  if (min !== null) return i18n.t('jobs.salaryFrom', { min: fmt(min) });
  if (max !== null) return i18n.t('jobs.salaryUpTo', { max: fmt(max) });
  return i18n.t('jobs.salaryHidden');
}
