# Jobly Pro

A small full-stack job platform (Spring Boot + Angular + PostgreSQL) built around real email flows: account
activation by email link, password reset, status emails and job alerts. Arabic and English, right-to-left ready.

Status: work in progress. Milestone 1 (accounts, email verification, password reset, email outbox) is done in
`backend/`. The full plan is milestones 1 to 5: accounts and emails, jobs and applications, alerts and admin,
the Angular app, then browser tests, CI and deployment notes.

## Run the backend tests

Needs Java 21 and Docker (the tests start a real PostgreSQL with Testcontainers and an in-memory SMTP server).

```
cd backend
./mvnw verify
```
