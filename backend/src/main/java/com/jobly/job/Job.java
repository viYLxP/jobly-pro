package com.jobly.job;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.jobly.user.AppUser;

/**
 * A job posting owned by one employer account.
 * إعلان وظيفة يملكه حساب صاحب عمل واحد.
 */
@Entity
@Table(name = "job")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: loading a job does not load its employer unless we ask (the list queries ask explicitly).
    // LAZY: تحميل الوظيفة ما يحمّل صاحب العمل إلا لو طلبنا (استعلامات القوائم تطلبه صراحة).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employer_id")
    private AppUser employer;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private JobType type;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private long version;

    // Required by JPA. / مطلوب من JPA.
    protected Job() {
    }

    public Job(AppUser employer, String title, String description, String city, JobType type,
               Integer salaryMin, Integer salaryMax) {
        this.employer = employer;
        edit(title, description, city, type, salaryMin, salaryMax);
    }

    public final void edit(String title, String description, String city, JobType type,
                           Integer salaryMin, Integer salaryMax) {
        this.title = title;
        this.description = description;
        this.city = city;
        this.type = type;
        this.salaryMin = salaryMin;
        this.salaryMax = salaryMax;
    }

    public void publish(Instant now) {
        this.status = JobStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public void close(Instant now) {
        this.status = JobStatus.CLOSED;
        this.closedAt = now;
    }

    public Long getId() {
        return id;
    }

    public AppUser getEmployer() {
        return employer;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCity() {
        return city;
    }

    public JobType getType() {
        return type;
    }

    public Integer getSalaryMin() {
        return salaryMin;
    }

    public Integer getSalaryMax() {
        return salaryMax;
    }

    public JobStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public long getVersion() {
        return version;
    }
}
