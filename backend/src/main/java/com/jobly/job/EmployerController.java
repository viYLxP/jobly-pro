package com.jobly.job;

import java.util.List;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jobly.application.ApplicationDtos.Applicant;
import com.jobly.application.ApplicationDtos.StatusRequest;
import com.jobly.application.ApplicationService;
import com.jobly.application.ApplicationStatus;
import com.jobly.common.CurrentUser;
import com.jobly.common.PageResponse;
import com.jobly.job.JobDtos.EmployerJob;
import com.jobly.job.JobDtos.JobRequest;

/**
 * The employer's area: their own jobs and the people who applied. Only EMPLOYER accounts reach it
 * (SecurityConfig), and every method also checks that the job belongs to the caller.
 * منطقة صاحب العمل: وظائفه والمتقدمين عليها. حسابات EMPLOYER فقط توصل لها (SecurityConfig)،
 * وكل دالة تتأكد كذلك إن الوظيفة تخص المستدعي.
 */
@RestController
@RequestMapping("/api/employer")
public class EmployerController {

    private final JobService jobs;
    private final ApplicationService applications;

    public EmployerController(JobService jobs, ApplicationService applications) {
        this.jobs = jobs;
        this.applications = applications;
    }

    @GetMapping("/jobs")
    List<EmployerJob> list() {
        return jobs.listOwn(CurrentUser.id());
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    EmployerJob create(@Valid @RequestBody JobRequest request) {
        return jobs.create(CurrentUser.id(), request);
    }

    @GetMapping("/jobs/{id}")
    EmployerJob get(@PathVariable long id) {
        return jobs.getOwn(CurrentUser.id(), id);
    }

    @PutMapping("/jobs/{id}")
    EmployerJob update(@PathVariable long id, @Valid @RequestBody JobRequest request) {
        return jobs.update(CurrentUser.id(), id, request);
    }

    @PostMapping("/jobs/{id}/publish")
    EmployerJob publish(@PathVariable long id) {
        return jobs.publish(CurrentUser.id(), id);
    }

    @PostMapping("/jobs/{id}/close")
    EmployerJob close(@PathVariable long id) {
        return jobs.close(CurrentUser.id(), id);
    }

    @DeleteMapping("/jobs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        jobs.delete(CurrentUser.id(), id);
    }

    @GetMapping("/jobs/{id}/applications")
    PageResponse<Applicant> applicants(@PathVariable long id,
                                       @RequestParam(required = false) ApplicationStatus status,
                                       @PageableDefault(size = 20) Pageable pageable) {
        return applications.listForJob(CurrentUser.id(), id, status, pageable);
    }

    @PatchMapping("/applications/{id}/status")
    Applicant changeStatus(@PathVariable long id, @Valid @RequestBody StatusRequest request) {
        return applications.changeStatus(CurrentUser.id(), id, request.status());
    }
}
