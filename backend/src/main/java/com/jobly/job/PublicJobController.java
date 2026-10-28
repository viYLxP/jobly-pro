package com.jobly.job;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jobly.common.PageResponse;
import com.jobly.job.JobDtos.JobDetail;
import com.jobly.job.JobDtos.JobSummary;

/**
 * The public job board: no account needed.
 * لوحة الوظائف العامة: بدون حاجة لحساب.
 */
@RestController
@RequestMapping("/api/jobs")
public class PublicJobController {

    private final JobService service;

    public PublicJobController(JobService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<JobSummary> search(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String city,
                                    @RequestParam(required = false) JobType type,
                                    @PageableDefault(size = 10) Pageable pageable) {
        // A search box never needs more than a short phrase. / مربع البحث ما يحتاج أكثر من عبارة قصيرة.
        return PageResponse.of(service.search(cut(q, 100), cut(city, 80), type, pageable), JobSummary::of);
    }

    @GetMapping("/{id}")
    JobDetail get(@PathVariable long id) {
        return JobDetail.of(service.getPublic(id));
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
