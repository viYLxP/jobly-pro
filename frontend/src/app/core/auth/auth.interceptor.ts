import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { I18nService } from '../i18n/i18n.service';
import { AuthService } from './auth.service';

// Public endpoints never get a token, and a 401 from them is not an expired session.
// الروابط العامة ما ترسل لها رمز، و401 منها لا يعني انتهاء جلسة.
const PUBLIC_PATHS = [
  '/auth/register',
  '/auth/login',
  '/auth/verify-email',
  '/auth/resend-verification',
  '/auth/forgot-password',
  '/auth/reset-password',
  '/public/',
];

/**
 * Adds "Authorization: Bearer <token>" and "Accept-Language" to API calls, and signs the user out on a 401.
 * يضيف "Authorization: Bearer <token>" و"Accept-Language" لطلبات الـ API، ويسجّل خروج المستخدم عند 401.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const i18n = inject(I18nService);
  const token = auth.token();
  const isPublic = PUBLIC_PATHS.some((path) => request.url.includes(path));

  const headers: Record<string, string> = { 'Accept-Language': i18n.lang() };
  if (token && !isPublic) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  return next(request.clone({ setHeaders: headers })).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !isPublic) {
        auth.sessionExpired();
      }
      return throwError(() => error);
    }),
  );
};
