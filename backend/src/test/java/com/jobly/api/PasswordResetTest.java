package com.jobly.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jobly.support.IntegrationTest;
import com.jobly.user.Role;

/**
 * Forgot password -> email link -> new password.
 * نسيت كلمة المرور -> رابط الإيميل -> كلمة مرور جديدة.
 */
class PasswordResetTest extends IntegrationTest {

    private static final String FORGOT = "/api/auth/forgot-password";
    private static final String RESET = "/api/auth/reset-password";

    private static String forgotBody(String email) {
        return "{\"email\":\"%s\"}".formatted(email);
    }

    private static String resetBody(String token, String password) {
        return "{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password);
    }

    @Test
    @DisplayName("the full flow: request a link, set a new password, the old one stops working")
    void fullFlow() throws Exception {
        activeUser("sara@example.com", PASSWORD, Role.CANDIDATE);

        postJson(FORGOT, forgotBody("sara@example.com")).andExpect(status().isAccepted());
        MimeMessage email = singleEmailTo("sara@example.com");
        assertThat(email.getSubject()).isEqualTo("Reset your Jobly password");
        assertThat(plainText(email)).contains("http://localhost:4200/reset-password#");

        postJson(RESET, resetBody(tokenIn(email), "BrandNew789"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PASSWORD_CHANGED"));

        loginRequest("sara@example.com", PASSWORD).andExpect(status().isUnauthorized());
        loginRequest("sara@example.com", "BrandNew789").andExpect(status().isOk());
        // A "your password was changed" notice goes out too. / يطلع كذلك إيميل "تم تغيير كلمة مرورك".
        assertThat(deliver()).extracting(m -> m.getSubject())
                .contains("Your Jobly password was changed");
    }

    @Test
    @DisplayName("an unknown, pending or suspended email gets the same 202 answer and no email")
    void doesNotRevealAccounts() throws Exception {
        activeUser("sara@example.com", PASSWORD, Role.CANDIDATE);
        activeUser("sus@example.com", PASSWORD, Role.CANDIDATE);
        jdbc.update("update app_user set status = 'SUSPENDED' where email = 'sus@example.com'");
        registerCandidate("pending@example.com").andExpect(status().isAccepted());
        // Throw away the verification email so only reset emails are counted below.
        deliver();
        GREEN_MAIL.purgeEmailFromAllMailboxes();

        String known = postJson(FORGOT, forgotBody("sara@example.com")).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        GREEN_MAIL.purgeEmailFromAllMailboxes();
        deliver();
        GREEN_MAIL.purgeEmailFromAllMailboxes();

        for (String email : new String[] {"nobody@example.com", "pending@example.com", "sus@example.com"}) {
            String answer = postJson(FORGOT, forgotBody(email)).andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString();
            assertThat(answer).isEqualTo(known);
        }
        assertThat(deliver()).isEmpty();
    }

    @Test
    @DisplayName("a reset link works once; using it again is TOKEN_INVALID")
    void singleUse() throws Exception {
        activeUser("sara@example.com", PASSWORD, Role.CANDIDATE);
        postJson(FORGOT, forgotBody("sara@example.com")).andExpect(status().isAccepted());
        String raw = tokenIn(singleEmailTo("sara@example.com"));

        postJson(RESET, resetBody(raw, "BrandNew789")).andExpect(status().isOk());
        postJson(RESET, resetBody(raw, "Another1234"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        loginRequest("sara@example.com", "BrandNew789").andExpect(status().isOk());
    }

    @Test
    @DisplayName("an expired reset link is TOKEN_EXPIRED and changes nothing")
    void expired() throws Exception {
        activeUser("sara@example.com", PASSWORD, Role.CANDIDATE);
        postJson(FORGOT, forgotBody("sara@example.com")).andExpect(status().isAccepted());
        String raw = tokenIn(singleEmailTo("sara@example.com"));

        jdbc.update("update email_token set expires_at = now() - interval '1 second'");
        postJson(RESET, resetBody(raw, "BrandNew789"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        loginRequest("sara@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("asking again retires the earlier link: only the newest reset link works")
    void newestLinkOnly() throws Exception {
        activeUser("sara@example.com", PASSWORD, Role.CANDIDATE);
        postJson(FORGOT, forgotBody("sara@example.com")).andExpect(status().isAccepted());
        String first = tokenIn(singleEmailTo("sara@example.com"));
        passCooldown("sara@example.com");
        GREEN_MAIL.purgeEmailFromAllMailboxes();

        postJson(FORGOT, forgotBody("sara@example.com")).andExpect(status().isAccepted());
        String second = tokenIn(deliver().get(0));

        postJson(RESET, resetBody(first, "BrandNew789")).andExpect(status().isBadRequest());
        postJson(RESET, resetBody(second, "BrandNew789")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("inside the cooldown a second request sends no second email")
    void cooldown() throws Exception {
        activeUser("sara@example.com", PASSWORD, Role.CANDIDATE);
        postJson(FORGOT, forgotBody("sara@example.com")).andExpect(status().isAccepted());
        postJson(FORGOT, forgotBody("sara@example.com")).andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("select count(*) from email_outbox", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("a weak new password is refused and the link stays usable")
    void weakPassword() throws Exception {
        activeUser("sara@example.com", PASSWORD, Role.CANDIDATE);
        postJson(FORGOT, forgotBody("sara@example.com")).andExpect(status().isAccepted());
        String raw = tokenIn(singleEmailTo("sara@example.com"));

        postJson(RESET, resetBody(raw, "short1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.password").exists());
        postJson(RESET, resetBody(raw, "BrandNew789")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("changing the password logs out every older session")
    void oldTokensDieAfterReset() throws Exception {
        activeUser("sara@example.com", PASSWORD, Role.CANDIDATE);
        String oldToken = login("sara@example.com", PASSWORD);
        meWith(oldToken).andExpect(status().isOk());

        postJson(FORGOT, forgotBody("sara@example.com")).andExpect(status().isAccepted());
        String raw = tokenIn(singleEmailTo("sara@example.com"));
        // Login tokens carry whole seconds, so the reset must happen in a later second than the old login.
        // رموز الدخول تحمل ثواني كاملة، فلازم الاستعادة تصير في ثانية بعد ثانية الدخول القديم.
        Thread.sleep(1100);
        postJson(RESET, resetBody(raw, "BrandNew789")).andExpect(status().isOk());

        meWith(oldToken).andExpect(status().isUnauthorized());
        String newToken = login("sara@example.com", "BrandNew789");
        meWith(newToken).andExpect(status().isOk());
    }
}
