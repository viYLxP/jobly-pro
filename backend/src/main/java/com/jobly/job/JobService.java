package com.jobly.job;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobly.application.JobApplicationRepository;
import com.jobly.common.ApiException;
import com.jobly.common.ErrorCode;
import com.jobly.job.JobDtos.EmployerJob;
import com.jobly.job.JobDtos.JobRequest;
import com.jobly.user.AppUser;
import com.jobly.user.AppUserRepository;

/**
 * Public job search, and the employer's own job management.
 * البحث العام في الوظائف، وإدارة صاحب العمل لوظائفه.
 *
 * <p>An employer can only ever touch their own jobs. Asking for someone else's job answers "not found", the same
 * as for a job that does not exist, so ids cannot be probed.</p>
 * <p>صاحب العمل ما يلمس إلا وظائفه. طلب وظيفة غيره يرد "غير موجود"، مثل وظيفة ما لها وجود، فما ينفع تخمين الأرقام.</p>
 */
@Service
public class JobService {

    // Newest published first; the id breaks ties so paging never repeats or skips a job.
    // الأحدث نشرًا أولًا؛ والرقم يفك التعادل فالتقسيم لصفحات ما يكرر ولا يفوّت وظيفة.
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    private static final int MAX_SEARCH_WORDS = 5;

    private final JobRepository jobs;
    private final AppUserRepository users;
    private final JobApplicationRepository applications;

    public JobService(JobRepository jobs, AppUserRepository users, JobApplicationRepository applications) {
        this.jobs = jobs;
        this.users = users;
        this.applications = applications;
    }

    // ---------------------------------------------------------------- public

    /**
     * Searches published jobs. Every filter is optional.
     * يبحث في الوظائف المنشورة. كل الفلاتر اختيارية.
     *
     * @param q    words looked for in the title, description and company name / كلمات تُبحث في العنوان والوصف واسم الشركة
     * @param city exact city, ignoring case / المدينة بالضبط بغض النظر عن حالة الأحرف
     * @param type full time, part time... / دوام كامل، جزئي...
     */
    @Transactional(readOnly = true)
    public Page<Job> search(String q, String city, JobType type, Pageable pageable) {
        Specification<Job> spec = (root, query, cb) -> {
            // The count query Spring runs for paging cannot use a fetch join, so only fetch for the data query.
            // استعلام العدّ اللي ينفذه Spring للتقسيم ما يقبل fetch join، فنجلب صاحب العمل في استعلام البيانات فقط.
            boolean dataQuery = query.getResultType() != Long.class && query.getResultType() != long.class;
            From<?, ?> employer = dataQuery ? (From<?, ?>) root.fetch("employer") : root.join("employer");
            List<Predicate> all = new java.util.ArrayList<>();
            all.add(cb.equal(root.get("status"), JobStatus.PUBLISHED));
            if (type != null) {
                all.add(cb.equal(root.get("type"), type));
            }
            if (city != null && !city.isBlank()) {
                all.add(cb.equal(cb.lower(root.get("city")), city.trim().toLowerCase(Locale.ROOT)));
            }
            if (q != null && !q.isBlank()) {
                // Every word must match somewhere, so "java riyadh" narrows the result like people expect.
                // كل كلمة لازم تتطابق في مكان ما، فـ "java riyadh" تضيّق النتيجة مثل ما يتوقع الناس.
                // Only the first few words count, so one request cannot become a huge query.
                // أول كم كلمة فقط تُحسب، فما يتحول طلب واحد لاستعلام ضخم.
                String[] words = q.trim().toLowerCase(Locale.ROOT).split("\\s+");
                for (int i = 0; i < Math.min(words.length, MAX_SEARCH_WORDS); i++) {
                    String word = words[i];
                    String pattern = "%" + escapeLike(word) + "%";
                    all.add(cb.or(
                            cb.like(cb.lower(root.get("title")), pattern, '\\'),
                            cb.like(cb.lower(root.get("description")), pattern, '\\'),
                            cb.like(cb.lower(employer.get("companyName")), pattern, '\\')));
                }
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
        return jobs.findAll(spec, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST));
    }

    /**
     * A job page anyone may open. Drafts do not exist for the public; closed jobs are shown as closed.
     * صفحة وظيفة يقدر أي أحد يفتحها. المسودات غير موجودة للعامة؛ والمغلقة تظهر كمغلقة.
     */
    @Transactional(readOnly = true)
    public Job getPublic(long id) {
        return jobs.findWithEmployerById(id)
                .filter(job -> job.getStatus() != JobStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound("job", id));
    }

    /** Escapes % and _ so a visitor's text is searched literally. / يهرّب % و _ فيُبحث عن نص الزائر حرفيًا. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ---------------------------------------------------------------- employer

    @Transactional(readOnly = true)
    public List<EmployerJob> listOwn(long employerId) {
        List<Job> own = jobs.findByEmployerIdOrderByCreatedAtDescIdDesc(employerId);
        Map<Long, Long> counts = applications.countByJobs(own.stream().map(Job::getId).toList());
        return own.stream().map(job -> EmployerJob.of(job, counts.getOrDefault(job.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public EmployerJob getOwn(long employerId, long jobId) {
        Job job = own(employerId, jobId);
        return EmployerJob.of(job, applications.countByJobId(jobId));
    }

    @Transactional
    public EmployerJob create(long employerId, JobRequest request) {
        checkSalary(request);
        AppUser employer = users.getReferenceById(employerId);
        Job job = jobs.save(new Job(employer, request.title().trim(), request.description().trim(),
                request.city().trim(), request.type(), request.salaryMin(), request.salaryMax()));
        return EmployerJob.of(job, 0);
    }

    @Transactional
    public EmployerJob update(long employerId, long jobId, JobRequest request) {
        checkSalary(request);
        Job job = own(employerId, jobId);
        // A closed job is history; to run it again, post a new one.
        // الوظيفة المغلقة صارت تاريخ؛ لو تبي تعيدها انشر وظيفة جديدة.
        if (job.getStatus() == JobStatus.CLOSED) {
            throw new ApiException(ErrorCode.INVALID_STATUS);
        }
        job.edit(request.title().trim(), request.description().trim(), request.city().trim(), request.type(),
                request.salaryMin(), request.salaryMax());
        return EmployerJob.of(job, applications.countByJobId(jobId));
    }

    @Transactional
    public EmployerJob publish(long employerId, long jobId) {
        Job job = own(employerId, jobId);
        if (job.getStatus() != JobStatus.DRAFT) {
            throw new ApiException(ErrorCode.INVALID_STATUS);
        }
        job.publish(Instant.now());
        return EmployerJob.of(job, 0);
    }

    @Transactional
    public EmployerJob close(long employerId, long jobId) {
        Job job = own(employerId, jobId);
        if (job.getStatus() != JobStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.INVALID_STATUS);
        }
        job.close(Instant.now());
        return EmployerJob.of(job, applications.countByJobId(jobId));
    }

    /**
     * Deletes a job nobody applied to. A job with applicants must be closed instead, so a candidate's application
     * never loses its job.
     * يحذف وظيفة ما قدّم عليها أحد. الوظيفة اللي عليها متقدمين لازم تنقفل بدل الحذف، حتى طلب المرشح ما يفقد وظيفته.
     */
    @Transactional
    public void delete(long employerId, long jobId) {
        Job job = own(employerId, jobId);
        if (applications.countByJobId(jobId) > 0) {
            throw new ApiException(ErrorCode.IN_USE);
        }
        jobs.delete(job);
    }

    /** The job, only if it belongs to this employer. / الوظيفة، لو كانت لصاحب العمل هذا فقط. */
    public Job own(long employerId, long jobId) {
        return jobs.findWithEmployerById(jobId)
                .filter(job -> job.getEmployer().getId().equals(employerId))
                .orElseThrow(() -> ApiException.notFound("job", jobId));
    }

    private static void checkSalary(JobRequest request) {
        if (request.salaryMin() != null && request.salaryMax() != null && request.salaryMax() < request.salaryMin()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("salaryMax", "Min")));
        }
    }
}
