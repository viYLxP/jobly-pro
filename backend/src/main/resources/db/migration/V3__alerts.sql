-- Jobly Pro schema, part 3: job alerts.
-- مخطط Jobly Pro، الجزء 3: تنبيهات الوظائف.

-- A saved search. When a job that matches is published, the candidate gets one email.
-- بحث محفوظ. لما تنشر وظيفة تطابقه، يوصل المرشح إيميل واحد.
create table job_alert (
    id           bigserial primary key,
    candidate_id bigint      not null references app_user (id) on delete cascade,
    keyword      varchar(100),
    city         varchar(80),
    job_type     varchar(20) check (job_type in ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP')),
    -- Switched off by the "stop alerts" link in an email, or by the candidate. / تُوقف من رابط "إيقاف التنبيهات" أو من المرشح.
    active       boolean     not null default true,
    created_at   timestamptz not null default now(),
    -- An alert with no filter at all would email every new job, so at least one filter is required.
    -- تنبيه بدون أي فلتر يرسل كل وظيفة جديدة، فلازم فلتر واحد على الأقل.
    constraint ck_alert_has_filter check (keyword is not null or city is not null or job_type is not null)
);
create index ix_job_alert_candidate on job_alert (candidate_id);
create index ix_job_alert_active on job_alert (job_type, lower(city)) where active;

-- Which job we already told which candidate about, and when. It stops duplicates and powers the daily limit.
-- أي وظيفة بلّغنا عنها أي مرشح ومتى. تمنع التكرار وتشغّل الحد اليومي.
create table job_alert_delivery (
    candidate_id bigint      not null references app_user (id) on delete cascade,
    job_id       bigint      not null references job (id) on delete cascade,
    created_at   timestamptz not null default now(),
    primary key (candidate_id, job_id)
);
create index ix_alert_delivery_recent on job_alert_delivery (candidate_id, created_at desc);
