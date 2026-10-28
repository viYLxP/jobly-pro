package com.jobly.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.jobly.config.DemoDataSeeder;
import com.jobly.support.IntegrationTest;

/**
 * The demo accounts that README lists must really exist and be able to log in.
 * الحسابات التجريبية اللي يذكرها README لازم تكون موجودة فعلًا وتقدر تدخل.
 */
@TestPropertySource(properties = "jobly.seed.enabled=true")
class DemoDataSeederTest extends IntegrationTest {

    @Autowired
    private DemoDataSeeder seeder;

    @Test
    @DisplayName("seeds an admin, a candidate and an employer that can log in, and never seeds twice")
    void seedsOnce() throws Exception {
        seeder.run(null);
        seeder.run(null);

        assertThat(users.count()).isEqualTo(4);
        login("admin@jobly.example", "Admin@12345");
        String candidate = login("candidate@jobly.example", "Candidate@12345");
        String employer = login("employer@jobly.example", "Employer@12345");
        login("employer2@jobly.example", "Employer@12345");

        // The public board has the 10 published jobs (the 11th is a draft). / اللوحة العامة فيها 10 وظائف منشورة (والحادية عشرة مسودة).
        getAs(null, "/api/jobs?size=50").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(10));
        getAs(employer, "/api/employer/jobs").andExpect(jsonPath("$.length()").value(7));
        // The demo candidate already has an application under review, with its timeline. / المرشح التجريبي عنده طلب قيد المراجعة مع تاريخه.
        getAs(candidate, "/api/candidate/applications").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("REVIEWING"))
                .andExpect(jsonPath("$.content[0].timeline.length()").value(2));
        getAs(candidate, "/api/candidate/profile").andExpect(jsonPath("$.headline").value("Java developer"));
    }

    @Test
    @DisplayName("does not touch a database that already has accounts")
    void skipsNonEmptyDatabase() {
        activeUser("sara@example.com", PASSWORD, com.jobly.user.Role.CANDIDATE);

        seeder.run(null);

        assertThat(users.count()).isEqualTo(1);
    }
}
