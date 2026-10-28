package com.jobly.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jobly.support.IntegrationTest;

class ProfileTest extends IntegrationTest {

    @Test
    @DisplayName("a new candidate has an empty profile; saving and saving again keeps one profile")
    void saveAndRead() throws Exception {
        String candidate = candidateToken("sara@example.com");

        getAs(candidate, "/api/candidate/profile").andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").doesNotExist());

        putAs(candidate, "/api/candidate/profile", """
                {"headline":"Java developer","city":"Riyadh","yearsExperience":4,"summary":"Spring Boot."}
                """).andExpect(status().isOk());
        putAs(candidate, "/api/candidate/profile", """
                {"headline":"Senior Java developer","city":"Jeddah","yearsExperience":5,"summary":"  "}
                """).andExpect(status().isOk());

        getAs(candidate, "/api/candidate/profile").andExpect(jsonPath("$.headline").value("Senior Java developer"))
                .andExpect(jsonPath("$.city").value("Jeddah")).andExpect(jsonPath("$.yearsExperience").value(5))
                // A blank summary is stored as empty. / الملخص الفارغ يُحفظ كفارغ.
                .andExpect(jsonPath("$.summary").doesNotExist());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from candidate_profile",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("invalid profile data is rejected")
    void validation() throws Exception {
        String candidate = candidateToken("sara@example.com");

        putAs(candidate, "/api/candidate/profile", "{\"yearsExperience\":61}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.yearsExperience").exists());
        putAs(candidate, "/api/candidate/profile", "{\"yearsExperience\":-1}").andExpect(status().isBadRequest());
        putAs(candidate, "/api/candidate/profile", "{\"summary\":\"" + "x".repeat(2001) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.summary").exists());
    }

    @Test
    @DisplayName("each candidate has their own profile")
    void profilesAreSeparate() throws Exception {
        String sara = candidateToken("sara@example.com");
        String omar = candidateToken("omar@example.com");

        putAs(sara, "/api/candidate/profile", "{\"headline\":\"Sara's headline\"}").andExpect(status().isOk());

        getAs(omar, "/api/candidate/profile").andExpect(jsonPath("$.headline").doesNotExist());
    }
}
