package com.jobly.admin;

import java.time.Instant;
import java.util.Map;

import com.jobly.mail.EmailTemplate;
import com.jobly.mail.OutboxStatus;
import com.jobly.user.Role;
import com.jobly.user.UserStatus;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminUser(long id, String email, String fullName, Role role, UserStatus status,
                            String companyName, Instant createdAt, Instant verifiedAt) {
    }

    /** A health picture of the platform. Every possible value is listed, with 0 when there are none. / صورة صحية للمنصة. كل قيمة ممكنة مذكورة، بـ 0 لو ما فيه. */
    public record Stats(Map<String, Long> usersByRole, Map<String, Long> usersByStatus,
                        Map<String, Long> jobsByStatus, Map<String, Long> applicationsByStatus,
                        long activeAlerts, Map<String, Long> emailsByStatus) {
    }

    public record AdminEmail(long id, String to, EmailTemplate template, OutboxStatus status, int attempts,
                             String lastError, Instant createdAt, Instant sentAt) {
    }
}
