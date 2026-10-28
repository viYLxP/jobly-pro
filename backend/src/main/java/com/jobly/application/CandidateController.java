package com.jobly.application;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.jobly.alert.AlertDtos.ActiveRequest;
import com.jobly.alert.AlertDtos.AlertRequest;
import com.jobly.alert.AlertDtos.AlertResponse;
import com.jobly.alert.AlertService;
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
    private final AlertService alerts;

    public CandidateController(ApplicationService applications, ProfileService profiles, AlertService alerts) {
        this.applications = applications;
        this.profiles = profiles;
        this.alerts = alerts;
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

    @GetMapping("/alerts")
    List<AlertResponse> alerts() {
        return alerts.list(CurrentUser.id());
    }

    @PostMapping("/alerts")
    @ResponseStatus(HttpStatus.CREATED)
    AlertResponse createAlert(@Valid @RequestBody AlertRequest request) {
        return alerts.create(CurrentUser.id(), request);
    }

    // Pause or resume one alert. / إيقاف مؤقت أو استئناف لتنبيه واحد.
    @PatchMapping("/alerts/{id}")
    AlertResponse setAlertActive(@PathVariable long id, @Valid @RequestBody ActiveRequest request) {
        return alerts.setActive(CurrentUser.id(), id, request.active());
    }

    @DeleteMapping("/alerts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAlert(@PathVariable long id) {
        alerts.delete(CurrentUser.id(), id);
    }
}
