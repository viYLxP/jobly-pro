package com.jobly.application;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One step in an application's history, for the candidate's timeline.
 * خطوة واحدة في تاريخ الطلب، لخط زمني يشوفه المرشح.
 */
@Entity
@Table(name = "application_event")
public class ApplicationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Required by JPA. / مطلوب من JPA.
    protected ApplicationEvent() {
    }

    public ApplicationEvent(Long applicationId, ApplicationStatus status, Instant createdAt) {
        this.applicationId = applicationId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
