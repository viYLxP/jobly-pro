package com.jobly.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One account: a candidate, an employer or an admin.
 * حساب واحد: مرشح أو صاحب عمل أو أدمن.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Always stored lower-case and trimmed (see AuthService), so lookups are simple.
    // نخزّنه دائمًا بأحرف صغيرة وبدون مسافات (شوف AuthService)، فالبحث يكون بسيط.
    @Column(nullable = false)
    private String email;

    // BCrypt hash. The real password is never stored or logged.
    // بصمة BCrypt. كلمة المرور الأصلية ما تنخزّن ولا تنكتب في السجلات.
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    // Language of the emails we send to this user: "ar" or "en".
    // لغة الإيميلات اللي نرسلها لهذا المستخدم: "ar" أو "en".
    @Column(nullable = false)
    private String language = "ar";

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    // Optimistic locking: two edits at the same moment cannot silently overwrite each other.
    // القفل المتفائل: تعديلين بنفس اللحظة ما يقدر واحد يكتب فوق الثاني بصمت.
    @Version
    private long version;

    // Required by JPA. / مطلوب من JPA.
    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, String fullName, Role role, UserStatus status,
                   String language, String companyName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
        this.language = language;
        this.companyName = companyName;
        if (status == UserStatus.ACTIVE) {
            this.verifiedAt = createdAt;
        }
    }

    /**
     * Marks the email as verified and lets the user in.
     * يعلّم الإيميل كمفعّل ويسمح للمستخدم بالدخول.
     */
    public void activate(Instant now) {
        this.status = UserStatus.ACTIVE;
        this.verifiedAt = now;
    }

    /**
     * Stores a new password hash and remembers when, so older login tokens stop working.
     * يحفظ بصمة كلمة مرور جديدة ويتذكر الوقت، فتتوقف رموز الدخول الأقدم.
     */
    public void changePassword(String newHash, Instant now) {
        this.passwordHash = newHash;
        this.passwordChangedAt = now;
    }

    /**
     * A second registration with the same email while still unverified: the latest details win.
     * تسجيل ثاني بنفس الإيميل والحساب لسه غير مفعّل: آخر بيانات هي المعتمدة.
     */
    public void restartRegistration(String passwordHash, String fullName, Role role, String language,
                                    String companyName, Instant now) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = now;
        this.fullName = fullName;
        this.role = role;
        this.language = language;
        this.companyName = companyName;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getLanguage() {
        return language;
    }

    public String getCompanyName() {
        return companyName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public long getVersion() {
        return version;
    }
}
