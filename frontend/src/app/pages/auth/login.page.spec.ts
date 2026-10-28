import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { I18nService } from '../../core/i18n/i18n.service';
import { LoginPage } from './login.page';

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let controller: HttpTestingController;
  let root: HTMLElement;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.inject(I18nService).setLang('en');
    controller = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LoginPage);
    root = fixture.nativeElement;
    fixture.detectChanges();
  });

  function fill(email: string, password: string): void {
    const email$ = root.querySelector<HTMLInputElement>('#email')!;
    const password$ = root.querySelector<HTMLInputElement>('#password')!;
    email$.value = email;
    email$.dispatchEvent(new Event('input'));
    password$.value = password;
    password$.dispatchEvent(new Event('input'));
    root.querySelector<HTMLButtonElement>('[data-testid=login-submit]')!.click();
  }

  it('does not call the server with an empty form', () => {
    root.querySelector<HTMLButtonElement>('[data-testid=login-submit]')!.click();
    fixture.detectChanges();
    controller.expectNone('/api/auth/login');
    expect(root.querySelectorAll('.field__error').length).toBe(2);
  });

  it('offers a new activation link when the email is not verified yet', async () => {
    fill('new@jobly.example', 'Password1');
    controller
      .expectOne('/api/auth/login')
      .flush({ code: 'EMAIL_NOT_VERIFIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(root.querySelector('[data-testid=error-alert]')?.textContent).toContain('not verified');
    const link = root.querySelector<HTMLAnchorElement>('a[href*="check-email"]');
    expect(link?.getAttribute('href')).toContain('email=new@jobly.example');
  });

  it('shows the wrong-password message and re-translates it when the language changes', () => {
    fill('a@jobly.example', 'Wrong1234');
    controller
      .expectOne('/api/auth/login')
      .flush({ code: 'BAD_CREDENTIALS' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(root.querySelector('[data-testid=error-alert]')?.textContent).toContain('Wrong email');
    TestBed.inject(I18nService).setLang('ar');
    fixture.detectChanges();
    expect(root.querySelector('[data-testid=error-alert]')?.textContent).toContain('غير صحيحة');
  });
});
