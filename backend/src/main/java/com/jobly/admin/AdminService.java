package com.jobly.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobly.admin.AdminDtos.AdminEmail;
import com.jobly.admin.AdminDtos.AdminUser;
import com.jobly.admin.AdminDtos.Stats;
import com.jobly.application.ApplicationStatus;
import com.jobly.common.ApiException;
import com.jobly.common.ErrorCode;
import com.jobly.job.JobService;
import com.jobly.job.JobStatus;
import com.jobly.mail.EmailOutbox;
import com.jobly.mail.EmailOutboxRepository;
import com.jobly.mail.OutboxStatus;
import com.jobly.user.AppUser;
import com.jobly.user.AppUserRepository;
import com.jobly.user.Role;
import com.jobly.user.UserStatus;

/**
 * What an admin can do: look up accounts, suspend and restore them, see platform numbers, and re-queue emails
 * that failed for good.
 * اللي يقدر الأدمن يسويه: يدوّر على الحسابات، يوقفها ويرجّعها، يشوف أرقام المنصة، ويعيد إدخال الإيميلات الفاشلة للطابور.
 */
@Service
public class AdminService {

    private final AppUserRepository users;
    private final EmailOutboxRepository outbox;
    private final JdbcClient jdbc;

    public AdminService(AppUserRepository users, EmailOutboxRepository outbox, JdbcClient jdbc) {
        this.users = users;
        this.outbox = outbox;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Page<AdminUser> users(String q, Role role, UserStatus status, Pageable pageable) {
        Specification<AppUser> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (role != null) {
                all.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                all.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
                all.add(cb.or(cb.like(cb.lower(root.get("email")), pattern, '\\'),
                        cb.like(cb.lower(root.get("fullName")), pattern, '\\')));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
        Pageable newest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return users.findAll(spec, newest).map(AdminService::toDto);
    }

    @Transactional
    public AdminUser suspend(long adminId, long userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> ApiException.notFound("user", userId));
        if (user.getId().equals(adminId)) {
            throw new ApiException(ErrorCode.CANNOT_MODIFY_SELF);
        }
        // Admin accounts are not managed here, so one admin cannot lock out another.
        // حسابات الأدمن ما تُدار من هنا، فما يقدر أدمن يقفل على أدمن ثاني.
        if (user.getRole() == Role.ADMIN) {
            throw new ApiException(ErrorCode.INVALID_STATUS);
        }
        user.suspend();
        return toDto(user);
    }

    @Transactional
    public AdminUser restore(long userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> ApiException.notFound("user", userId));
        if (user.getStatus() != UserStatus.SUSPENDED) {
            throw new ApiException(ErrorCode.INVALID_STATUS);
        }
        user.unsuspend();
        return toDto(user);
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        return new Stats(
                counts("select role, count(*) from app_user group by role", Role.values()),
                counts("select status, count(*) from app_user group by status", UserStatus.values()),
                counts("select status, count(*) from job group by status", JobStatus.values()),
                counts("select status, count(*) from job_application group by status", ApplicationStatus.values()),
                jdbc.sql("select count(*) from job_alert where active").query(Long.class).single(),
                counts("select status, count(*) from email_outbox group by status", OutboxStatus.values()));
    }

    @Transactional(readOnly = true)
    public Page<AdminEmail> emails(OutboxStatus status, Pageable pageable) {
        Pageable newest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("id")));
        Page<EmailOutbox> page = status == null ? outbox.findAll(newest) : outbox.findByStatus(status, newest);
        return page.map(e -> new AdminEmail(e.getId(), e.getToEmail(), e.getTemplate(), e.getStatus(), e.getAttempts(),
                e.getLastError(), e.getCreatedAt(), e.getSentAt()));
    }

    /**
     * Gives a FAILED email another round of attempts, for example after the mail provider was fixed.
     * يعطي إيميل فاشل جولة محاولات جديدة، مثلًا بعد إصلاح مزوّد البريد.
     */
    @Transactional
    public void retryEmail(long emailId) {
        EmailOutbox email = outbox.findById(emailId).orElseThrow(() -> ApiException.notFound("email", emailId));
        if (email.getStatus() != OutboxStatus.FAILED) {
            throw new ApiException(ErrorCode.INVALID_STATUS);
        }
        email.retry(java.time.Instant.now());
    }

    private Map<String, Long> counts(String sql, Enum<?>[] possible) {
        // Start every known value at 0, so the UI never has to guess a missing key.
        // نبدأ كل قيمة معروفة من 0، فالواجهة ما تحتاج تخمّن مفتاح ناقص.
        Map<String, Long> result = new LinkedHashMap<>();
        for (Enum<?> value : possible) {
            result.put(value.name(), 0L);
        }
        jdbc.sql(sql).query((rs, row) -> {
            result.put(rs.getString(1), rs.getLong(2));
            return null;
        }).list();
        return result;
    }

    private static AdminUser toDto(AppUser u) {
        return new AdminUser(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getStatus(),
                u.getCompanyName(), u.getCreatedAt(), u.getVerifiedAt());
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
