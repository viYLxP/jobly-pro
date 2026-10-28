package com.jobly.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jobly.support.IntegrationTest;
import com.jobly.user.Role;

/**
 * The admin area: finding accounts, suspending them, platform numbers and failed emails.
 * منطقة الأدمن: البحث عن الحسابات، إيقافها، أرقام المنصة، والإيميلات الفاشلة.
 */
class AdminTest extends IntegrationTest {

    private String admin;

    @BeforeEach
    void signInAdmin() throws Exception {
        activeUser("admin@example.com", PASSWORD, Role.ADMIN);
        admin = login("admin@example.com", PASSWORD);
    }

    private long idOfUser(String email) {
        return jdbc.queryForObject("select id from app_user where email = ?", Long.class, email);
    }

    @Test
    @DisplayName("only an admin reaches the admin area")
    void onlyAdmins() throws Exception {
        String candidate = candidateToken("sara@example.com");
        String employer = employerToken("boss@example.com");

        for (String url : new String[] {"/api/admin/users", "/api/admin/stats", "/api/admin/emails"}) {
            getAs(candidate, url).andExpect(status().isForbidden());
            getAs(employer, url).andExpect(status().isForbidden());
            getAs(null, url).andExpect(status().isUnauthorized());
            getAs(admin, url).andExpect(status().isOk());
        }
        postAs(candidate, "/api/admin/users/1/suspend", "").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the user list filters by role, status and text in the email or name, newest first, with paging")
    void userList() throws Exception {
        candidateToken("sara@example.com");
        candidateToken("omar@example.com");
        employerToken("boss@example.com");
        registerCandidate("pending@example.com").andExpect(status().isAccepted());

        getAs(admin, "/api/admin/users").andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].email").value("pending@example.com"));
        getAs(admin, "/api/admin/users?role=CANDIDATE").andExpect(jsonPath("$.totalElements").value(3));
        getAs(admin, "/api/admin/users?status=PENDING").andExpect(jsonPath("$.totalElements").value(1));
        getAs(admin, "/api/admin/users?q=OMAR").andExpect(jsonPath("$.totalElements").value(1));
        getAs(admin, "/api/admin/users?q=%25").andExpect(jsonPath("$.totalElements").value(0));
        getAs(admin, "/api/admin/users?size=2&page=1").andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(3));
        // The list never carries password data. / القائمة ما تحمل أي بيانات كلمات مرور.
        assertThat(getAs(admin, "/api/admin/users").andReturn().getResponse().getContentAsString())
                .doesNotContain("password").doesNotContain("$2a$");
    }

    @Test
    @DisplayName("suspending an employer blocks their login, hides their jobs and stops applications; restoring undoes it")
    void suspendAndRestoreEmployer() throws Exception {
        String boss = employerToken("boss@example.com");
        String candidate = candidateToken("sara@example.com");
        long job = publishedJob(boss, "Java Developer", "Riyadh", "FULL_TIME");
        getAs(null, "/api/jobs").andExpect(jsonPath("$.totalElements").value(1));

        postAs(admin, "/api/admin/users/" + idOfUser("boss@example.com") + "/suspend", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        loginRequest("boss@example.com", PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        getAs(boss, "/api/employer/jobs").andExpect(status().isUnauthorized());
        getAs(null, "/api/jobs").andExpect(jsonPath("$.totalElements").value(0));
        getAs(null, "/api/jobs/" + job).andExpect(status().isNotFound());
        postAs(candidate, "/api/candidate/jobs/" + job + "/applications", "{}").andExpect(status().isNotFound());

        postAs(admin, "/api/admin/users/" + idOfUser("boss@example.com") + "/restore", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        loginRequest("boss@example.com", PASSWORD).andExpect(status().isOk());
        getAs(null, "/api/jobs").andExpect(jsonPath("$.totalElements").value(1));
        postAs(candidate, "/api/candidate/jobs/" + job + "/applications", "{}").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("restoring an account that never verified its email puts it back to PENDING, not ACTIVE")
    void restoreDoesNotSkipVerification() throws Exception {
        registerCandidate("pending@example.com").andExpect(status().isAccepted());
        long id = idOfUser("pending@example.com");

        postAs(admin, "/api/admin/users/" + id + "/suspend", "").andExpect(status().isOk());
        postAs(admin, "/api/admin/users/" + id + "/restore", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        loginRequest("pending@example.com", PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("an admin cannot suspend themselves or another admin; restoring a non-suspended account is refused")
    void guardRails() throws Exception {
        activeUser("other-admin@example.com", PASSWORD, Role.ADMIN);
        candidateToken("sara@example.com");

        postAs(admin, "/api/admin/users/" + idOfUser("admin@example.com") + "/suspend", "")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
        postAs(admin, "/api/admin/users/" + idOfUser("other-admin@example.com") + "/suspend", "")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATUS"));
        postAs(admin, "/api/admin/users/" + idOfUser("sara@example.com") + "/restore", "")
                .andExpect(status().isConflict());
        postAs(admin, "/api/admin/users/999999/suspend", "").andExpect(status().isNotFound());
        // Still active after the refused attempts. / تبقى فعالة بعد المحاولات المرفوضة.
        assertThat(statusOf("admin@example.com")).isEqualTo("ACTIVE");
        assertThat(statusOf("other-admin@example.com")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("stats count users, jobs, applications, alerts and emails, with 0 for values that do not exist yet")
    void stats() throws Exception {
        String boss = employerToken("boss@example.com");
        String sara = candidateToken("sara@example.com");
        long published = publishedJob(boss, "Published", "Riyadh", "FULL_TIME");
        draftJob(boss, "Draft", "Riyadh", "FULL_TIME");
        postAs(sara, "/api/candidate/jobs/" + published + "/applications", "{}").andExpect(status().isCreated());
        postAs(sara, "/api/candidate/alerts", "{\"keyword\":\"java\"}").andExpect(status().isCreated());

        getAs(admin, "/api/admin/stats").andExpect(status().isOk())
                .andExpect(jsonPath("$.usersByRole.ADMIN").value(1))
                .andExpect(jsonPath("$.usersByRole.CANDIDATE").value(1))
                .andExpect(jsonPath("$.usersByRole.EMPLOYER").value(1))
                .andExpect(jsonPath("$.usersByStatus.ACTIVE").value(3))
                .andExpect(jsonPath("$.usersByStatus.SUSPENDED").value(0))
                .andExpect(jsonPath("$.jobsByStatus.PUBLISHED").value(1))
                .andExpect(jsonPath("$.jobsByStatus.DRAFT").value(1))
                .andExpect(jsonPath("$.jobsByStatus.CLOSED").value(0))
                .andExpect(jsonPath("$.applicationsByStatus.SUBMITTED").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.OFFER").value(0))
                .andExpect(jsonPath("$.activeAlerts").value(1))
                .andExpect(jsonPath("$.emailsByStatus.PENDING").value(2));
    }

    @Test
    @DisplayName("failed emails are listed with their error and can be put back in the queue")
    void failedEmails() throws Exception {
        GREEN_MAIL.stop();
        try {
            registerCandidate("sara@example.com").andExpect(status().isAccepted());
            for (int i = 0; i < 5; i++) {
                jdbc.update("update email_outbox set next_attempt_at = now() - interval '1 second' where status = 'PENDING'");
                outboxProcessor.processDue();
            }
        } finally {
            GREEN_MAIL.start();
        }

        String list = getAs(admin, "/api/admin/emails?status=FAILED").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].to").value("sara@example.com"))
                .andExpect(jsonPath("$.content[0].template").value("VERIFY_EMAIL"))
                .andExpect(jsonPath("$.content[0].attempts").value(5))
                .andExpect(jsonPath("$.content[0].lastError").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(list, "$.content[0].id")).longValue();
        // The admin list shows no link or secret from the email. / قائمة الأدمن ما تعرض أي رابط أو سر من الإيميل.
        assertThat(list).doesNotContain("verify-email").doesNotContain("params");

        postAs(admin, "/api/admin/emails/" + id + "/retry", "").andExpect(status().isNoContent());
        assertThat(outboxProcessor.processDue()).isEqualTo(1);
        getAs(admin, "/api/admin/emails?status=SENT").andExpect(jsonPath("$.totalElements").value(1));
        // Only a FAILED email can be retried. / فقط الإيميل الفاشل يمكن إعادته.
        postAs(admin, "/api/admin/emails/" + id + "/retry", "").andExpect(status().isConflict());
        postAs(admin, "/api/admin/emails/999999/retry", "").andExpect(status().isNotFound());
    }
}
