package com.jobly.profile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record ProfileRequest(
            @Size(max = 150) String headline,
            @Size(max = 80) String city,
            @Min(0) @Max(60) Integer yearsExperience,
            @Size(max = 2000) String summary) {
    }

    public record ProfileResponse(String headline, String city, Integer yearsExperience, String summary) {

        public static ProfileResponse of(CandidateProfile profile) {
            return profile == null ? new ProfileResponse(null, null, null, null)
                    : new ProfileResponse(profile.getHeadline(), profile.getCity(), profile.getYearsExperience(),
                    profile.getSummary());
        }
    }
}
