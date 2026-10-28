package com.jobly.job;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    // The employer is loaded in the same query, so showing a company name does not cost one query per job.
    // صاحب العمل يتحمّل بنفس الاستعلام، فعرض اسم الشركة ما يكلّف استعلام لكل وظيفة.
    @EntityGraph(attributePaths = "employer")
    Optional<Job> findWithEmployerById(Long id);

    @EntityGraph(attributePaths = "employer")
    List<Job> findByEmployerIdOrderByCreatedAtDescIdDesc(Long employerId);
}
