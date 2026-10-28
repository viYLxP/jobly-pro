import { TestBed } from '@angular/core/testing';

import { FormatService } from '../core/i18n/format.service';
import { I18nService } from '../core/i18n/i18n.service';
import { salaryText } from './salary';

describe('salaryText', () => {
  let i18n: I18nService;
  let format: FormatService;

  beforeEach(() => {
    i18n = TestBed.inject(I18nService);
    format = TestBed.inject(FormatService);
    i18n.setLang('en');
  });

  it('shows a range, one open end, or a "not listed" text', () => {
    expect(salaryText(i18n, format, 8000, 12000)).toBe('8,000 SAR – 12,000 SAR / month');
    expect(salaryText(i18n, format, 8000, null)).toBe('From 8,000 SAR / month');
    expect(salaryText(i18n, format, null, 12000)).toBe('Up to 12,000 SAR / month');
    expect(salaryText(i18n, format, null, null)).toBe('Salary not listed');
  });

  it('uses the Arabic currency text in Arabic', () => {
    i18n.setLang('ar');
    expect(salaryText(i18n, format, 5000, null)).toContain('ر.س');
  });
});
