package com.jobly.alert;

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

import com.jobly.job.JobType;
import com.jobly.user.AppUser;

/**
 * A saved search: when a job matching it is published, the candidate is emailed.
 * بحث محفوظ: لما تنشر وظيفة تطابقه، يوصل المرشح إيميل.
 */
@Entity
@Table(name = "job_alert")
public class JobAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id")
    private AppUser candidate;

    // Words that must all appear in the job's title, description or company name. / كلمات لازم تظهر كلها في عنوان الوظيفة أو وصفها أو اسم الشركة.
    private String keyword;

    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type")
    private JobType type;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Required by JPA. / مطلوب من JPA.
    protected JobAlert() {
    }

    public JobAlert(AppUser candidate, String keyword, String city, JobType type) {
        this.candidate = candidate;
        this.keyword = keyword;
        this.city = city;
        this.type = type;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public AppUser getCandidate() {
        return candidate;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getCity() {
        return city;
    }

    public JobType getType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
