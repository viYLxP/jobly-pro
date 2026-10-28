package com.jobly.job;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Request and response shapes for jobs.
 * أشكال الطلبات والردود للوظائف.
 */
public final class JobDtos {

    private JobDtos() {
    }

    public record JobRequest(
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 8000) String description,
            @NotBlank @Size(max = 80) String city,
            @NotNull JobType type,
            @PositiveOrZero Integer salaryMin,
            @PositiveOrZero Integer salaryMax) {
    }

    /** One row of the public list. / صف واحد من القائمة العامة. */
    public record JobSummary(long id, String title, String companyName, String city, JobType type,
                             Integer salaryMin, Integer salaryMax, Instant publishedAt) {

        public static JobSummary of(Job job) {
            return new JobSummary(job.getId(), job.getTitle(), job.getEmployer().getCompanyName(), job.getCity(),
                    job.getType(), job.getSalaryMin(), job.getSalaryMax(), job.getPublishedAt());
        }
    }

    /** The public page of one job. / الصفحة العامة لوظيفة واحدة. */
    public record JobDetail(long id, String title, String description, String companyName, String city, JobType type,
                            Integer salaryMin, Integer salaryMax, JobStatus status, Instant publishedAt) {

        public static JobDetail of(Job job) {
            return new JobDetail(job.getId(), job.getTitle(), job.getDescription(),
                    job.getEmployer().getCompanyName(), job.getCity(), job.getType(), job.getSalaryMin(),
                    job.getSalaryMax(), job.getStatus(), job.getPublishedAt());
        }
    }

    /** What the owner sees: every status, plus how many people applied. / اللي يشوفه صاحب الوظيفة: كل الحالات مع عدد المتقدمين. */
    public record EmployerJob(long id, String title, String description, String city, JobType type,
                              Integer salaryMin, Integer salaryMax, JobStatus status, Instant createdAt,
                              Instant publishedAt, Instant closedAt, long applicantCount) {

        public static EmployerJob of(Job job, long applicantCount) {
            return new EmployerJob(job.getId(), job.getTitle(), job.getDescription(), job.getCity(), job.getType(),
                    job.getSalaryMin(), job.getSalaryMax(), job.getStatus(), job.getCreatedAt(),
                    job.getPublishedAt(), job.getClosedAt(), applicantCount);
        }
    }
}
