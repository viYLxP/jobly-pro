package com.jobly.profile;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * What a candidate shares with employers they apply to. One row per candidate, created on first save.
 * اللي يشاركه المرشح مع أصحاب العمل اللي يقدّم عندهم. صف واحد لكل مرشح، ينشأ عند أول حفظ.
 */
@Entity
@Table(name = "candidate_profile")
public class CandidateProfile {

    // Same value as the candidate's account id. / نفس رقم حساب المرشح.
    @Id
    @Column(name = "user_id")
    private Long userId;

    private String headline;

    private String city;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    private String summary;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // A wrapper type: null means "never saved", which is how Spring Data knows to INSERT instead of UPDATE for an id we assign ourselves.
    // نوع wrapper: null يعني "ما انحفظ قبل"، وبه يعرف Spring Data إنه INSERT مو UPDATE لرقم نحدده نحن.
    @Version
    private Long version;

    // Required by JPA. / مطلوب من JPA.
    protected CandidateProfile() {
    }

    public CandidateProfile(Long userId) {
        this.userId = userId;
    }

    public void update(String headline, String city, Integer yearsExperience, String summary) {
        this.headline = headline;
        this.city = city;
        this.yearsExperience = yearsExperience;
        this.summary = summary;
        this.updatedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public String getHeadline() {
        return headline;
    }

    public String getCity() {
        return city;
    }

    public Integer getYearsExperience() {
        return yearsExperience;
    }

    public String getSummary() {
        return summary;
    }
}
