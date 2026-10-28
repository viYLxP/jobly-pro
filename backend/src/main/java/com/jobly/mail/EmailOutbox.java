package com.jobly.mail;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

/**
 * One email waiting to be delivered (or already delivered). Rows are created in the same transaction as the
 * change that causes the email, so an email is never promised and then lost.
 * إيميل واحد ينتظر التوصيل (أو تم توصيله). الصفوف تُنشأ بنفس عملية التغيير اللي سببت الإيميل،
 * فما نوعد بإيميل ثم يضيع.
 */
@Entity
@Table(name = "email_outbox")
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_email", nullable = false)
    private String toEmail;

    @Column(nullable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    // Values that fill the template, such as the person's name and the link.
    // قيم تعبّي القالب، مثل اسم الشخص والرابط.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "email_outbox_param", joinColumns = @JoinColumn(name = "outbox_id"))
    @MapKeyColumn(name = "name")
    @Column(name = "value", nullable = false)
    private Map<String, String> params = new HashMap<>();

    // Required by JPA. / مطلوب من JPA.
    protected EmailOutbox() {
    }

    public EmailOutbox(String toEmail, String language, EmailTemplate template, Map<String, String> params) {
        this.toEmail = toEmail;
        this.language = language;
        this.template = template;
        this.params = new HashMap<>(params);
    }

    public void markSent(Instant now) {
        this.status = OutboxStatus.SENT;
        this.sentAt = now;
        this.attempts++;
        this.lastError = null;
    }

    /**
     * Records a failure. Below the limit we try again later; at the limit we give up.
     * يسجّل فشل. تحت الحد نعيد المحاولة لاحقًا؛ وعند الحد نستسلم.
     */
    public void markFailed(String error, Instant nextAttemptAt, boolean giveUp) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        this.nextAttemptAt = nextAttemptAt;
        if (giveUp) {
            this.status = OutboxStatus.FAILED;
        }
    }

    public Long getId() {
        return id;
    }

    public String getToEmail() {
        return toEmail;
    }

    public String getLanguage() {
        return language;
    }

    public EmailTemplate getTemplate() {
        return template;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Map<String, String> getParams() {
        return params;
    }
}
