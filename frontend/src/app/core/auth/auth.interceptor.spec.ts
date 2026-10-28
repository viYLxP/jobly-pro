import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { I18nService } from '../i18n/i18n.service';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'login', children: [] }]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    TestBed.inject(I18nService).setLang('en');
  });

  afterEach(() => controller.verify());

  function signIn(): void {
    auth.adopt({
      token: 'jwt-123',
      expiresAt: '2030-01-01T00:00:00Z',
      user: {
        id: 1,
        email: 'c@jobly.example',
        fullName: 'Candidate',
        role: 'CANDIDATE',
        status: 'ACTIVE',
        language: 'en',
        companyName: null,
      },
    });
  }

  it('adds the token and the language to private calls', () => {
    signIn();
    http.get('/api/candidate/profile').subscribe();
    const request = controller.expectOne('/api/candidate/profile');
    expect(request.request.headers.get('Authorization')).toBe('Bearer jwt-123');
    expect(request.request.headers.get('Accept-Language')).toBe('en');
    request.flush({});
  });

  it('never sends the token to public account endpoints', () => {
    signIn();
    for (const path of ['/api/auth/login', '/api/auth/verify-email', '/api/public/unsubscribe']) {
      http.post(path, {}).subscribe();
      const request = controller.expectOne(path);
      expect(request.request.headers.has('Authorization'), path).toBe(false);
      request.flush({});
    }
  });

  it('signs the user out on a 401 from a private call, but not from login', () => {
    signIn();
    http.get('/api/auth/login').subscribe({ error: () => undefined });
    controller.expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.token()).toBe('jwt-123');

    http.get('/api/candidate/applications').subscribe({ error: () => undefined });
    controller
      .expectOne('/api/candidate/applications')
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.token()).toBeNull();
    expect(auth.user()).toBeNull();
  });
});
