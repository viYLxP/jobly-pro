import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { Role } from '../api/models';
import { AuthService } from './auth.service';
import { adminGuard, candidateGuard, employerGuard, guestGuard } from './guards';

function run(guard: typeof adminGuard): Promise<boolean | UrlTree> {
  return TestBed.runInInjectionContext(() =>
    guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
  ) as Promise<boolean | UrlTree>;
}

describe('role guards', () => {
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    router = TestBed.inject(Router);
  });

  function signInAs(role: Role): void {
    TestBed.inject(AuthService).adopt({
      token: 't',
      expiresAt: '2030-01-01T00:00:00Z',
      user: {
        id: 1,
        email: 'x@jobly.example',
        fullName: 'X',
        role,
        status: 'ACTIVE',
        language: 'en',
        companyName: null,
      },
    });
  }

  it('sends a visitor to the login page', async () => {
    const result = await run(candidateGuard);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });

  it('lets only the matching role in and sends others to their own home', async () => {
    signInAs('EMPLOYER');
    expect(await run(employerGuard)).toBe(true);
    expect(router.serializeUrl((await run(adminGuard)) as UrlTree)).toBe('/employer/jobs');
    expect(router.serializeUrl((await run(candidateGuard)) as UrlTree)).toBe('/employer/jobs');
  });

  it('keeps signed-in users away from the guest pages', async () => {
    signInAs('CANDIDATE');
    expect(router.serializeUrl((await run(guestGuard)) as UrlTree)).toBe('/candidate/applications');
  });

  it('treats a rejected saved token as signed out', async () => {
    sessionStorage.setItem('jobly.token', 'stale');
    const pending = TestBed.inject(AuthService).restore();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/auth/me')
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(await pending).toBe(false);
    expect(sessionStorage.getItem('jobly.token')).toBeNull();
  });
});
