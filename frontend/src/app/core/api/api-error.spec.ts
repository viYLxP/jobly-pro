import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { I18nService } from '../i18n/i18n.service';
import { errorMessage, toApiError } from './api-error';

describe('api-error', () => {
  let i18n: I18nService;

  beforeEach(() => {
    i18n = TestBed.inject(I18nService);
    i18n.setLang('en');
  });

  it('reads code, fields and details from a ProblemDetail body', () => {
    const err = toApiError(
      new HttpErrorResponse({
        status: 400,
        error: { code: 'VALIDATION_FAILED', fields: { phone: 'Pattern' } },
      }),
    );
    expect(err.status).toBe(400);
    expect(err.code).toBe('VALIDATION_FAILED');
    expect(err.fields).toEqual({ phone: 'Pattern' });
  });

  it('maps status 0 to NETWORK and non-HTTP errors to UNKNOWN', () => {
    expect(toApiError(new HttpErrorResponse({ status: 0 })).code).toBe('NETWORK');
    expect(toApiError(new Error('boom')).code).toBe('UNKNOWN');
    // A proxy HTML error page has no JSON code.
    // صفحة خطأ HTML من البروكسي ما فيها code بصيغة JSON.
    expect(toApiError(new HttpErrorResponse({ status: 502, error: '<html>' })).code).toBe(
      'UNKNOWN',
    );
  });

  it('translates the application statuses inside INVALID_TRANSITION', () => {
    const err = toApiError(
      new HttpErrorResponse({
        status: 409,
        error: { code: 'INVALID_TRANSITION', current: 'OFFER', requested: 'REVIEWING' },
      }),
    );
    expect(errorMessage(i18n, err)).toBe('An application cannot move from Offer to Under review.');
    i18n.setLang('ar');
    expect(errorMessage(i18n, err)).toContain('عرض وظيفي');
  });

  it('gives a specific message for each known code', () => {
    const err = toApiError(
      new HttpErrorResponse({ status: 403, error: { code: 'EMAIL_NOT_VERIFIED' } }),
    );
    expect(errorMessage(i18n, err)).toBe(i18n.t('errors.EMAIL_NOT_VERIFIED'));
  });

  it('falls back to a generic message for unknown codes', () => {
    const err = toApiError(
      new HttpErrorResponse({ status: 418, error: { code: 'SOMETHING_NEW' } }),
    );
    expect(errorMessage(i18n, err)).toBe(i18n.t('errors.UNKNOWN'));
  });
});
