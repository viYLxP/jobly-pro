package com.jobly.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jobly.support.IntegrationTest;

/**
 * Job postings: the employer's life cycle, who can see what, and the public search.
 * إعلانات الوظائف: دورة حياتها عند صاحب العمل، ومن يشوف ماذا، والبحث العام.
 */
class JobsTest extends IntegrationTest {

    @Test
    @DisplayName("a draft is private, a published job is public, a closed job stays viewable but leaves the search")
    void lifeCycle() throws Exception {
        String employer = employerToken("boss@example.com");
        long id = draftJob(employer, "Java Developer", "Riyadh", "FULL_TIME");

        // Draft: not visible to the public, not in the list. / مسودة: غير ظاهرة للعامة وغير موجودة بالقائمة.
        getAs(null, "/api/jobs/" + id).andExpect(status().isNotFound());
        getAs(null, "/api/jobs").andExpect(jsonPath("$.totalElements").value(0));
        getAs(employer, "/api/employer/jobs/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        postAs(employer, "/api/employer/jobs/" + id + "/publish", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED")).andExpect(jsonPath("$.publishedAt").exists());
        getAs(null, "/api/jobs/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Java Developer"))
                .andExpect(jsonPath("$.companyName").value("Test Co"));
        getAs(null, "/api/jobs").andExpect(jsonPath("$.totalElements").value(1));

        postAs(employer, "/api/employer/jobs/" + id + "/close", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        getAs(null, "/api/jobs/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        getAs(null, "/api/jobs").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("the public job pages never show the employer's email or account details")
    void publicPagesHideContactDetails() throws Exception {
        String employer = employerToken("secret-boss@example.com");
        long id = publishedJob(employer, "Java Developer", "Riyadh", "FULL_TIME");

        String list = getAs(null, "/api/jobs").andReturn().getResponse().getContentAsString();
        String detail = getAs(null, "/api/jobs/" + id).andReturn().getResponse().getContentAsString();
        assertThat(list + detail).doesNotContain("secret-boss").doesNotContain("@").doesNotContain("password");
    }

    @Test
    @DisplayName("invalid job data is rejected: missing title, wrong type, negative or reversed salary")
    void validation() throws Exception {
        String employer = employerToken("boss@example.com");

        postAs(employer, "/api/employer/jobs", jobJson("", "Riyadh", "FULL_TIME"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.title").exists());
        postAs(employer, "/api/employer/jobs", jobJson("Dev", "Riyadh", "FOREVER"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        postAs(employer, "/api/employer/jobs", """
                {"title":"Dev","description":"Description here","city":"Riyadh","type":"FULL_TIME","salaryMin":9000,"salaryMax":5000}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.salaryMax").exists());
        postAs(employer, "/api/employer/jobs", """
                {"title":"Dev","description":"Description here","city":"Riyadh","type":"FULL_TIME","salaryMin":-1}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.salaryMin").exists());
        // Salary is optional. / الراتب اختياري.
        postAs(employer, "/api/employer/jobs", """
                {"title":"Dev","description":"Description here","city":"Riyadh","type":"FULL_TIME"}
                """).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("one employer cannot see, change, publish, close or delete another employer's job")
    void ownership() throws Exception {
        String owner = employerToken("owner@example.com");
        String other = employerToken("other@example.com");
        long id = draftJob(owner, "Java Developer", "Riyadh", "FULL_TIME");
        String url = "/api/employer/jobs/" + id;

        getAs(other, url).andExpect(status().isNotFound());
        putAs(other, url, jobJson("Hacked", "Riyadh", "FULL_TIME")).andExpect(status().isNotFound());
        postAs(other, url + "/publish", "").andExpect(status().isNotFound());
        postAs(other, url + "/close", "").andExpect(status().isNotFound());
        deleteAs(other, url).andExpect(status().isNotFound());
        getAs(other, "/api/employer/jobs").andExpect(jsonPath("$.length()").value(0));

        getAs(owner, url).andExpect(jsonPath("$.title").value("Java Developer"));
    }

    @Test
    @DisplayName("each role stays in its own area, and an anonymous caller gets 401")
    void roleAreas() throws Exception {
        String employer = employerToken("boss@example.com");
        String candidate = candidateToken("sara@example.com");

        getAs(candidate, "/api/employer/jobs").andExpect(status().isForbidden());
        postAs(candidate, "/api/employer/jobs", jobJson("Dev", "Riyadh", "FULL_TIME")).andExpect(status().isForbidden());
        getAs(employer, "/api/candidate/applications").andExpect(status().isForbidden());
        getAs(employer, "/api/candidate/profile").andExpect(status().isForbidden());
        getAs(null, "/api/employer/jobs").andExpect(status().isUnauthorized());
        getAs(null, "/api/candidate/applications").andExpect(status().isUnauthorized());
        // Anything not opened by an explicit rule is closed. / أي شي ما انفتح بقاعدة صريحة مقفل.
        getAs(candidate, "/api/unknown").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("state rules: publish only a draft, close only a published job, edit nothing closed")
    void stateRules() throws Exception {
        String employer = employerToken("boss@example.com");
        long id = draftJob(employer, "Java Developer", "Riyadh", "FULL_TIME");
        String url = "/api/employer/jobs/" + id;

        postAs(employer, url + "/close", "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
        putAs(employer, url, jobJson("Senior Java Developer", "Jeddah", "CONTRACT")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Senior Java Developer"));
        postAs(employer, url + "/publish", "").andExpect(status().isOk());
        postAs(employer, url + "/publish", "").andExpect(status().isConflict());
        postAs(employer, url + "/close", "").andExpect(status().isOk());
        putAs(employer, url, jobJson("Late edit", "Riyadh", "FULL_TIME")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    @DisplayName("a job nobody applied to can be deleted; one with applicants cannot")
    void delete() throws Exception {
        String employer = employerToken("boss@example.com");
        String candidate = candidateToken("sara@example.com");
        long empty = draftJob(employer, "Empty", "Riyadh", "FULL_TIME");
        long busy = publishedJob(employer, "Busy", "Riyadh", "FULL_TIME");
        postAs(candidate, "/api/candidate/jobs/" + busy + "/applications", "{}").andExpect(status().isCreated());

        deleteAs(employer, "/api/employer/jobs/" + empty).andExpect(status().isNoContent());
        getAs(employer, "/api/employer/jobs/" + empty).andExpect(status().isNotFound());
        deleteAs(employer, "/api/employer/jobs/" + busy).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_USE"));
    }

    @Test
    @DisplayName("the employer's list shows every status with the number of applicants")
    void employerList() throws Exception {
        String employer = employerToken("boss@example.com");
        String sara = candidateToken("sara@example.com");
        String omar = candidateToken("omar@example.com");
        long published = publishedJob(employer, "Published", "Riyadh", "FULL_TIME");
        draftJob(employer, "Draft", "Riyadh", "FULL_TIME");
        postAs(sara, "/api/candidate/jobs/" + published + "/applications", "{}").andExpect(status().isCreated());
        postAs(omar, "/api/candidate/jobs/" + published + "/applications", "{}").andExpect(status().isCreated());

        getAs(employer, "/api/employer/jobs").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Newest first: the draft was created last. / الأحدث أولًا: المسودة انصنعت أخيرًا.
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[0].applicantCount").value(0))
                .andExpect(jsonPath("$[1].applicantCount").value(2));
    }

    // ------------------------------------------------------------------ public search

    private void sampleBoard() throws Exception {
        activeUser("najd@example.com", PASSWORD, com.jobly.user.Role.EMPLOYER, "Najd Digital", "en");
        activeUser("sea@example.com", PASSWORD, com.jobly.user.Role.EMPLOYER, "Red Sea Logistics", "en");
        String najd = login("najd@example.com", PASSWORD);
        String sea = login("sea@example.com", PASSWORD);
        publishedJob(najd, "Senior Java Developer", "Riyadh", "FULL_TIME");
        publishedJob(najd, "Angular Developer", "Riyadh", "FULL_TIME");
        publishedJob(sea, "Warehouse Supervisor", "Jeddah", "FULL_TIME");
        publishedJob(sea, "Customer Support", "Jeddah", "PART_TIME");
        publishedJob(sea, "Java Intern", "Dammam", "INTERNSHIP");
    }

    @Test
    @DisplayName("search by words, city and type, in any letter case, all filters combined with AND")
    void searchFilters() throws Exception {
        sampleBoard();

        getAs(null, "/api/jobs").andExpect(jsonPath("$.totalElements").value(5));
        getAs(null, "/api/jobs?q=java").andExpect(jsonPath("$.totalElements").value(2));
        getAs(null, "/api/jobs?q=JAVA developer").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Senior Java Developer"));
        getAs(null, "/api/jobs?city=riyadh").andExpect(jsonPath("$.totalElements").value(2));
        getAs(null, "/api/jobs?type=PART_TIME").andExpect(jsonPath("$.totalElements").value(1));
        getAs(null, "/api/jobs?q=java&city=Dammam&type=INTERNSHIP").andExpect(jsonPath("$.totalElements").value(1));
        getAs(null, "/api/jobs?q=java&city=Jeddah").andExpect(jsonPath("$.totalElements").value(0));
        // The company name is searched too. / اسم الشركة يُبحث فيه كذلك.
        getAs(null, "/api/jobs?q=najd").andExpect(jsonPath("$.totalElements").value(2));
        getAs(null, "/api/jobs?q=red sea").andExpect(jsonPath("$.totalElements").value(3));
        getAs(null, "/api/jobs?q=red sea java").andExpect(jsonPath("$.totalElements").value(1));
        getAs(null, "/api/jobs?type=NOPE").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("% and _ in a search are plain characters, not wildcards")
    void wildcardsAreLiteral() throws Exception {
        sampleBoard();

        getAs(null, "/api/jobs?q=%25").andExpect(jsonPath("$.totalElements").value(0));
        getAs(null, "/api/jobs?q=_ava").andExpect(jsonPath("$.totalElements").value(0));
        getAs(null, "/api/jobs?q=%25ava").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("paging is stable, newest first, with correct totals; size is capped at 100")
    void paging() throws Exception {
        String employer = employerToken("boss@example.com");
        for (int i = 1; i <= 5; i++) {
            publishedJob(employer, "Job " + i, "Riyadh", "FULL_TIME");
        }

        getAs(null, "/api/jobs?size=2&page=0").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Job 5"))
                .andExpect(jsonPath("$.content[1].title").value("Job 4"));
        getAs(null, "/api/jobs?size=2&page=2").andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Job 1"));
        getAs(null, "/api/jobs?size=100000").andExpect(jsonPath("$.size").value(100));
    }
}
