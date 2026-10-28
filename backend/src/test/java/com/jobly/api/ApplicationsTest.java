package com.jobly.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jobly.support.IntegrationTest;
import com.jobly.user.Role;

/**
 * Applying, the employer reviewing, and the emails both sides receive.
 * التقديم، ومراجعة صاحب العمل، والإيميلات اللي توصل الطرفين.
 */
class ApplicationsTest extends IntegrationTest {

    private static final String NOTE = "{\"coverNote\":\"I would love to join.\"}";

    private static String apply(long jobId) {
        return "/api/candidate/jobs/" + jobId + "/applications";
    }

    private static String statusBody(String value) {
        return "{\"status\":\"%s\"}".formatted(value);
    }

    @Test
    @DisplayName("applying creates the application and emails both the candidate and the employer")
    void applyAndEmails() throws Exception {
        activeUser("boss@example.com", PASSWORD, Role.EMPLOYER, "Fahad", "en");
        String employer = login("boss@example.com", PASSWORD);
        String candidate = candidateToken("sara@example.com");
        long job = publishedJob(employer, "Java Developer", "Riyadh", "FULL_TIME");

        postAs(candidate, apply(job), NOTE).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.jobTitle").value("Java Developer"))
                .andExpect(jsonPath("$.coverNote").value("I would love to join."))
                .andExpect(jsonPath("$.timeline.length()").value(1));

        List<MimeMessage> emails = deliver();
        assertThat(emails).hasSize(2);
        MimeMessage toCandidate = emails.stream().filter(m -> recipient(m).equals("sara@example.com")).findFirst().orElseThrow();
        MimeMessage toEmployer = emails.stream().filter(m -> recipient(m).equals("boss@example.com")).findFirst().orElseThrow();
        assertThat(toCandidate.getSubject()).isEqualTo("We received your application: Java Developer");
        assertThat(plainText(toCandidate)).contains("http://localhost:4200/candidate/applications");
        assertThat(toEmployer.getSubject()).isEqualTo("New applicant for Java Developer");
        assertThat(plainText(toEmployer)).contains("applied for Java Developer")
                .contains("http://localhost:4200/employer/jobs/" + job + "/applicants");
    }

    private static String recipient(MimeMessage message) {
        try {
            return message.getAllRecipients()[0].toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("the cover note is optional")
    void coverNoteOptional() throws Exception {
        String employer = employerToken("boss@example.com");
        String candidate = candidateToken("sara@example.com");
        long job = publishedJob(employer, "Java Developer", "Riyadh", "FULL_TIME");

        postAs(candidate, apply(job), "{}").andExpect(status().isCreated()).andExpect(jsonPath("$.coverNote").doesNotExist());
    }

    @Test
    @DisplayName("applying twice is refused, and only published jobs accept applications")
    void applyRules() throws Exception {
        String employer = employerToken("boss@example.com");
        String candidate = candidateToken("sara@example.com");
        long open = publishedJob(employer, "Open", "Riyadh", "FULL_TIME");
        long draft = draftJob(employer, "Draft", "Riyadh", "FULL_TIME");
        long closed = publishedJob(employer, "Closed", "Riyadh", "FULL_TIME");
        postAs(employer, "/api/employer/jobs/" + closed + "/close", "").andExpect(status().isOk());

        postAs(candidate, apply(open), NOTE).andExpect(status().isCreated());
        postAs(candidate, apply(open), NOTE).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_APPLIED"));
        postAs(candidate, apply(draft), NOTE).andExpect(status().isNotFound());
        postAs(candidate, apply(closed), NOTE).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_CLOSED"));
        postAs(candidate, apply(999999), NOTE).andExpect(status().isNotFound());
        // Employers cannot apply, and visitors must sign in. / أصحاب العمل ما يقدمون، والزائر لازم يسجّل دخول.
        postAs(employer, apply(open), NOTE).andExpect(status().isForbidden());
        postAs(null, apply(open), NOTE).andExpect(status().isUnauthorized());
        // A cover note over 2000 characters is a validation error, not a duplicate. / رسالة تقديم فوق 2000 حرف خطأ تحقق وليست تكرار.
        postAs(candidate, apply(open), "{\"coverNote\":\"" + "x".repeat(2001) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.coverNote").exists());
    }

    @Test
    @DisplayName("two simultaneous applications by the same candidate: exactly one wins")
    void doubleSubmitRace() throws Exception {
        String employer = employerToken("boss@example.com");
        String candidate = candidateToken("sara@example.com");
        long job = publishedJob(employer, "Java Developer", "Riyadh", "FULL_TIME");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> clicks = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                clicks.add(() -> postAs(candidate, apply(job), NOTE).andReturn().getResponse().getStatus());
            }
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> result : pool.invokeAll(clicks)) {
                codes.add(result.get());
            }
            assertThat(codes).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdown();
        }
        assertThat(jdbc.queryForObject("select count(*) from job_application", Integer.class)).isEqualTo(1);
        // One application means one pair of emails, not two. / طلب واحد يعني زوج إيميلات واحد وليس اثنين.
        assertThat(jdbc.queryForObject("select count(*) from email_outbox", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("the employer sees applicants with their profile and moves them through the steps; each step emails the candidate")
    void reviewFlow() throws Exception {
        String employer = employerToken("boss@example.com");
        String candidate = candidateToken("sara@example.com");
        long job = publishedJob(employer, "Java Developer", "Riyadh", "FULL_TIME");
        putAs(candidate, "/api/candidate/profile", """
                {"headline":"Java developer","city":"Jeddah","yearsExperience":4,"summary":"Four years of Spring Boot."}
                """).andExpect(status().isOk());
        long applicationId = idOf(postAs(candidate, apply(job), NOTE).andExpect(status().isCreated()));
        deliver();
        GREEN_MAIL.purgeEmailFromAllMailboxes();

        getAs(employer, "/api/employer/jobs/" + job + "/applications").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].candidate.fullName").value("Test CANDIDATE"))
                .andExpect(jsonPath("$.content[0].candidate.email").value("sara@example.com"))
                .andExpect(jsonPath("$.content[0].candidate.headline").value("Java developer"))
                .andExpect(jsonPath("$.content[0].candidate.yearsExperience").value(4))
                .andExpect(jsonPath("$.content[0].status").value("SUBMITTED"));

        String url = "/api/employer/applications/" + applicationId + "/status";
        patchAs(employer, url, statusBody("REVIEWING")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEWING"));
        MimeMessage first = singleEmailTo("sara@example.com");
        assertThat(first.getSubject()).isEqualTo("Update on your application: Java Developer");
        assertThat(plainText(first)).contains("is now: Under review");
        GREEN_MAIL.purgeEmailFromAllMailboxes();

        patchAs(employer, url, statusBody("INTERVIEW")).andExpect(status().isOk());
        patchAs(employer, url, statusBody("OFFER")).andExpect(status().isOk());
        assertThat(deliver()).hasSize(2);

        getAs(candidate, "/api/candidate/applications/" + applicationId).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFER"))
                .andExpect(jsonPath("$.timeline.length()").value(4))
                .andExpect(jsonPath("$.timeline[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.timeline[3].status").value("OFFER"));
    }

    @Test
    @DisplayName("an Arabic candidate gets the status email in Arabic, right-to-left")
    void arabicStatusEmail() throws Exception {
        String employer = employerToken("boss@example.com");
        activeUser("noura@example.com", PASSWORD, Role.CANDIDATE, "نورة", "ar");
        String candidate = login("noura@example.com", PASSWORD);
        long job = publishedJob(employer, "Java Developer", "Riyadh", "FULL_TIME");
        long id = idOf(postAs(candidate, apply(job), NOTE).andExpect(status().isCreated()));
        deliver();
        GREEN_MAIL.purgeEmailFromAllMailboxes();

        patchAs(employer, "/api/employer/applications/" + id + "/status", statusBody("REVIEWING")).andExpect(status().isOk());

        MimeMessage email = singleEmailTo("noura@example.com");
        assertThat(email.getSubject()).startsWith("تحديث على طلبك");
        assertThat(plainText(email)).contains("قيد المراجعة");
        assertThat(html(email)).contains("dir=\"rtl\"");
    }

    @Test
    @DisplayName("only the allowed moves work: no skipping steps, and OFFER and REJECTED are final")
    void transitions() throws Exception {
        String employer = employerToken("boss@example.com");
        String candidate = candidateToken("sara@example.com");
        long job = publishedJob(employer, "Java Developer", "Riyadh", "FULL_TIME");
        long id = idOf(postAs(candidate, apply(job), NOTE));
        String url = "/api/employer/applications/" + id + "/status";

        patchAs(employer, url, statusBody("OFFER")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
                .andExpect(jsonPath("$.current").value("SUBMITTED")).andExpect(jsonPath("$.requested").value("OFFER"));
        patchAs(employer, url, statusBody("SUBMITTED")).andExpect(status().isConflict());
        patchAs(employer, url, statusBody("REJECTED")).andExpect(status().isOk());
        patchAs(employer, url, statusBody("REVIEWING")).andExpect(status().isConflict());
        patchAs(employer, url, statusBody("REJECTED")).andExpect(status().isConflict());
        patchAs(employer, url, statusBody("NONSENSE")).andExpect(status().isBadRequest());
        patchAs(employer, url, "{}").andExpect(status().isBadRequest());
        // Refused moves sent no email and left no history. / الانتقالات المرفوضة ما أرسلت إيميل ولا تركت تاريخ.
        assertThat(jdbc.queryForObject("select count(*) from application_event where application_id = ?",
                Integer.class, id)).isEqualTo(2);
    }

    @Test
    @DisplayName("people only reach their own data: another employer, or another candidate, gets 404")
    void isolation() throws Exception {
        String owner = employerToken("owner@example.com");
        String other = employerToken("other@example.com");
        String sara = candidateToken("sara@example.com");
        String omar = candidateToken("omar@example.com");
        long job = publishedJob(owner, "Java Developer", "Riyadh", "FULL_TIME");
        long id = idOf(postAs(sara, apply(job), NOTE));

        getAs(other, "/api/employer/jobs/" + job + "/applications").andExpect(status().isNotFound());
        patchAs(other, "/api/employer/applications/" + id + "/status", statusBody("REVIEWING")).andExpect(status().isNotFound());
        getAs(omar, "/api/candidate/applications/" + id).andExpect(status().isNotFound());
        getAs(omar, "/api/candidate/applications").andExpect(jsonPath("$.totalElements").value(0));
        getAs(sara, "/api/candidate/applications").andExpect(jsonPath("$.totalElements").value(1));
        // The failed attempts changed nothing. / المحاولات الفاشلة ما غيّرت شيئًا.
        getAs(sara, "/api/candidate/applications/" + id).andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("applicants can be filtered by status, and the candidate's list is newest first with paging")
    void filtersAndPaging() throws Exception {
        String employer = employerToken("boss@example.com");
        String sara = candidateToken("sara@example.com");
        String omar = candidateToken("omar@example.com");
        long first = publishedJob(employer, "First", "Riyadh", "FULL_TIME");
        long second = publishedJob(employer, "Second", "Riyadh", "FULL_TIME");
        long saraFirst = idOf(postAs(sara, apply(first), NOTE));
        postAs(omar, apply(first), NOTE).andExpect(status().isCreated());
        postAs(sara, apply(second), NOTE).andExpect(status().isCreated());
        patchAs(employer, "/api/employer/applications/" + saraFirst + "/status", statusBody("REVIEWING")).andExpect(status().isOk());

        String url = "/api/employer/jobs/" + first + "/applications";
        getAs(employer, url).andExpect(jsonPath("$.totalElements").value(2));
        getAs(employer, url + "?status=REVIEWING").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(saraFirst));
        getAs(employer, url + "?status=OFFER").andExpect(jsonPath("$.totalElements").value(0));

        getAs(sara, "/api/candidate/applications?size=1&page=0").andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].jobTitle").value("Second"));
        getAs(sara, "/api/candidate/applications?size=1&page=1").andExpect(jsonPath("$.content[0].jobTitle").value("First"));
    }

    @Test
    @DisplayName("a candidate still sees the application after the employer closes the job")
    void closedJobKeepsApplication() throws Exception {
        String employer = employerToken("boss@example.com");
        String candidate = candidateToken("sara@example.com");
        long job = publishedJob(employer, "Java Developer", "Riyadh", "FULL_TIME");
        postAs(candidate, apply(job), NOTE).andExpect(status().isCreated());

        postAs(employer, "/api/employer/jobs/" + job + "/close", "").andExpect(status().isOk());

        getAs(candidate, "/api/candidate/applications").andExpect(jsonPath("$.content[0].jobTitle").value("Java Developer"));
    }
}
