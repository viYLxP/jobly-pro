package com.jobly.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobly.profile.ProfileDtos.ProfileRequest;
import com.jobly.profile.ProfileDtos.ProfileResponse;

/**
 * Reads and saves a candidate's own profile.
 * يقرأ ويحفظ ملف المرشح الخاص به.
 */
@Service
public class ProfileService {

    private final CandidateProfileRepository profiles;

    public ProfileService(CandidateProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(long candidateId) {
        return ProfileResponse.of(profiles.findById(candidateId).orElse(null));
    }

    @Transactional
    public ProfileResponse save(long candidateId, ProfileRequest request) {
        CandidateProfile profile = profiles.findById(candidateId).orElseGet(() -> new CandidateProfile(candidateId));
        profile.update(blankToNull(request.headline()), blankToNull(request.city()), request.yearsExperience(),
                blankToNull(request.summary()));
        return ProfileResponse.of(profiles.save(profile));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
