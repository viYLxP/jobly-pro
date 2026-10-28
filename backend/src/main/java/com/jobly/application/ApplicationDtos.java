package com.jobly.application;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request and response shapes for applications.
 * أشكال الطلبات والردود لطلبات التقديم.
 */
public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    public record ApplyRequest(@Size(max = 2000) String coverNote) {
    }

    public record StatusRequest(@NotNull ApplicationStatus status) {
    }

    public record TimelineEntry(ApplicationStatus status, Instant at) {
    }

    /** What a candidate sees about their own application. / اللي يشوفه المرشح عن طلبه. */
    public record MyApplication(long id, long jobId, String jobTitle, String companyName, ApplicationStatus status,
                                String coverNote, Instant createdAt, Instant updatedAt,
                                List<TimelineEntry> timeline) {
    }

    /** The candidate as an employer sees them: contact details and the shared profile. / المرشح كما يشوفه صاحب العمل: بيانات التواصل والملف المشارك. */
    public record CandidateInfo(long id, String fullName, String email, String headline, String city,
                                Integer yearsExperience, String summary) {
    }

    /** One applicant in an employer's list. / متقدم واحد في قائمة صاحب العمل. */
    public record Applicant(long id, long jobId, String jobTitle, ApplicationStatus status, String coverNote,
                            Instant createdAt, Instant updatedAt, CandidateInfo candidate) {
    }
}
