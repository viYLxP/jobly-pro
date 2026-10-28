import { Routes } from '@angular/router';

import {
  adminGuard,
  candidateGuard,
  employerGuard,
  guestGuard,
  sessionRestore,
} from './core/auth/guards';
import { SiteLayoutComponent } from './layout/site-layout.component';

/**
 * Every page is lazy-loaded, so a visitor only downloads the code of the pages they open.
 * كل صفحة تتحمّل عند الطلب، فالزائر يحمّل فقط كود الصفحات اللي يفتحها.
 */
export const routes: Routes = [
  {
    path: '',
    component: SiteLayoutComponent,
    // Restores a saved session first so the header knows who is signed in on every page.
    // يسترجع الجلسة المحفوظة أول، حتى يعرف الهيدر مين داخل في كل صفحة.
    canActivate: [sessionRestore],
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./pages/home.page').then((m) => m.HomePage),
      },
      { path: 'jobs', loadComponent: () => import('./pages/jobs.page').then((m) => m.JobsPage) },
      {
        path: 'jobs/:id',
        loadComponent: () => import('./pages/job-detail.page').then((m) => m.JobDetailPage),
      },

      // Account pages. The links in emails land on verify-email, reset-password and unsubscribe.
      // صفحات الحساب. الروابط في الإيميلات توصل لـ verify-email وreset-password وunsubscribe.
      {
        path: 'login',
        canActivate: [guestGuard],
        loadComponent: () => import('./pages/auth/login.page').then((m) => m.LoginPage),
      },
      {
        path: 'register',
        canActivate: [guestGuard],
        loadComponent: () => import('./pages/auth/register.page').then((m) => m.RegisterPage),
      },
      {
        path: 'check-email',
        loadComponent: () => import('./pages/auth/check-email.page').then((m) => m.CheckEmailPage),
      },
      {
        path: 'verify-email',
        loadComponent: () =>
          import('./pages/auth/verify-email.page').then((m) => m.VerifyEmailPage),
      },
      {
        path: 'forgot-password',
        loadComponent: () =>
          import('./pages/auth/forgot-password.page').then((m) => m.ForgotPasswordPage),
      },
      {
        path: 'reset-password',
        loadComponent: () =>
          import('./pages/auth/reset-password.page').then((m) => m.ResetPasswordPage),
      },
      {
        path: 'unsubscribe',
        loadComponent: () => import('./pages/unsubscribe.page').then((m) => m.UnsubscribePage),
      },

      {
        path: 'candidate',
        canActivate: [candidateGuard],
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'applications' },
          {
            path: 'applications',
            loadComponent: () =>
              import('./pages/candidate/applications.page').then((m) => m.ApplicationsPage),
          },
          {
            path: 'profile',
            loadComponent: () =>
              import('./pages/candidate/profile.page').then((m) => m.ProfilePage),
          },
          {
            path: 'alerts',
            loadComponent: () => import('./pages/candidate/alerts.page').then((m) => m.AlertsPage),
          },
        ],
      },
      {
        path: 'employer',
        canActivate: [employerGuard],
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'jobs' },
          {
            path: 'jobs',
            loadComponent: () =>
              import('./pages/employer/jobs.page').then((m) => m.EmployerJobsPage),
          },
          {
            path: 'jobs/new',
            loadComponent: () =>
              import('./pages/employer/job-form.page').then((m) => m.JobFormPage),
          },
          {
            path: 'jobs/:id/edit',
            loadComponent: () =>
              import('./pages/employer/job-form.page').then((m) => m.JobFormPage),
          },
          {
            path: 'jobs/:id/applicants',
            loadComponent: () =>
              import('./pages/employer/applicants.page').then((m) => m.ApplicantsPage),
          },
        ],
      },
      {
        path: 'admin',
        canActivate: [adminGuard],
        loadComponent: () => import('./pages/admin/admin.page').then((m) => m.AdminPage),
        children: [
          {
            path: '',
            pathMatch: 'full',
            loadComponent: () => import('./pages/admin/stats.page').then((m) => m.StatsPage),
          },
          {
            path: 'users',
            loadComponent: () => import('./pages/admin/users.page').then((m) => m.UsersPage),
          },
          {
            path: 'emails',
            loadComponent: () => import('./pages/admin/emails.page').then((m) => m.EmailsPage),
          },
        ],
      },
      {
        path: '**',
        loadComponent: () => import('./pages/not-found.page').then((m) => m.NotFoundPage),
      },
    ],
  },
];
