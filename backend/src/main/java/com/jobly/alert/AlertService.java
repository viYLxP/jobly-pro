package com.jobly.alert;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobly.alert.AlertDtos.AlertRequest;
import com.jobly.alert.AlertDtos.AlertResponse;
import com.jobly.common.ApiException;
import com.jobly.common.ErrorCode;
import com.jobly.common.Links;
import com.jobly.config.AppProperties;
import com.jobly.job.Job;
import com.jobly.mail.EmailTemplate;
import com.jobly.mail.OutboxService;
import com.jobly.user.AppUser;
import com.jobly.user.AppUserRepository;

/**
 * Saved job searches, and emailing the candidates whose search matches a newly published job.
 * البحوث المحفوظة للوظائف، وإرسال إيميل للمرشحين اللي بحثهم يطابق وظيفة نُشرت للتو.
 */
@Service
public class AlertService {

    private static final int MAX_KEYWORD_WORDS = 5;

    private final JobAlertRepository alerts;
    private final AppUserRepository users;
    private final OutboxService outbox;
    private final UnsubscribeTokens tokens;
    private final Links links;
    private final JdbcClient jdbc;
    private final AppProperties.Alerts limits;

    public AlertService(JobAlertRepository alerts, AppUserRepository users, OutboxService outbox,
                        UnsubscribeTokens tokens, Links links, JdbcClient jdbc, AppProperties properties) {
        this.alerts = alerts;
        this.users = users;
        this.outbox = outbox;
        this.tokens = tokens;
        this.links = links;
        this.jdbc = jdbc;
        this.limits = properties.alerts();
    }

    // ---------------------------------------------------------------- the candidate's own alerts

    @Transactional(readOnly = true)
    public List<AlertResponse> list(long candidateId) {
        return alerts.findByCandidateIdOrderByIdDesc(candidateId).stream().map(AlertResponse::of).toList();
    }

    @Transactional
    public AlertResponse create(long candidateId, AlertRequest request) {
        String keyword = blankToNull(request.keyword());
        String city = blankToNull(request.city());
        if (keyword == null && city == null && request.type() == null) {
            // An alert with no filter would email every new job. / تنبيه بدون فلتر يرسل كل وظيفة جديدة.
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("filter", "Required")));
        }
        if (alerts.countByCandidateId(candidateId) >= limits.maxPerCandidate()) {
            throw new ApiException(ErrorCode.ALERT_LIMIT);
        }
        AppUser candidate = users.getReferenceById(candidateId);
        return AlertResponse.of(alerts.save(new JobAlert(candidate, keyword, city, request.type())));
    }

    @Transactional
    public AlertResponse setActive(long candidateId, long alertId, boolean active) {
        JobAlert alert = owned(candidateId, alertId);
        alert.setActive(active);
        return AlertResponse.of(alert);
    }

    @Transactional
    public void delete(long candidateId, long alertId) {
        alerts.delete(owned(candidateId, alertId));
    }

    private JobAlert owned(long candidateId, long alertId) {
        return alerts.findByIdAndCandidateId(alertId, candidateId)
                .orElseThrow(() -> ApiException.notFound("alert", alertId));
    }

    // ---------------------------------------------------------------- "stop my alerts" link

    /**
     * Switches off every alert of the person the signed token belongs to.
     * يوقف كل تنبيهات صاحب الرمز الموقّع.
     */
    @Transactional
    public int unsubscribe(String token) {
        long userId = tokens.verify(token).orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        return alerts.deactivateAllOf(userId);
    }

    // ---------------------------------------------------------------- matching a new job

    /**
     * Called inside the transaction that publishes a job. Queues one email per matching candidate; if the
     * transaction rolls back, no alert is sent for a job that never got published.
     * يُستدعى داخل عملية نشر الوظيفة. يضيف إيميل واحد لكل مرشح مطابق؛ ولو تراجعت العملية ما يُرسل تنبيه لوظيفة ما اننشرت.
     */
    @Transactional
    public int notifyMatching(Job job) {
        String company = job.getEmployer().getCompanyName();
        String haystack = (job.getTitle() + " " + job.getDescription() + " " + company).toLowerCase(Locale.ROOT);
        Set<Long> alreadyNotified = new HashSet<>();
        int sent = 0;
        for (JobAlert alert : alerts.findMatchingTypeAndCity(job.getType(), job.getCity().toLowerCase(Locale.ROOT))) {
            AppUser candidate = alert.getCandidate();
            // One email per person per job, even if two of their alerts match. / إيميل واحد لكل شخص لكل وظيفة، حتى لو تطابق تنبيهين له.
            if (!keywordMatches(alert.getKeyword(), haystack) || alreadyNotified.contains(candidate.getId())) {
                continue;
            }
            if (recentDeliveries(candidate.getId()) >= limits.dailyLimit()) {
                continue;
            }
            // The primary key (candidate, job) makes a duplicate insert do nothing, a second safety net.
            // المفتاح الأساسي (مرشح، وظيفة) يخلي الإدخال المكرر ما يسوي شي، شبكة أمان ثانية.
            int inserted = jdbc.sql("insert into job_alert_delivery (candidate_id, job_id) values (?, ?) "
                    + "on conflict do nothing").params(candidate.getId(), job.getId()).update();
            if (inserted == 0) {
                continue;
            }
            alreadyNotified.add(candidate.getId());
            outbox.enqueue(candidate.getEmail(), candidate.getLanguage(), EmailTemplate.JOB_ALERT,
                    Map.of("name", candidate.getFullName(), "jobTitle", job.getTitle(), "company", company,
                            "city", job.getCity(), "link", links.url("/jobs/" + job.getId()),
                            "unsubscribe", links.url("/unsubscribe#" + tokens.create(candidate.getId()))));
            sent++;
        }
        return sent;
    }

    private long recentDeliveries(long candidateId) {
        return jdbc.sql("select count(*) from job_alert_delivery where candidate_id = ? and created_at > ?")
                .params(candidateId, java.sql.Timestamp.from(Instant.now().minus(Duration.ofHours(24))))
                .query(Long.class).single();
    }

    /** Every word of the keyword must appear somewhere in the job. / كل كلمة من الكلمة المفتاحية لازم تظهر في مكان بالوظيفة. */
    static boolean keywordMatches(String keyword, String lowerCaseJobText) {
        if (keyword == null) {
            return true;
        }
        String[] words = keyword.trim().toLowerCase(Locale.ROOT).split("\\s+");
        for (int i = 0; i < Math.min(words.length, MAX_KEYWORD_WORDS); i++) {
            if (!lowerCaseJobText.contains(words[i])) {
                return false;
            }
        }
        return true;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
