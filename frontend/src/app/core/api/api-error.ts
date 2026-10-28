import { HttpErrorResponse } from '@angular/common/http';

import { I18nService } from '../i18n/i18n.service';

/**
 * Normalised error read from the server's ProblemDetail body.
 * خطأ موحّد مقروء من جسم ProblemDetail القادم من الخادم.
 */
export interface ApiError {
  status: number;
  code: string;
  // Field name → validation rule (e.g. { sku: 'Pattern' }).
  // اسم الحقل ← قاعدة التحقق (مثل { sku: 'Pattern' }).
  fields: Record<string, string>;
  // Extra details such as { current, requested } for INVALID_TRANSITION.
  // تفاصيل إضافية مثل { current, requested } لخطأ INVALID_TRANSITION.
  details: Record<string, unknown>;
}

/**
 * Converts any HttpClient error into an ApiError.
 * يحوّل أي خطأ من HttpClient إلى ApiError.
 */
export function toApiError(error: unknown): ApiError {
  if (error instanceof HttpErrorResponse) {
    // status 0 means the request never reached the server (offline, CORS, server down).
    // الحالة 0 تعني إن الطلب ما وصل للخادم أصلًا (بدون إنترنت، CORS، أو الخادم طافي).
    if (error.status === 0) {
      return { status: 0, code: 'NETWORK', fields: {}, details: {} };
    }
    const body = (error.error ?? {}) as Record<string, unknown>;
    return {
      status: error.status,
      code: typeof body['code'] === 'string' ? body['code'] : 'UNKNOWN',
      fields: (body['fields'] as Record<string, string>) ?? {},
      details: body,
    };
  }
  return { status: -1, code: 'UNKNOWN', fields: {}, details: {} };
}

/**
 * Builds the user-facing message for an error in the current language.
 * يبني رسالة الخطأ للمستخدم باللغة الحالية.
 */
export function errorMessage(i18n: I18nService, error: ApiError): string {
  const params: Record<string, string | number> = {};
  for (const [key, value] of Object.entries(error.details)) {
    if (typeof value === 'string' || typeof value === 'number') {
      params[key] = value;
    }
  }
  // Application statuses inside INVALID_TRANSITION ("from {current} to {requested}") are shown translated.
  // حالات الطلب داخل INVALID_TRANSITION ("من {current} إلى {requested}") تظهر مترجمة.
  for (const name of ['current', 'requested']) {
    if (typeof params[name] === 'string') {
      params[name] = i18n.t(`appStatus.${params[name]}`);
    }
  }
  const known = i18n.t(`errors.${error.code}`, params);
  return known === `errors.${error.code}` ? i18n.t('errors.UNKNOWN') : known;
}
