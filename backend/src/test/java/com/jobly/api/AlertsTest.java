package com.jobly.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jobly.support.IntegrationTest;
import com.jobly.user.Role;

/**
 * Job alerts: a saved search emails the candidate when a matching job is published.
 * تنبيهات الوظائف: بحث محفوظ يرسل إيميل للمرشح لما تنشر وظيفة تطابقه.
 */
class AlertsTest extends IntegrationTest {

    private static final String ALERTS = "/api/candidate/alerts";

    private String employer;

    private void signInEmployer() throws Exception {
        activeUser("boss@example.com", PASSWORD, Role.EMPLOYER, "Fahad", "en");
        employer = login("boss@example.com", PASSWORD);
    }

    private long publish(String title, String city, String type) throws Exception {
        return publishedJob(employer, title, city, type);
    }

    private static String alert(String keyword, String city, String type) {
        return """
                {"keyword":%s,"city":%s,"type":%s}
                """.formatted(keyword == null ? "null" : "\"" + keyword + "\"", city == null ? "null" : "\"" + city + "\"",
                type == null ? "null" : "\"" + type + "\"");
    }

    @Test
    @DisplayName("a matching job sends one alert email with a link to the job and a stop-alerts link")
    void matchingJobSendsAlert() throws Exception {
        signInEmployer();
        String candidate = candidateToken("sara@example.com");
        postAs(candidate, ALERTS, alert("java", "Riyadh", null)).andExpect(status().isCreated());

        long job = publish("Senior Java Developer", "riyadh", "FULL_TIME");

        MimeMessage email = singleEmailTo("sara@example.com");
        assertThat(email.getSubject()).isEqualTo("New job for you: Senior Java Developer");
        assertThat(plainText(email)).contains("http://localhost:4200/jobs/" + job)
                .contains("Stop all my job alerts: http://localhost:4200/unsubscribe#");
        assertThat(html(email)).contains("Stop all my job alerts");
    }

    @Test
    @DisplayName("jobs that do not match the keyword, city or type send nothing")
    void nonMatchingJobsSendNothing() throws Exception {
        signInEmployer();
        String candidate = candidateToken("sara@example.com");
        postAs(candidate, ALERTS, alert("java", "Riyadh", "FULL_TIME")).andExpect(status().isCreated());

        publish("Senior Java Developer", "Jeddah", "FULL_TIME");
        publish("Senior Java Developer", "Riyadh", "PART_TIME");
        publish("Accountant", "Riyadh", "FULL_TIME");

        assertThat(deliver()).isEmpty();
    }

    @Test
    @DisplayName("every keyword word must match, in the title, description or company name, in any letter case")
    void keywordRules() throws Exception {
        signInEmployer();
        String candidate = candidateToken("sara@example.com");
        postAs(candidate, ALERTS, alert("JAVA developer", null, null)).andExpect(status().isCreated());

        publish("Java Tester", "Riyadh", "FULL_TIME");
        assertThat(deliver()).isEmpty();
        // The first job has "java" but not "developer" anywhere, so it does not match; the second has both.
        // الوظيفة الأولى فيها "java" بدون "developer" في أي مكان فما تطابق؛ والثانية فيها الكلمتين.
        publish("Senior JAVA Developer", "Riyadh", "FULL_TIME");
        assertThat(deliver()).hasSize(1);
    }

    @Test
    @DisplayName("an alert with only a city or only a type works too")
    void singleFilterAlerts() throws Exception {
        signInEmployer();
        String sara = candidateToken("sara@example.com");
        String omar = candidateToken("omar@example.com");
        postAs(sara, ALERTS, alert(null, "Dammam", null)).andExpect(status().isCreated());
        postAs(omar, ALERTS, alert(null, null, "INTERNSHIP")).andExpect(status().isCreated());

        publish("Analyst", "Dammam", "FULL_TIME");
        publish("Helper", "Riyadh", "INTERNSHIP");

        List<MimeMessage> emails = deliver();
        assertThat(emails).hasSize(2);
        assertThat(emails).extracting(m -> m.getSubject()).containsExactlyInAnyOrder(
                "New job for you: Analyst", "New job for you: Helper");
    }

    @Test
    @DisplayName("two matching alerts of one candidate still send one email per job")
    void oneEmailPerCandidatePerJob() throws Exception {
        signInEmployer();
        String candidate = candidateToken("sara@example.com");
        postAs(candidate, ALERTS, alert("java", null, null)).andExpect(status().isCreated());
        postAs(candidate, ALERTS, alert(null, "Riyadh", null)).andExpect(status().isCreated());

        publish("Java Developer", "Riyadh", "FULL_TIME");

        assertThat(deliver()).hasSize(1);
    }

    @Test
    @DisplayName("a paused alert sends nothing until it is resumed")
    void pauseAndResume() throws Exception {
        signInEmployer();
        String candidate = candidateToken("sara@example.com");
        long alertId = idOf(postAs(candidate, ALERTS, alert("java", null, null)).andExpect(status().isCreated()));

        patchAs(candidate, ALERTS + "/" + alertId, "{\"active\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        publish("Java One", "Riyadh", "FULL_TIME");
        assertThat(deliver()).isEmpty();

        patchAs(candidate, ALERTS + "/" + alertId, "{\"active\":true}").andExpect(jsonPath("$.active").value(true));
        publish("Java Two", "Riyadh", "FULL_TIME");
        assertThat(deliver()).hasSize(1);
    }

    @Test
    @DisplayName("at most 5 alert emails per candidate in 24 hours, however many jobs match")
    void dailyLimit() throws Exception {
        signInEmployer();
        String candidate = candidateToken("sara@example.com");
        postAs(candidate, ALERTS, alert("java", null, null)).andExpect(status().isCreated());

        for (int i = 1; i <= 7; i++) {
            publish("Java Job " + i, "Riyadh", "FULL_TIME");
        }

        assertThat(deliver()).hasSize(5);
    }

    @Test
    @DisplayName("a pending or suspended candidate gets no alerts")
    void inactiveCandidatesGetNothing() throws Exception {
        signInEmployer();
        String candidate = candidateToken("sara@example.com");
        postAs(candidate, ALERTS, alert("java", null, null)).andExpect(status().isCreated());
        jdbc.update("update app_user set status = 'SUSPENDED' where email = 'sara@example.com'");

        publish("Java Developer", "Riyadh", "FULL_TIME");

        assertThat(deliver()).isEmpty();
    }

    @Test
    @DisplayName("an Arabic candidate gets the alert in Arabic")
    void arabicAlert() throws Exception {
        signInEmployer();
        activeUser("noura@example.com", PASSWORD, Role.CANDIDATE, "نورة", "ar");
        String candidate = login("noura@example.com", PASSWORD);
        postAs(candidate, ALERTS, alert("java", null, null)).andExpect(status().isCreated());

        publish("Java Developer", "Riyadh", "FULL_TIME");

        MimeMessage email = singleEmailTo("noura@example.com");
        assertThat(email.getSubject()).startsWith("وظيفة جديدة لك");
        assertThat(html(email)).contains("dir=\"rtl\"").contains("إيقاف كل تنبيهات الوظائف");
    }

    // ------------------------------------------------------------------ managing alerts

    @Test
    @DisplayName("an alert needs at least one filter, and a candidate may save at most 5")
    void alertRules() throws Exception {
        String candidate = candidateToken("sara@example.com");

        postAs(candidate, ALERTS, alert(null, null, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.filter").exists());
        postAs(candidate, ALERTS, alert("  ", " ", null)).andExpect(status().isBadRequest());
        postAs(candidate, ALERTS, alert(null, null, "FOREVER")).andExpect(status().isBadRequest());
        for (int i = 1; i <= 5; i++) {
            postAs(candidate, ALERTS, alert("word" + i, null, null)).andExpect(status().isCreated());
        }
        postAs(candidate, ALERTS, alert("one more", null, null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALERT_LIMIT"));
        getAs(candidate, ALERTS).andExpect(jsonPath("$.length()").value(5)).andExpect(jsonPath("$[0].keyword").value("word5"));
    }

    @Test
    @DisplayName("alerts are private: another candidate, or an employer, cannot see or change them")
    void alertsArePrivate() throws Exception {
        String sara = candidateToken("sara@example.com");
        String omar = candidateToken("omar@example.com");
        String boss = employerToken("boss@example.com");
        long id = idOf(postAs(sara, ALERTS, alert("java", null, null)).andExpect(status().isCreated()));

        getAs(omar, ALERTS).andExpect(jsonPath("$.length()").value(0));
        patchAs(omar, ALERTS + "/" + id, "{\"active\":false}").andExpect(status().isNotFound());
        deleteAs(omar, ALERTS + "/" + id).andExpect(status().isNotFound());
        getAs(boss, ALERTS).andExpect(status().isForbidden());
        getAs(null, ALERTS).andExpect(status().isUnauthorized());

        deleteAs(sara, ALERTS + "/" + id).andExpect(status().isNoContent());
        getAs(sara, ALERTS).andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------------ the "stop my alerts" link

    @Test
    @DisplayName("the stop-alerts link from the email switches off all of that person's alerts, and no one else's")
    void unsubscribe() throws Exception {
        signInEmployer();
        String sara = candidateToken("sara@example.com");
        String omar = candidateToken("omar@example.com");
        postAs(sara, ALERTS, alert("java", null, null)).andExpect(status().isCreated());
        postAs(sara, ALERTS, alert(null, "Jeddah", null)).andExpect(status().isCreated());
        postAs(omar, ALERTS, alert("java", null, null)).andExpect(status().isCreated());
        publish("Java Developer", "Riyadh", "FULL_TIME");
        List<MimeMessage> emails = deliver();
        MimeMessage saraEmail = emails.stream().filter(m -> {
            try {
                return m.getAllRecipients()[0].toString().equals("sara@example.com");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).findFirst().orElseThrow();
        String token = unsubscribeTokenIn(saraEmail);

        // No sign-in needed: the person clicking the link is just reading their email.
        // ما يحتاج تسجيل دخول: الشخص اللي يضغط الرابط يقرأ إيميله فقط.
        postJson("/api/public/unsubscribe", "{\"token\":\"%s\"}".formatted(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNSUBSCRIBED")).andExpect(jsonPath("$.stopped").value(2));

        getAs(sara, ALERTS).andExpect(jsonPath("$[0].active").value(false)).andExpect(jsonPath("$[1].active").value(false));
        getAs(omar, ALERTS).andExpect(jsonPath("$[0].active").value(true));
        // Pressing it again is harmless. / الضغط عليه مرة ثانية ما يضر.
        postJson("/api/public/unsubscribe", "{\"token\":\"%s\"}".formatted(token)).andExpect(jsonPath("$.stopped").value(0));
    }

    @Test
    @DisplayName("a forged or altered stop-alerts token is refused")
    void forgedUnsubscribe() throws Exception {
        signInEmployer();
        String sara = candidateToken("sara@example.com");
        String omar = candidateToken("omar@example.com");
        postAs(sara, ALERTS, alert("java", null, null)).andExpect(status().isCreated());
        postAs(omar, ALERTS, alert("java", null, null)).andExpect(status().isCreated());
        publish("Java Developer", "Riyadh", "FULL_TIME");
        String saraToken = deliver().stream().filter(m -> {
            try {
                return m.getAllRecipients()[0].toString().equals("sara@example.com");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).map(m -> {
            try {
                return unsubscribeTokenIn(m);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).findFirst().orElseThrow();
        long omarId = userId("omar@example.com");

        // Sara's signature with Omar's id, a made-up signature, no signature, and rubbish.
        // توقيع سارة مع رقم عمر، وتوقيع مخترع، وبدون توقيع، وكلام فاضي.
        for (String forged : new String[] {omarId + "." + saraToken.substring(saraToken.indexOf('.') + 1),
                omarId + ".AAAA", String.valueOf(omarId), "nonsense", "x.y", "."}) {
            postJson("/api/public/unsubscribe", "{\"token\":\"%s\"}".formatted(forged))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        }
        getAs(omar, ALERTS).andExpect(jsonPath("$[0].active").value(true));
    }

    private long userId(String email) {
        return jdbc.queryForObject("select id from app_user where email = ?", Long.class, email);
    }
}
