package com.jobly.api;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(users.count()).isEqualTo(3);
        login("admin@jobly.example", "Admin@12345");
        login("candidate@jobly.example", "Candidate@12345");
        login("employer@jobly.example", "Employer@12345");
    }

    @Test
    @DisplayName("does not touch a database that already has accounts")
    void skipsNonEmptyDatabase() {
        activeUser("sara@example.com", PASSWORD, com.jobly.user.Role.CANDIDATE);

        seeder.run(null);

        assertThat(users.count()).isEqualTo(1);
    }
}
