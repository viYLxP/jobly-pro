import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, firstValueFrom, tap } from 'rxjs';

import { ApiService } from '../api/api.service';
import { LoginResponse, StatusResponse, User } from '../api/models';
import { I18nService } from '../i18n/i18n.service';

const TOKEN_KEY = 'jobly.token';

export interface RegisterInput {
  email: string;
  password: string;
  fullName: string;
  role: 'CANDIDATE' | 'EMPLOYER';
  companyName: string | null;
}

/**
 * Session state: the JWT and the signed-in user.
 * حالة الجلسة: رمز JWT والمستخدم المسجّل.
 *
 * The token lives in sessionStorage (cleared when the tab closes), which narrows how long a stolen token is useful.
 * الرمز محفوظ في sessionStorage (ينمسح لما تقفل التبويب)، وهذا يقلل مدة فائدة الرمز المسروق.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  private readonly tokenSignal = signal<string | null>(readToken());
  private readonly userSignal = signal<User | null>(null);

  readonly token = this.tokenSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly isCandidate = computed(() => this.userSignal()?.role === 'CANDIDATE');
  readonly isEmployer = computed(() => this.userSignal()?.role === 'EMPLOYER');
  readonly isAdmin = computed(() => this.userSignal()?.role === 'ADMIN');
  // Where each role lands after signing in.
  // الصفحة اللي يوصل لها كل دور بعد الدخول.
  readonly home = computed(() => {
    switch (this.userSignal()?.role) {
      case 'EMPLOYER':
        return '/employer/jobs';
      case 'ADMIN':
        return '/admin';
      case 'CANDIDATE':
        return '/candidate/applications';
      default:
        return '/';
    }
  });

  /** Creates an account. The server always answers CHECK_EMAIL, even when the email already exists. */
  /** ينشئ حساب. الخادم يرد دائمًا CHECK_EMAIL حتى لو الإيميل موجود. */
  register(input: RegisterInput): Observable<StatusResponse> {
    return this.api.post<StatusResponse>('/auth/register', {
      ...input,
      companyName: input.role === 'EMPLOYER' ? input.companyName : null,
      language: this.i18n.lang(),
    });
  }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.api
      .post<LoginResponse>('/auth/login', { email, password })
      .pipe(tap((response) => this.adopt(response)));
  }

  adopt(response: LoginResponse): void {
    this.storeToken(response.token);
    this.userSignal.set(response.user);
  }

  /**
   * Restores the session after a page reload: if a token is saved, ask the server who it belongs to.
   * يسترجع الجلسة بعد إعادة تحميل الصفحة: لو فيه رمز محفوظ، نسأل الخادم لمن هو.
   */
  async restore(): Promise<boolean> {
    if (this.userSignal()) {
      return true;
    }
    if (!this.tokenSignal()) {
      return false;
    }
    try {
      this.userSignal.set(await firstValueFrom(this.api.get<User>('/auth/me')));
      return true;
    } catch {
      this.clear();
      return false;
    }
  }

  logout(): void {
    this.clear();
    void this.router.navigateByUrl('/');
  }

  /** Called by the interceptor on any 401 from the API. / يستدعيه الـ interceptor عند أي 401 من الـ API. */
  sessionExpired(): void {
    this.clear();
    void this.router.navigate(['/login'], { queryParams: { expired: 1 } });
  }

  private storeToken(token: string): void {
    this.tokenSignal.set(token);
    try {
      sessionStorage.setItem(TOKEN_KEY, token);
    } catch {
      // Storage blocked: the session still works until the page is reloaded.
      // التخزين محجوب: الجلسة تشتغل لين تتحدث الصفحة.
    }
  }

  private clear(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    try {
      sessionStorage.removeItem(TOKEN_KEY);
    } catch {
      // Nothing to clean up if storage is unavailable.
      // ما فيه شي ننظفه لو التخزين غير متاح.
    }
  }
}

function readToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}
