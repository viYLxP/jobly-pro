package com.jobly.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jobly.common.SecureTokens;
import com.jobly.support.IntegrationTest;
import com.jobly.user.Role;

/**
 * Register -> email link -> verified account, and everything that can go wrong on the way.
 * تسجيل -> رابط الإيميل -> حساب مفعّل، وكل شي ممكن يغلط في الطريق.
 */
class RegistrationAndVerificationTest extends IntegrationTest {

    @Test
    @DisplayName("register creates a PENDING account and sends one verification email with a working link")
    void registerSendsVerificationEmail() throws Exception {
        registerCandidate("sara@example.com")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CHECK_EMAIL"));

        assertThat(statusOf("sara@example.com")).isEqualTo("PENDING");
        MimeMessage email = singleEmailTo("sara@example.com");
        assertThat(email.getSubject()).isEqualTo("Verify your Jobly email");
        assertThat(plainText(email)).contains("http://localhost:4200/verify-email#");

        postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted(tokenIn(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
        assertThat(statusOf("sara@example.com")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("the database keeps only the hash of the link secret, never the secret")
    void onlyTheHashIsStored() throws Exception {
        registerCandidate("sara@example.com").andExpect(status().isAccepted());
        String raw = tokenIn(singleEmailTo("sara@example.com"));

        Integer rawMatches = jdbc.queryForObject("select count(*) from email_token where token_hash = ?",
                Integer.class, raw);
        Integer hashMatches = jdbc.queryForObject("select count(*) from email_token where token_hash = ?",
                Integer.class, SecureTokens.sha256(raw));
        assertThat(rawMatches).isZero();
        assertThat(hashMatches).isEqualTo(1);
    }

    @Test
    @DisplayName("the Arabic account gets an Arabic, right-to-left email")
    void arabicEmail() throws Exception {
        register("noura@example.com", PASSWORD, "نورة", "CANDIDATE", null, "ar").andExpect(status().isAccepted());

        MimeMessage email = singleEmailTo("noura@example.com");
        assertThat(email.getSubject()).contains("فعّل بريدك");
        assertThat(html(email)).contains("dir=\"rtl\"").contains("نورة");
    }

    @Test
    @DisplayName("a name with HTML in it cannot inject markup into the email")
    void nameIsEscapedInTheEmail() throws Exception {
        register("x@example.com", PASSWORD, "<script>alert(1)</script>", "CANDIDATE", null, "en")
                .andExpect(status().isAccepted());

        String html = html(singleEmailTo("x@example.com"));
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("the email address is stored lower-case, so Sara@X.com and sara@x.com are one account")
    void emailIsNormalised() throws Exception {
        registerCandidate("Sara@Example.COM").andExpect(status().isAccepted());
        registerCandidate("SARA@example.com").andExpect(status().isAccepted());

        assertThat(statusOf("sara@example.com")).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("an employer needs a company name; an admin cannot be registered; bad input is rejected")
    void validation() throws Exception {
        register("e@example.com", PASSWORD, "Emp", "EMPLOYER", null, "en")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.companyName").exists());
        register("a@example.com", PASSWORD, "Adm", "ADMIN", null, "en")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.role").exists());
        register("not-an-email", PASSWORD, "Bad", "CANDIDATE", null, "en")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists());
        // No digit / too short / only digits.
        register("w1@example.com", "NoDigitsHere", "W", "CANDIDATE", null, "en")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.password").exists());
        register("w2@example.com", "Ab1", "W", "CANDIDATE", null, "en")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.password").exists());
        register("w3@example.com", "12345678", "W", "CANDIDATE", null, "en")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.password").exists());
        // 40 Arabic letters + digit = 81 bytes: under 72 characters but over BCrypt's 72-byte limit.
        register("w4@example.com", "ع".repeat(40) + "1", "W", "CANDIDATE", null, "en")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.password").exists());
        register("w5@example.com", PASSWORD, "W", "CANDIDATE", null, "fr")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.language").exists());

        assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from email_outbox", Integer.class)).isZero();
    }

    @Test
    @DisplayName("a candidate's company name is ignored; an employer's is kept")
    void companyNameRules() throws Exception {
        register("c@example.com", PASSWORD, "Cand", "CANDIDATE", "Ignored Co", "en").andExpect(status().isAccepted());
        register("e@example.com", PASSWORD, "Emp", "EMPLOYER", "Najd Co", "en").andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("select company_name from app_user where email = 'c@example.com'",
                String.class)).isNull();
        assertThat(jdbc.queryForObject("select company_name from app_user where email = 'e@example.com'",
                String.class)).isEqualTo("Najd Co");
    }

    // ------------------------------------------------------------------ login rules

    @Test
    @DisplayName("login is refused until the email is verified, and only after the password was right")
    void loginNeedsVerification() throws Exception {
        registerCandidate("sara@example.com").andExpect(status().isAccepted());

        loginRequest("sara@example.com", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        // Wrong password on the same pending account: generic answer, so the state is not revealed.
        // كلمة مرور خطأ على نفس الحساب المعلّق: رد عام، فما تنكشف الحالة.
        loginRequest("sara@example.com", "Wrong123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    @DisplayName("an unknown email and a wrong password get the identical answer")
    void unknownEmailLooksLikeWrongPassword() throws Exception {
        activeUser("sara@example.com", PASSWORD, Role.CANDIDATE);

        String wrongPassword = loginRequest("sara@example.com", "Wrong123").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = loginRequest("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    @DisplayName("after verification the user can log in and /me returns the account without the password hash")
    void loginAfterVerification() throws Exception {
        register("fahad@example.com", PASSWORD, "Fahad", "EMPLOYER", "Najd Co", "en").andExpect(status().isAccepted());
        postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted(tokenIn(singleEmailTo("fahad@example.com"))))
                .andExpect(status().isOk());

        String token = login("fahad@example.com", PASSWORD);
        String me = meWith(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("fahad@example.com"))
                .andExpect(jsonPath("$.role").value("EMPLOYER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.companyName").value("Najd Co"))
                .andReturn().getResponse().getContentAsString();
        assertThat(me).doesNotContain("password");
        // The login is case-insensitive on the email. / الدخول لا يفرّق بين حالة أحرف الإيميل.
        loginRequest("FAHAD@Example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a suspended account cannot log in, and a token it already holds stops working at once")
    void suspendedAccount() throws Exception {
        activeUser("sara@example.com", PASSWORD, Role.CANDIDATE);
        String token = login("sara@example.com", PASSWORD);
        meWith(token).andExpect(status().isOk());

        jdbc.update("update app_user set status = 'SUSPENDED' where email = 'sara@example.com'");

        loginRequest("sara@example.com", PASSWORD)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        meWith(token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/me without a token, or with a forged one, is 401")
    void meNeedsAToken() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        meWith("not.a.jwt").andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ verification link rules

    @Test
    @DisplayName("a wrong link is TOKEN_INVALID; an expired link is TOKEN_EXPIRED and does not activate")
    void invalidAndExpiredLinks() throws Exception {
        registerCandidate("sara@example.com").andExpect(status().isAccepted());
        String raw = tokenIn(singleEmailTo("sara@example.com"));

        postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted("x".repeat(43)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        jdbc.update("update email_token set expires_at = now() - interval '1 minute'");
        postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted(raw))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        assertThat(statusOf("sara@example.com")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("clicking the link twice is friendly (ALREADY_VERIFIED) and sends only one welcome email")
    void secondClick() throws Exception {
        registerCandidate("sara@example.com").andExpect(status().isAccepted());
        String raw = tokenIn(singleEmailTo("sara@example.com"));
        String body = "{\"token\":\"%s\"}".formatted(raw);

        postJson("/api/auth/verify-email", body).andExpect(jsonPath("$.status").value("VERIFIED"));
        postJson("/api/auth/verify-email", body).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_VERIFIED"));

        // verification email + one welcome email. / إيميل التفعيل + إيميل ترحيب واحد.
        assertThat(deliver()).hasSize(2);
    }

    @Test
    @DisplayName("a reset-password link cannot be used as a verification link")
    void tokenTypesAreNotInterchangeable() throws Exception {
        activeUser("sara@example.com", PASSWORD, Role.CANDIDATE);
        postJson("/api/auth/forgot-password", "{\"email\":\"sara@example.com\"}").andExpect(status().isAccepted());
        String resetToken = tokenIn(singleEmailTo("sara@example.com"));

        postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted(resetToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    // ------------------------------------------------------------------ no account enumeration

    @Test
    @DisplayName("registering an email that already has an account answers exactly like a new one, and creates nothing")
    void registerExistingActiveAccount() throws Exception {
        String fresh = registerCandidate("new@example.com").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        activeUser("sara@example.com", PASSWORD, Role.CANDIDATE);

        String existing = registerCandidate("sara@example.com").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(existing).isEqualTo(fresh);
        assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isEqualTo(2);
        // The owner gets an "account exists" notice, with no verification link.
        // المالك ياخذ تنبيه "الحساب موجود"، بدون رابط تفعيل.
        MimeMessage notice = singleEmailTo("sara@example.com");
        assertThat(notice.getSubject()).isEqualTo("You already have a Jobly account");
        assertThat(plainText(notice)).doesNotContain("verify-email");
        assertThat(jdbc.queryForObject("select count(*) from email_token where user_id = "
                + "(select id from app_user where email = 'sara@example.com')", Integer.class)).isZero();
    }

    // ------------------------------------------------------------------ re-registering and resending

    @Test
    @DisplayName("registering again while still unverified: the latest details win and only the newest link works")
    void registerAgainWhilePending() throws Exception {
        register("sara@example.com", "OldPass123", "Old Name", "CANDIDATE", null, "en").andExpect(status().isAccepted());
        String oldLink = tokenIn(singleEmailTo("sara@example.com"));
        passCooldown("sara@example.com");

        register("sara@example.com", "NewPass456", "New Name", "CANDIDATE", null, "en").andExpect(status().isAccepted());
        GREEN_MAIL.purgeEmailFromAllMailboxes();
        // Two emails were queued in total; the first one was already delivered (and purged above), so find the second.
        MimeMessage second = deliver().get(0);
        String newLink = tokenIn(second);

        assertThat(newLink).isNotEqualTo(oldLink);
        postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted(oldLink))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted(newLink)).andExpect(status().isOk());
        loginRequest("sara@example.com", "OldPass123").andExpect(status().isUnauthorized());
        loginRequest("sara@example.com", "NewPass456").andExpect(status().isOk())
                .andExpect(jsonPath("$.user.fullName").value("New Name"));
    }

    @Test
    @DisplayName("resend sends a new link and retires the old one; inside the cooldown it sends nothing")
    void resendVerification() throws Exception {
        registerCandidate("sara@example.com").andExpect(status().isAccepted());
        String oldLink = tokenIn(singleEmailTo("sara@example.com"));

        // Inside the 60 second cooldown: same 202 answer, no new email. / داخل فترة الـ 60 ثانية: نفس رد 202 بدون إيميل جديد.
        postJson("/api/auth/resend-verification", "{\"email\":\"sara@example.com\"}")
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("CHECK_EMAIL"));
        assertThat(jdbc.queryForObject("select count(*) from email_outbox", Integer.class)).isEqualTo(1);

        passCooldown("sara@example.com");
        GREEN_MAIL.purgeEmailFromAllMailboxes();
        postJson("/api/auth/resend-verification", "{\"email\":\"sara@example.com\"}").andExpect(status().isAccepted());
        String newLink = tokenIn(deliver().get(0));

        postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted(oldLink))
                .andExpect(status().isBadRequest());
        postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted(newLink)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("resend for an unknown or already active email answers the same and sends nothing")
    void resendDoesNotRevealAccounts() throws Exception {
        activeUser("sara@example.com", PASSWORD, Role.CANDIDATE);

        String unknown = postJson("/api/auth/resend-verification", "{\"email\":\"nobody@example.com\"}")
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String active = postJson("/api/auth/resend-verification", "{\"email\":\"sara@example.com\"}")
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(active).isEqualTo(unknown);
        assertThat(deliver()).isEmpty();
    }

    @Test
    @DisplayName("at most 5 verification emails per account in 24 hours")
    void dailyLimit() throws Exception {
        registerCandidate("sara@example.com").andExpect(status().isAccepted());
        for (int i = 0; i < 6; i++) {
            passCooldown("sara@example.com");
            postJson("/api/auth/resend-verification", "{\"email\":\"sara@example.com\"}")
                    .andExpect(status().isAccepted());
        }
        // 1 from registration + 4 resends = 5; the 5th and 6th resend were refused silently.
        // 1 من التسجيل + 4 إعادات = 5؛ والإعادة الخامسة والسادسة انرفضت بصمت.
        assertThat(jdbc.queryForObject("select count(*) from email_outbox", Integer.class)).isEqualTo(5);
    }
}
