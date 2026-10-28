package com.jobly.application;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jobly.application.ApplicationDtos.ApplyRequest;
import com.jobly.application.ApplicationDtos.MyApplication;
import com.jobly.common.CurrentUser;
import com.jobly.common.PageResponse;
import com.jobly.profile.ProfileDtos.ProfileRequest;
import com.jobly.profile.ProfileDtos.ProfileResponse;
import com.jobly.profile.ProfileService;

/**
 * The candidate's area: their profile and their own applications. Only CANDIDATE accounts reach it.
 * منطقة المرشح: ملفه وطلباته. حسابات CANDIDATE فقط توصل لها.
 */
@RestController
@RequestMapping("/api/candidate")
public class CandidateController {

    private final ApplicationService applications;
    private final ProfileService profiles;

    public CandidateController(ApplicationService applications, ProfileService profiles) {
        this.applications = applications;
        this.profiles = profiles;
    }

    @GetMapping("/profile")
    ProfileResponse profile() {
        return profiles.get(CurrentUser.id());
    }

    @PutMapping("/profile")
    ProfileResponse saveProfile(@Valid @RequestBody ProfileRequest request) {
        return profiles.save(CurrentUser.id(), request);
    }

    @PostMapping("/jobs/{jobId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    MyApplication apply(@PathVariable long jobId, @Valid @RequestBody(required = false) ApplyRequest request) {
        return applications.apply(CurrentUser.id(), jobId, request == null ? null : request.coverNote());
    }

    @GetMapping("/applications")
    PageResponse<MyApplication> mine(@PageableDefault(size = 10) Pageable pageable) {
        return applications.listMine(CurrentUser.id(), pageable);
    }

    @GetMapping("/applications/{id}")
    MyApplication one(@PathVariable long id) {
        return applications.getMine(CurrentUser.id(), id);
    }
}
