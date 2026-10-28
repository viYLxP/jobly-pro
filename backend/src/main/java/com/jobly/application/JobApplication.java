package com.jobly.application;

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

import com.jobly.job.Job;
import com.jobly.user.AppUser;

/**
 * One candidate's application to one job.
 * طلب تقديم واحد من مرشح واحد على وظيفة واحدة.
 */
@Entity
@Table(name = "job_application")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id")
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id")
    private AppUser candidate;

    @Column(name = "cover_note")
    private String coverNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = createdAt;

    // Two employers changing the same application at once: the second gets a conflict instead of overwriting.
    // صاحبي عمل يغيّرون نفس الطلب بنفس الوقت: الثاني ياخذ تعارض بدل ما يكتب فوق الأول.
    @Version
    private long version;

    // Required by JPA. / مطلوب من JPA.
    protected JobApplication() {
    }

    public JobApplication(Job job, AppUser candidate, String coverNote) {
        this.job = job;
        this.candidate = candidate;
        this.coverNote = coverNote;
    }

    public void moveTo(ApplicationStatus target, Instant now) {
        this.status = target;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Job getJob() {
        return job;
    }

    public AppUser getCandidate() {
        return candidate;
    }

    public String getCoverNote() {
        return coverNote;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
