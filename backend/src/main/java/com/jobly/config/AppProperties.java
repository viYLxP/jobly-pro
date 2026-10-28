package com.jobly.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed application settings bound from the {@code jobly.*} section of application.yml.
 * إعدادات التطبيق مربوطة بأنواع واضحة من قسم {@code jobly.*} في application.yml.
 *
 * <p>{@code @Validated} makes the app refuse to start with an invalid configuration
 * (for example a JWT secret that is too short), instead of failing later at runtime.</p>
 * <p>{@code @Validated} يمنع التطبيق من التشغيل إذا كانت الإعدادات غير صالحة
 * (مثل مفتاح JWT قصير)، بدل ما يفشل لاحقًا أثناء الاستخدام.</p>
 */
@Validated
@ConfigurationProperties(prefix = "jobly")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        // Business time zone (Asia/Riyadh by default). / المنطقة الزمنية للنشاط (الرياض افتراضيًا).
        @NotNull ZoneId timeZone,
        // Public address of the web app; email links are built from it. / عنوان التطبيق العام؛ منه نبني روابط الإيميل.
        @NotBlank String publicUrl,
        @Valid @NotNull Seed seed,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Auth auth,
        @Valid @NotNull Mail mail,
        @Valid @NotNull Alerts alerts) {

    /**
     * JWT signing settings.
     * إعدادات توقيع رموز JWT.
     *
     * @param secret     HMAC-SHA256 key; at least 32 chars (256 bits) as required by the algorithm
     *                   / مفتاح HMAC-SHA256 ولازم 32 حرفًا على الأقل (256 بت) حسب متطلبات الخوارزمية
     * @param expiration how long a token stays valid / مدة صلاحية الرمز
     */
    public record Jwt(@NotNull @Size(min = 32) String secret, @NotNull Duration expiration) {
    }

    /**
     * Allowed browser origins for CORS (the Angular dev server in development).
     * مصادر المتصفح المسموح لها عبر CORS (خادم Angular أثناء التطوير).
     */
    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    /**
     * Demo data settings. Passwords come from environment variables, never hard-coded in production.
     * إعدادات البيانات التجريبية. كلمات المرور من متغيرات البيئة، وليست مكتوبة في الكود في الإنتاج.
     */
    public record Seed(boolean enabled, @NotBlank String adminPassword, @NotBlank String candidatePassword,
                       @NotBlank String employerPassword) {
    }

    /**
     * Limits per visitor address on the public account endpoints (login, register, reset...).
     * حدود لكل عنوان زائر على روابط الحسابات العامة (الدخول، التسجيل، الاستعادة...).
     */
    public record RateLimit(@NotNull Duration window, @Min(1) int attempts) {
    }

    /**
     * Rules for the one-time email links.
     * قواعد روابط الإيميل لمرة واحدة.
     *
     * @param verifyValidity  how long a verification link works / مدة صلاحية رابط التفعيل
     * @param resetValidity   how long a password-reset link works / مدة صلاحية رابط استعادة كلمة المرور
     * @param resendCooldown  minimum gap between two emails of the same kind to one account / أقل فاصل بين إيميلين من نفس النوع لنفس الحساب
     * @param dailyLimit      maximum emails of one kind per account in 24 hours / أقصى عدد إيميلات من نوع واحد لحساب في 24 ساعة
     */
    public record Auth(@NotNull Duration verifyValidity, @NotNull Duration resetValidity,
                       @NotNull Duration resendCooldown, @Min(1) int dailyLimit) {
    }

    /**
     * Email sending settings.
     * إعدادات إرسال الإيميل.
     *
     * @param from             sender address shown to the reader / عنوان المرسل الظاهر للقارئ
     * @param schedulerEnabled turn the background sender on or off (tests call it directly) / تشغيل المرسل في الخلفية أو إيقافه (الاختبارات تستدعيه مباشرة)
     * @param pollInterval     how often the sender looks for due emails / كل كم يفحص المرسل الإيميلات المستحقة
     * @param batchSize        emails handled per run / عدد الإيميلات في كل دورة
     * @param maxAttempts      after this many failures an email is marked FAILED / بعد هذا العدد من الفشل يُعلَّم الإيميل FAILED
     * @param retryDelay       first retry delay; doubles with each failed attempt / أول تأخير للإعادة؛ يتضاعف مع كل فشل
     */
    public record Mail(@NotBlank String from, boolean schedulerEnabled, @NotNull Duration pollInterval,
                       @Min(1) int batchSize, @Min(1) int maxAttempts, @NotNull Duration retryDelay) {
    }

    /**
     * Job alert limits.
     * حدود تنبيهات الوظائف.
     *
     * @param maxPerCandidate how many alerts one candidate may save / كم تنبيه يقدر المرشح الواحد يحفظ
     * @param dailyLimit      most alert emails one candidate gets in 24 hours, however many jobs match / أقصى عدد إيميلات تنبيه لمرشح في 24 ساعة مهما تطابقت الوظائف
     */
    public record Alerts(@Min(1) int maxPerCandidate, @Min(1) int dailyLimit) {
    }
}
