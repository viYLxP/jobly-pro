package com.jobly.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobly.application.ApplicationDtos.Applicant;
import com.jobly.application.ApplicationDtos.CandidateInfo;
import com.jobly.application.ApplicationDtos.MyApplication;
import com.jobly.application.ApplicationDtos.TimelineEntry;
import com.jobly.common.ApiException;
import com.jobly.common.ErrorCode;
import com.jobly.common.Links;
import com.jobly.common.PageResponse;
import com.jobly.job.Job;
import com.jobly.job.JobRepository;
import com.jobly.job.JobService;
import com.jobly.job.JobStatus;
import com.jobly.mail.EmailTemplate;
import com.jobly.mail.OutboxService;
import com.jobly.profile.CandidateProfile;
import com.jobly.profile.CandidateProfileRepository;
import com.jobly.user.AppUser;
import com.jobly.user.AppUserRepository;
import com.jobly.user.UserStatus;

/**
 * Applying to jobs, following an application, and the employer's review of applicants.
 * التقديم على الوظائف، ومتابعة الطلب، ومراجعة صاحب العمل للمتقدمين.
 *
 * <p>Both sides are told by email: the candidate when they apply and every time the status changes, the
 * employer when a new applicant arrives. The emails are queued in the same transaction as the change.</p>
 * <p>الطرفين يوصلهم إيميل: المرشح عند التقديم وعند كل تغيير حالة، وصاحب العمل عند وصول متقدم جديد.
 * الإيميلات تنضاف للطابور بنفس عملية التغيير.</p>
 */
@Service
public class ApplicationService {

    // Newest first; the id breaks ties so paging is stable. / الأحدث أولًا؛ والرقم يفك التعادل فيثبت الترتيب.
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final JobApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final JobRepository jobs;
    private final JobService jobService;
    private final AppUserRepository users;
    private final CandidateProfileRepository profiles;
    private final OutboxService outbox;
    private final Links links;

    public ApplicationService(JobApplicationRepository applications, ApplicationEventRepository events,
                              JobRepository jobs, JobService jobService, AppUserRepository users,
                              CandidateProfileRepository profiles, OutboxService outbox, Links links) {
        this.applications = applications;
        this.events = events;
        this.jobs = jobs;
        this.jobService = jobService;
        this.users = users;
        this.profiles = profiles;
        this.outbox = outbox;
        this.links = links;
    }

    // ---------------------------------------------------------------- candidate

    @Transactional
    public MyApplication apply(long candidateId, long jobId, String coverNote) {
        Job job = jobs.findWithEmployerById(jobId)
                .filter(j -> j.getStatus() != JobStatus.DRAFT)
                .filter(j -> j.getEmployer().getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("job", jobId));
        if (job.getStatus() == JobStatus.CLOSED) {
            throw new ApiException(ErrorCode.JOB_CLOSED);
        }
        if (applications.existsByJobIdAndCandidateId(jobId, candidateId)) {
            throw new ApiException(ErrorCode.ALREADY_APPLIED);
        }
        AppUser candidate = users.findById(candidateId).orElseThrow(() -> ApiException.notFound("user", candidateId));
        String note = coverNote == null || coverNote.isBlank() ? null : coverNote.trim();
        JobApplication application;
        try {
            // saveAndFlush sends the INSERT now, so a duplicate from two simultaneous requests fails right here.
            // saveAndFlush يرسل الـ INSERT الحين، فالتكرار من طلبين متزامنين يفشل هنا مباشرة.
            application = applications.saveAndFlush(new JobApplication(job, candidate, note));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.ALREADY_APPLIED);
        }
        Instant now = application.getCreatedAt();
        events.save(new ApplicationEvent(application.getId(), ApplicationStatus.SUBMITTED, now));

        String company = job.getEmployer().getCompanyName();
        outbox.enqueue(candidate.getEmail(), candidate.getLanguage(), EmailTemplate.APPLICATION_RECEIVED,
                Map.of("name", candidate.getFullName(), "jobTitle", job.getTitle(), "company", company,
                        "link", links.url("/candidate/applications")));
        AppUser employer = job.getEmployer();
        outbox.enqueue(employer.getEmail(), employer.getLanguage(), EmailTemplate.NEW_APPLICANT,
                Map.of("name", employer.getFullName(), "candidate", candidate.getFullName(),
                        "jobTitle", job.getTitle(),
                        "link", links.url("/employer/jobs/" + job.getId() + "/applicants")));
        return toMine(application, List.of(new ApplicationEvent(application.getId(), ApplicationStatus.SUBMITTED, now)));
    }

    @Transactional(readOnly = true)
    public PageResponse<MyApplication> listMine(long candidateId, Pageable pageable) {
        Page<JobApplication> page = applications.findByCandidateId(candidateId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST));
        Map<Long, List<ApplicationEvent>> timelines = timelines(page.getContent());
        return PageResponse.of(page, page.getContent().stream()
                .map(a -> toMine(a, timelines.getOrDefault(a.getId(), List.of()))).toList());
    }

    @Transactional(readOnly = true)
    public MyApplication getMine(long candidateId, long applicationId) {
        JobApplication application = applications.findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> ApiException.notFound("application", applicationId));
        return toMine(application, timelines(List.of(application)).getOrDefault(applicationId, List.of()));
    }

    // ---------------------------------------------------------------- employer

    /**
     * The applicants of one of this employer's jobs, optionally only one status.
     * متقدمو وظيفة من وظائف صاحب العمل هذا، ويمكن حصرهم بحالة واحدة.
     */
    @Transactional(readOnly = true)
    public PageResponse<Applicant> listForJob(long employerId, long jobId, ApplicationStatus status, Pageable pageable) {
        jobService.own(employerId, jobId);
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST);
        Page<JobApplication> page = status == null
                ? applications.findByJobId(jobId, sorted)
                : applications.findByJobIdAndStatus(jobId, status, sorted);
        Map<Long, CandidateProfile> profileById = new HashMap<>();
        profiles.findAllById(page.getContent().stream().map(a -> a.getCandidate().getId()).toList())
                .forEach(p -> profileById.put(p.getUserId(), p));
        return PageResponse.of(page, page.getContent().stream()
                .map(a -> toApplicant(a, profileById.get(a.getCandidate().getId()))).toList());
    }

    /**
     * Moves an application to the next step and tells the candidate by email.
     * ينقل الطلب للخطوة التالية ويبلغ المرشح بالإيميل.
     */
    @Transactional
    public Applicant changeStatus(long employerId, long applicationId, ApplicationStatus target) {
        JobApplication application = applications.findWithDetailsById(applicationId)
                .filter(a -> a.getJob().getEmployer().getId().equals(employerId))
                .orElseThrow(() -> ApiException.notFound("application", applicationId));
        if (!application.getStatus().canMoveTo(target)) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION,
                    Map.of("current", application.getStatus().name(), "requested", target.name()));
        }
        Instant now = Instant.now();
        application.moveTo(target, now);
        events.save(new ApplicationEvent(application.getId(), target, now));

        AppUser candidate = application.getCandidate();
        Job job = application.getJob();
        outbox.enqueue(candidate.getEmail(), candidate.getLanguage(), EmailTemplate.STATUS_CHANGED,
                Map.of("name", candidate.getFullName(), "jobTitle", job.getTitle(),
                        "company", job.getEmployer().getCompanyName(), "status", target.name(),
                        "link", links.url("/candidate/applications")));
        return toApplicant(application, profiles.findById(candidate.getId()).orElse(null));
    }

    // ---------------------------------------------------------------- mapping

    /** The history of several applications in one query. / تاريخ عدة طلبات باستعلام واحد. */
    private Map<Long, List<ApplicationEvent>> timelines(List<JobApplication> list) {
        Map<Long, List<ApplicationEvent>> byApplication = new HashMap<>();
        if (list.isEmpty()) {
            return byApplication;
        }
        for (ApplicationEvent event : events.findByApplicationIdInOrderByIdAsc(
                list.stream().map(JobApplication::getId).toList())) {
            byApplication.computeIfAbsent(event.getApplicationId(), k -> new java.util.ArrayList<>()).add(event);
        }
        return byApplication;
    }

    private static MyApplication toMine(JobApplication a, List<ApplicationEvent> timeline) {
        Job job = a.getJob();
        return new MyApplication(a.getId(), job.getId(), job.getTitle(), job.getEmployer().getCompanyName(),
                a.getStatus(), a.getCoverNote(), a.getCreatedAt(), a.getUpdatedAt(),
                timeline.stream().map(e -> new TimelineEntry(e.getStatus(), e.getCreatedAt())).toList());
    }

    private static Applicant toApplicant(JobApplication a, CandidateProfile profile) {
        AppUser c = a.getCandidate();
        CandidateInfo info = new CandidateInfo(c.getId(), c.getFullName(), c.getEmail(),
                profile == null ? null : profile.getHeadline(), profile == null ? null : profile.getCity(),
                profile == null ? null : profile.getYearsExperience(), profile == null ? null : profile.getSummary());
        return new Applicant(a.getId(), a.getJob().getId(), a.getJob().getTitle(), a.getStatus(), a.getCoverNote(),
                a.getCreatedAt(), a.getUpdatedAt(), info);
    }
}
