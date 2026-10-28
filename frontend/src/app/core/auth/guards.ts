import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Role } from '../api/models';
import { AuthService } from './auth.service';

// Guards only hide pages in the UI; the real protection is the server's role check on every endpoint.
// الحراس يخفون الصفحات في الواجهة فقط؛ والحماية الحقيقية هي فحص الدور في الخادم على كل رابط.

/**
 * Builds a guard that lets only the given role in. A visitor goes to sign-in, other roles to their own home.
 * يبني حارس يسمح بدور واحد فقط. الزائر يروح لصفحة الدخول، والأدوار الثانية لصفحتها.
 */
function roleGuard(role: Role): CanActivateFn {
  // inject() only works before the first await, so we inject first.
  // inject() يشتغل بس قبل أول await، فنسوي inject أول.
  return async () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!(await auth.restore())) return router.createUrlTree(['/login']);
    return auth.user()?.role === role ? true : router.parseUrl(auth.home());
  };
}

export const candidateGuard = roleGuard('CANDIDATE');
export const employerGuard = roleGuard('EMPLOYER');
export const adminGuard = roleGuard('ADMIN');

/**
 * Keeps signed-in users away from the sign-in and sign-up pages.
 * يمنع المستخدم المسجّل من صفحات الدخول والتسجيل.
 */
export const guestGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return (await auth.restore()) ? router.parseUrl(auth.home()) : true;
};

/**
 * Public pages that still show who is signed in: restore a saved session first.
 * الصفحات العامة اللي تعرض مين داخل: تسترجع الجلسة المحفوظة أول.
 */
export const sessionRestore: CanActivateFn = async () => {
  await inject(AuthService).restore();
  return true;
};
