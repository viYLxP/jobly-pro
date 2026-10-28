-- Jobly Pro schema, part 1: accounts, one-time email tokens and the email outbox.
-- مخطط Jobly Pro، الجزء 1: الحسابات، ورموز الإيميل لمرة واحدة، وصندوق الإيميلات الصادرة.

create table app_user (
    id                  bigserial primary key,
    email               varchar(254) not null,
    password_hash       varchar(100) not null,
    full_name           varchar(120) not null,
    role                varchar(20)  not null check (role in ('CANDIDATE', 'EMPLOYER', 'ADMIN')),
    -- PENDING = registered but email not verified yet; cannot log in.
    -- PENDING = مسجّل لكن الإيميل ما انفعّل؛ ما يقدر يدخل.
    status              varchar(20)  not null check (status in ('PENDING', 'ACTIVE', 'SUSPENDED')),
    language            varchar(2)   not null default 'ar' check (language in ('ar', 'en')),
    company_name        varchar(150),
    created_at          timestamptz  not null default now(),
    verified_at         timestamptz,
    -- Tokens issued before this moment are rejected (set when the password changes).
    -- الرموز الصادرة قبل هذا الوقت تُرفض (يتحدد عند تغيير كلمة المرور).
    password_changed_at timestamptz,
    version             bigint       not null default 0,
    constraint ck_employer_has_company check (role <> 'EMPLOYER' or company_name is not null)
);
-- Email is unique ignoring case: Sara@x.com and sara@x.com are the same account.
-- الإيميل فريد بغض النظر عن حالة الأحرف.
create unique index ux_app_user_email on app_user (lower(email));

create table email_token (
    id         bigserial primary key,
    user_id    bigint      not null references app_user (id) on delete cascade,
    type       varchar(20) not null check (type in ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    -- Only the SHA-256 hash is stored, so a database leak does not leak working links.
    -- نخزّن SHA-256 للرمز فقط، فتسريب القاعدة ما يسرّب روابط شغالة.
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null default now()
);
create unique index ux_email_token_hash on email_token (token_hash);
create index ix_email_token_user on email_token (user_id, type, created_at desc);

-- Emails are written here in the same transaction as the change that causes them, and a sender delivers them
-- later with retries. If the mail server is down, nothing is lost.
-- الإيميلات تُكتب هنا بنفس عملية التغيير اللي سببتها، ومرسل لاحق يوصّلها مع إعادة المحاولة. لو خادم البريد واقف ما يضيع شي.
create table email_outbox (
    id              bigserial primary key,
    to_email        varchar(254) not null,
    language        varchar(2)   not null,
    template        varchar(40)  not null,
    status          varchar(10)  not null default 'PENDING' check (status in ('PENDING', 'SENT', 'FAILED')),
    attempts        int          not null default 0,
    next_attempt_at timestamptz  not null default now(),
    last_error      varchar(500),
    created_at      timestamptz  not null default now(),
    sent_at         timestamptz
);
create index ix_email_outbox_due on email_outbox (next_attempt_at) where status = 'PENDING';

create table email_outbox_param (
    outbox_id bigint        not null references email_outbox (id) on delete cascade,
    name      varchar(40)   not null,
    value     varchar(1000) not null,
    primary key (outbox_id, name)
);
