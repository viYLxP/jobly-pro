package com.jobly.auth;

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

import com.jobly.user.AppUser;

/**
 * A one-time secret sent by email (verify the address, or reset the password). We keep only its hash.
 * سر لمرة واحدة يُرسل بالإيميل (تفعيل العنوان، أو استعادة كلمة المرور). نحتفظ ببصمته فقط.
 */
@Entity
@Table(name = "email_token")
public class EmailToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailTokenType type;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Required by JPA. / مطلوب من JPA.
    protected EmailToken() {
    }

    public EmailToken(AppUser user, EmailTokenType type, String tokenHash, Instant now, Instant expiresAt) {
        this.user = user;
        this.type = type;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public AppUser getUser() {
        return user;
    }

    public EmailTokenType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
