package com.jobly.alert;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.jobly.job.JobType;

public final class AlertDtos {

    private AlertDtos() {
    }

    public record AlertRequest(@Size(max = 100) String keyword, @Size(max = 80) String city, JobType type) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    public record AlertResponse(long id, String keyword, String city, JobType type, boolean active,
                                Instant createdAt) {

        public static AlertResponse of(JobAlert alert) {
            return new AlertResponse(alert.getId(), alert.getKeyword(), alert.getCity(), alert.getType(),
                    alert.isActive(), alert.getCreatedAt());
        }
    }

    public record UnsubscribeRequest(@NotNull @Size(max = 200) String token) {
    }

    public record UnsubscribeResponse(String status, int stopped) {
    }
}
