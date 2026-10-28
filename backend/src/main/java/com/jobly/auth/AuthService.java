package com.jobly.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobly.auth.AuthDtos.LoginResponse;
import com.jobly.auth.AuthDtos.RegisterRequest;
import com.jobly.common.ApiException;
import com.jobly.common.ErrorCode;
import com.jobly.common.SecureTokens;
import com.jobly.config.AppProperties;
import com.jobly.mail.EmailTemplate;
import com.jobly.mail.OutboxService;
import com.jobly.user.AppUser;
import com.jobly.user.AppUserRepository;
import com.jobly.user.Role;
import com.jobly.user.UserDto;
import com.jobly.user.UserStatus;

/**
 * Registration, email verification, login and password reset.
 * التسجيل وتفعيل الإيميل وتسجيل الدخول واستعادة كلمة المرور.
 *
 * <p>Two rules run through every method: (1) a public endpoint never reveals whether an email has an account
 * (it answers the same either way), and (2) emails are only queued here; sending happens later in the outbox.</p>
 * <p>قاعدتان في كل الدوال: (1) الرابط العام ما يكشف هل الإيميل عنده حساب (نفس الرد في الحالتين)،
 * و(2) هنا نضيف الإيميلات للطابور فقط؛ والإرسال الفعلي يصير لاحقًا من صندوق الإيميلات.</p>
 */
@Service
public class AuthService {

    public enum VerifyResult { VERIFIED, ALREADY_VERIFIED }

    private final AppUserRepository users;
    private final EmailTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final OutboxService outbox;
    private final TokenService jwt;
    private final AppProperties props;

    // A hash of a throwaway password. When the email is unknown we still run a BCrypt check against it, so a
    // login for an unknown email takes as long as a wrong password and timing reveals nothing.
    // بصمة لكلمة مرور وهمية. لما الإيميل مجهول نشغّل فحص BCrypt عليها برضو، فيأخذ الدخول بإيميل مجهول نفس وقت
    // كلمة المرور الخاطئة، والتوقيت ما يكشف شي.
    private final String dummyHash;

    public AuthService(AppUserRepository users, EmailTokenRepository tokens, PasswordEncoder encoder,
                       OutboxService outbox, TokenService jwt, AppProperties props) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.outbox = outbox;
        this.jwt = jwt;
        this.props = props;
        this.dummyHash = encoder.encode("not-a-real-password-1");
    }

    // ---------------------------------------------------------------- register

    /**
     * Creates a PENDING account and queues the verification email.
     * ينشئ حساب PENDING ويضيف إيميل التفعيل للطابور.
     *
     * <p>If the email already has an account we do NOT say so in the response. A still-unverified account gets a
     * fresh link (latest details win); an active one gets an "account exists" email.</p>
     * <p>لو الإيميل عنده حساب فعلًا ما نقول ذلك في الرد. الحساب غير المفعّل ياخذ رابط جديد (آخر بيانات تُعتمد)؛
     * والمفعّل ياخذ إيميل "الحساب موجود".</p>
     */
    @Transactional
    public void register(RegisterRequest request) {
        PasswordRules.requireAcceptable(request.password());
        Role role = request.role();
        // Admins are created by the seeder only, never through the public form.
        // الأدمن يُنشأ من الـ seeder فقط، وليس من النموذج العام.
        if (role == Role.ADMIN) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("role", "Invalid")));
        }
        String company = blankToNull(request.companyName());
        if (role == Role.EMPLOYER && company == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("companyName", "NotBlank")));
        }
        if (role == Role.CANDIDATE) {
            company = null;
        }
        String email = normalize(request.email());
        String language = request.language() == null ? "ar" : request.language();
        String fullName = request.fullName().trim();
        Instant now = Instant.now();

        Optional<AppUser> existing = users.findByEmailIgnoreCase(email);
        if (existing.isEmpty()) {
            AppUser user = users.save(new AppUser(email, encoder.encode(request.password()), fullName, role,
                    UserStatus.PENDING, language, company));
            sendVerification(user, now, false);
            return;
        }
        AppUser user = existing.get();
        if (user.getStatus() == UserStatus.PENDING) {
            user.restartRegistration(encoder.encode(request.password()), fullName, role, language, company, now);
            sendVerification(user, now, true);
        } else {
            outbox.enqueue(user.getEmail(), user.getLanguage(), EmailTemplate.ACCOUNT_EXISTS,
                    Map.of("name", user.getFullName(), "link", url("/login")));
        }
    }

    // ---------------------------------------------------------------- verification

    /**
     * Activates the account that the link belongs to.
     * يفعّل الحساب اللي يخصه الرابط.
     */
    @Transactional
    public VerifyResult verifyEmail(String rawToken) {
        Instant now = Instant.now();
        EmailToken token = tokens.findByTokenHash(SecureTokens.sha256(rawToken))
                .filter(t -> t.getType() == EmailTokenType.VERIFY_EMAIL)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        AppUser user = token.getUser();

        if (token.isUsed()) {
            // Clicking the link twice, or an older link after a newer one worked: friendly if already verified.
            // ضغط الرابط مرتين، أو رابط أقدم بعد ما اشتغل الأحدث: نرد بلطف لو الحساب مفعّل فعلًا.
            if (user.getStatus() == UserStatus.ACTIVE) {
                return VerifyResult.ALREADY_VERIFIED;
            }
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        if (token.isExpired(now)) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }
        token.markUsed(now);
        if (user.getStatus() == UserStatus.PENDING) {
            user.activate(now);
            outbox.enqueue(user.getEmail(), user.getLanguage(), EmailTemplate.WELCOME,
                    Map.of("name", user.getFullName(), "link", url("/login")));
        }
        return VerifyResult.VERIFIED;
    }

    /**
     * Sends a new verification link if the account is still PENDING and limits allow. Always silent: the caller
     * answers the same whether or not anything was sent.
     * يرسل رابط تفعيل جديد لو الحساب لسه PENDING والحدود تسمح. دائمًا بصمت: المستدعي يرد نفس الرد سواء انرسل شي أو لا.
     */
    @Transactional
    public void resendVerification(String rawEmail) {
        users.findByEmailIgnoreCase(normalize(rawEmail))
                .filter(u -> u.getStatus() == UserStatus.PENDING)
                .ifPresent(user -> sendVerification(user, Instant.now(), true));
    }

    // ---------------------------------------------------------------- login

    @Transactional(readOnly = true)
    public LoginResponse login(String rawEmail, String password) {
        AppUser user = users.findByEmailIgnoreCase(normalize(rawEmail)).orElse(null);
        // A password longer than BCrypt's limit can never match, and BCrypt would throw on it.
        // كلمة مرور أطول من حد BCrypt ما تتطابق أبدًا، وBCrypt يرمي استثناء عليها.
        boolean matches = !PasswordRules.tooLong(password)
                && encoder.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) {
            throw new ApiException(ErrorCode.BAD_CREDENTIALS);
        }
        // The state is revealed only AFTER the password was right, so these answers do not expose which emails exist.
        // الحالة تنكشف فقط بعد ما تكون كلمة المرور صحيحة، فهذي الردود ما تكشف أي إيميلات موجودة.
        if (user.getStatus() == UserStatus.PENDING) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        TokenService.IssuedToken token = jwt.issue(user);
        return new LoginResponse(token.value(), token.expiresAt(), UserDto.of(user));
    }

    // ---------------------------------------------------------------- password reset

    /**
     * Queues a reset link for an active account. Silent for unknown emails, pending/suspended accounts and when
     * the limits are reached.
     * يضيف رابط استعادة لحساب مفعّل. صامت للإيميل المجهول والحسابات المعلّقة/الموقوفة وعند بلوغ الحدود.
     */
    @Transactional
    public void forgotPassword(String rawEmail) {
        users.findByEmailIgnoreCase(normalize(rawEmail))
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    Instant now = Instant.now();
                    if (!mayIssue(user, EmailTokenType.RESET_PASSWORD, now)) {
                        return;
                    }
                    String raw = issueToken(user, EmailTokenType.RESET_PASSWORD, props.auth().resetValidity(), now);
                    outbox.enqueue(user.getEmail(), user.getLanguage(), EmailTemplate.RESET_PASSWORD,
                            Map.of("name", user.getFullName(), "link", url("/reset-password#" + raw)));
                });
    }

    /**
     * Sets the new password. The link is single-use, every other reset link dies with it, and login tokens issued
     * before now stop working.
     * يضبط كلمة المرور الجديدة. الرابط لمرة واحدة، وكل روابط الاستعادة الثانية تموت معه، ورموز الدخول الصادرة قبل الآن تتوقف.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordRules.requireAcceptable(newPassword);
        Instant now = Instant.now();
        EmailToken token = tokens.findByTokenHash(SecureTokens.sha256(rawToken))
                .filter(t -> t.getType() == EmailTokenType.RESET_PASSWORD)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        if (token.isUsed()) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        if (token.isExpired(now)) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }
        AppUser user = token.getUser();
        token.markUsed(now);
        tokens.invalidateUnused(user.getId(), EmailTokenType.RESET_PASSWORD, now);
        user.changePassword(encoder.encode(newPassword), now);
        outbox.enqueue(user.getEmail(), user.getLanguage(), EmailTemplate.PASSWORD_CHANGED,
                Map.of("name", user.getFullName(), "link", url("/forgot-password")));
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Issues a verification link and queues the email, unless the limits say to wait.
     * يصدر رابط تفعيل ويضيف الإيميل للطابور، إلا لو الحدود تقول ننتظر.
     */
    private void sendVerification(AppUser user, Instant now, boolean enforceLimits) {
        if (enforceLimits && !mayIssue(user, EmailTokenType.VERIFY_EMAIL, now)) {
            return;
        }
        String raw = issueToken(user, EmailTokenType.VERIFY_EMAIL, props.auth().verifyValidity(), now);
        outbox.enqueue(user.getEmail(), user.getLanguage(), EmailTemplate.VERIFY_EMAIL,
                Map.of("name", user.getFullName(), "link", url("/verify-email#" + raw)));
    }

    /**
     * Cooldown between two emails of one kind, plus a cap per 24 hours, so nobody can use us to flood a mailbox.
     * فاصل بين إيميلين من نفس النوع، وحد أعلى لكل 24 ساعة، حتى ما أحد يستخدمنا لإغراق صندوق بريد.
     */
    private boolean mayIssue(AppUser user, EmailTokenType type, Instant now) {
        Optional<EmailToken> last = tokens.findFirstByUserIdAndTypeOrderByCreatedAtDesc(user.getId(), type);
        if (last.isPresent() && last.get().getCreatedAt().plus(props.auth().resendCooldown()).isAfter(now)) {
            return false;
        }
        return tokens.countByUserIdAndTypeAndCreatedAtAfter(user.getId(), type, now.minus(Duration.ofHours(24)))
                < props.auth().dailyLimit();
    }

    /**
     * Creates a new token and retires older unused ones of the same kind. Returns the raw secret for the email
     * link; only its hash is stored.
     * ينشئ رمزًا جديدًا ويسحب الأقدم غير المستخدم من نفس النوع. يرجّع السر الخام لرابط الإيميل؛ ونخزّن بصمته فقط.
     */
    private String issueToken(AppUser user, EmailTokenType type, Duration validity, Instant now) {
        tokens.invalidateUnused(user.getId(), type, now);
        String raw = SecureTokens.generate();
        tokens.save(new EmailToken(user, type, SecureTokens.sha256(raw), now, now.plus(validity)));
        return raw;
    }

    // The part after "#" never reaches web servers or their logs, so the secret stays in the browser.
    // الجزء بعد "#" ما يوصل لخوادم الويب ولا سجلاتها، فيبقى السر في المتصفح.
    private String url(String path) {
        String base = props.publicUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
