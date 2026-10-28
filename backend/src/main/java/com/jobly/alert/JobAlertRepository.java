package com.jobly.alert;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jobly.job.JobType;

public interface JobAlertRepository extends JpaRepository<JobAlert, Long> {

    List<JobAlert> findByCandidateIdOrderByIdDesc(Long candidateId);

    long countByCandidateId(Long candidateId);

    Optional<JobAlert> findByIdAndCandidateId(Long id, Long candidateId);

    /**
     * Active alerts of active candidates whose type and city fit the job. The keyword is checked afterwards in
     * Java, because it needs to look inside the job's text.
     * تنبيهات فعالة لمرشحين فعالين نوعها ومدينتها يناسبان الوظيفة. الكلمة المفتاحية تُفحص بعدين في Java،
     * لأنها تحتاج تنظر داخل نص الوظيفة.
     */
    @Query("""
            select a from JobAlert a join fetch a.candidate c
            where a.active = true and c.status = com.jobly.user.UserStatus.ACTIVE
              and (a.type is null or a.type = :type)
              and (a.city is null or lower(a.city) = :city)
            """)
    List<JobAlert> findMatchingTypeAndCity(@Param("type") JobType type, @Param("city") String lowerCaseCity);

    @Modifying
    @Query("update JobAlert a set a.active = false where a.candidate.id = :candidateId and a.active = true")
    int deactivateAllOf(@Param("candidateId") Long candidateId);
}
