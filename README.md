# Jobly Pro

A small full-stack job platform built around real email flows: accounts that must be activated from an email link,
password reset, a confirmation email when you apply, an email whenever an employer moves your application forward,
and job alerts. Arabic and English with right-to-left support.

**Stack:** Java 21, Spring Boot 4, PostgreSQL 17, Flyway, Spring Security (JWT) · Angular 21 (zoneless, signals) ·
Mailpit for development email · Playwright for browser tests.

## Who can do what

| Role | Can do |
| --- | --- |
| Visitor | Search and read jobs, register, activate, reset a password, unsubscribe from alerts |
| Candidate | Apply (once per job), see the status timeline, edit a profile, manage job alerts |
| Employer | Post jobs (draft, publish, close), see applicants, move an application to the next status |
| Admin | See platform numbers, search users, suspend and restore them, see the email outbox and retry failed emails |

Application statuses move one way: `SUBMITTED → REVIEWING → INTERVIEW → OFFER`, and any step before the offer can
go to `REJECTED`. The server enforces this; the screen only offers the allowed next steps.

## Run it

Needs Docker.

```
cp .env.example .env     # then put a random value in JWT_SECRET (openssl rand -base64 48)
docker compose up --build
```

- The app: <http://localhost:8084>
- Every email the app sends shows up in Mailpit: <http://localhost:8025> (open the activation link from there)
- API docs (Swagger): <http://localhost:8084/swagger-ui.html>

Demo accounts (created on an empty database, passwords can be changed in `.env`):

| Account | Password |
| --- | --- |
| `admin@jobly.example` | `Admin@12345` |
| `candidate@jobly.example` | `Candidate@12345` |
| `employer@jobly.example`, `employer2@jobly.example` | `Employer@12345` |

### Develop without Docker for the app itself

```
docker run -d --name jobly-db -e POSTGRES_DB=jobly -e POSTGRES_USER=jobly -e POSTGRES_PASSWORD=jobly -p 5432:5432 postgres:17-alpine
docker run -d --name jobly-mail -p 1025:1025 -p 8025:8025 axllent/mailpit:v1.21
cd backend  && ./mvnw spring-boot:run          # API on :8080
cd frontend && npm ci && npm start             # app on :4200, forwards /api to :8080
```

## Tests

| Where | What | Count |
| --- | --- | --- |
| `backend` | Integration tests on a real PostgreSQL (Testcontainers) and an in-memory SMTP server (GreenMail), plus unit tests | 89 |
| `frontend` | Unit and component tests (vitest) | 25 |
| `e2e` | Browser tests (Playwright) against the running stack, reading emails from Mailpit | 14 |

```
cd backend  && ./mvnw verify                    # needs Docker for Testcontainers
cd frontend && npm run lint && npm run check:i18n && npm run test:ci && npm run build
```

### End-to-end tests

Start the stack (`docker compose up --build`, with `RATE_LIMIT_ATTEMPTS=100000` in `.env`, because the tests register
many accounts from one address), then:

```
cd e2e
npm ci && npx playwright install chromium
E2E_BASE_URL=http://localhost:8084 MAILPIT_URL=http://localhost:8025 npx playwright test
```

They cover: register → read the activation email → activate → sign in; refused sign-in before activation; forgot and
reset password, with a link that works once; applying, the employer moving the application, the status email and the
timeline; a job alert email and the one-click unsubscribe; admin suspend and restore; role guards; Arabic and RTL; and
that no page scrolls sideways on a 375 px phone in either language.

CI (`.github/workflows/ci.yml`) runs all three levels on every push and pull request.

## How the email flows are built

- **Activation and reset links** carry a random 32-byte secret. Only its SHA-256 hash is stored, with an expiry, a
  single use, a resend cooldown and a daily limit. The secret sits after `#` in the link, so it never reaches web
  server logs. A new link replaces the older one.
- **No account enumeration.** Register, resend and forgot-password answer the same `202` whether or not the email has
  an account; an existing owner is told by email instead. Login says "wrong email or password" for an unknown email
  and for a wrong password, takes the same time for both, and reveals "not verified" or "suspended" only after the
  right password.
- **Outbox.** Emails are written to a table in the same transaction as the action that causes them, and a scheduler
  sends them. A failed send retries with a growing delay and becomes `FAILED` after 5 attempts; an admin can retry it.
  Restarting the server loses nothing, and an SMTP outage never breaks a request.
- **Job alerts** are matched when a job is published. A delivery table limits each candidate to a few alert emails per
  day and never sends the same job twice. The unsubscribe link is signed, never expires, and only turns alerts off; it
  needs a button press (`POST`), so mail scanners that open links cannot unsubscribe anyone.
- **Passwords:** BCrypt, 8 or more characters with a letter and a digit; changing the password invalidates older login
  tokens. Public account endpoints are rate limited per IP.
- **Emails** are HTML plus plain text, Arabic (RTL) or English by the account's language, with values HTML-escaped.

## Known limits and trade-offs

These are real and deliberate, not hidden:

- **Pre-registration.** Someone can register an address they do not own. They cannot use the account without the
  link in that inbox, and if the real owner registers, their details replace the unverified ones and the old link
  stops working. But an owner who clicks an activation link they never asked for would activate an account whose
  password someone else chose. The email text says to ignore messages you did not ask for.
- **No payments, CV upload, chat or WhatsApp.** Out of scope for this project.
- **Login token in `sessionStorage`** (cleared when the tab closes) rather than an httpOnly cookie. It is simple and
  has no CSRF surface, but a script injected into the page could read it. The app has no `innerHTML` and Angular
  escapes templates, but a cookie-based session would be the stronger choice for production.
- **Mailpit is a demo mail server.** For real use point `MAIL_*` at an SMTP provider and set up SPF and DKIM for the
  sending domain, or the emails will land in spam.
- **Single server.** The email scheduler runs in the API process. More than one API instance is safe (rows are
  claimed with `FOR UPDATE SKIP LOCKED`), but there is no load-balancer or HTTPS setup in this repository.
- **Search** is a simple `LIKE` match on title, description and company (every word must match), plus exact filters
  for city and type. Fine for thousands of jobs, not millions.

## Repository layout

```
backend/    Spring Boot API (com.jobly: auth, mail, job, application, profile, alert, admin, security, config)
frontend/   Angular app (core: api, auth, i18n, ui · pages · shared · layout)
e2e/        Playwright browser tests
docker-compose.yml, .env.example, .github/workflows/ci.yml
```
