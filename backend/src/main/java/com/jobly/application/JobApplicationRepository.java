package com.jobly.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    boolean existsByJobIdAndCandidateId(Long jobId, Long candidateId);

    long countByJobId(Long jobId);

    // The job and its employer come along in one query, because every row of "my applications" shows them.
    // الوظيفة وصاحبها تجي مع نفس الاستعلام، لأن كل صف في "طلباتي" يعرضهم.
    @EntityGraph(attributePaths = {"job", "job.employer"})
    Page<JobApplication> findByCandidateId(Long candidateId, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "job.employer"})
    Optional<JobApplication> findByIdAndCandidateId(Long id, Long candidateId);

    @EntityGraph(attributePaths = {"job", "job.employer", "candidate"})
    Page<JobApplication> findByJobId(Long jobId, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "job.employer", "candidate"})
    Page<JobApplication> findByJobIdAndStatus(Long jobId, ApplicationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "job.employer", "candidate"})
    Optional<JobApplication> findWithDetailsById(Long id);

    @Query("select a.job.id, count(a) from JobApplication a where a.job.id in :ids group by a.job.id")
    List<Object[]> countRowsByJobs(@Param("ids") Collection<Long> ids);

    /** Applicants per job for a set of jobs, in one query. / عدد المتقدمين لكل وظيفة لمجموعة وظائف، باستعلام واحد. */
    default Map<Long, Long> countByJobs(Collection<Long> jobIds) {
        Map<Long, Long> result = new HashMap<>();
        if (jobIds.isEmpty()) {
            return result;
        }
        for (Object[] row : countRowsByJobs(jobIds)) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }
}
