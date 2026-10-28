-- Jobly Pro schema, part 2: candidate profiles, jobs and applications.
-- مخطط Jobly Pro، الجزء 2: ملفات المرشحين، والوظائف، والطلبات.

create table candidate_profile (
    user_id          bigint primary key references app_user (id) on delete cascade,
    headline         varchar(150),
    city             varchar(80),
    years_experience int check (years_experience between 0 and 60),
    summary          varchar(2000),
    updated_at       timestamptz not null default now(),
    version          bigint      not null default 0
);

create table job (
    id           bigserial primary key,
    employer_id  bigint       not null references app_user (id),
    title        varchar(150) not null,
    description  text         not null check (char_length(description) <= 8000),
    city         varchar(80)  not null,
    job_type     varchar(20)  not null check (job_type in ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP')),
    -- Monthly salary range in SAR, both optional. / نطاق الراتب الشهري بالريال، والاثنين اختياريين.
    salary_min   int check (salary_min >= 0),
    salary_max   int check (salary_max >= 0),
    -- DRAFT is visible to its employer only; PUBLISHED is searchable; CLOSED no longer takes applications.
    -- المسودة يشوفها صاحبها فقط؛ المنشورة تظهر في البحث؛ المغلقة ما تستقبل طلبات.
    status       varchar(20)  not null check (status in ('DRAFT', 'PUBLISHED', 'CLOSED')),
    created_at   timestamptz  not null default now(),
    published_at timestamptz,
    closed_at    timestamptz,
    version      bigint       not null default 0,
    constraint ck_job_salary check (salary_min is null or salary_max is null or salary_max >= salary_min)
);
-- The public list shows only published jobs, newest first: a partial index keeps it small and fast.
-- القائمة العامة تعرض المنشورة فقط، الأحدث أولًا: فهرس جزئي يخليه صغير وسريع.
create index ix_job_public on job (published_at desc, id desc) where status = 'PUBLISHED';
create index ix_job_employer on job (employer_id, created_at desc);

create table job_application (
    id           bigserial primary key,
    job_id       bigint      not null references job (id),
    candidate_id bigint      not null references app_user (id),
    cover_note   varchar(2000),
    status       varchar(20) not null check (status in ('SUBMITTED', 'REVIEWING', 'INTERVIEW', 'OFFER', 'REJECTED')),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    version      bigint      not null default 0,
    -- One application per candidate per job, enforced by the database even if two requests race.
    -- طلب واحد لكل مرشح لكل وظيفة، تفرضه قاعدة البيانات حتى لو تسابق طلبين.
    constraint ux_application_once unique (job_id, candidate_id)
);
create index ix_application_candidate on job_application (candidate_id, created_at desc);
create index ix_application_job on job_application (job_id, status);

-- Every status an application went through, for the candidate's timeline.
-- كل حالة مرّ فيها الطلب، لخط زمني يشوفه المرشح.
create table application_event (
    id             bigserial primary key,
    application_id bigint      not null references job_application (id) on delete cascade,
    status         varchar(20) not null,
    created_at     timestamptz not null default now()
);
create index ix_application_event on application_event (application_id, id);
