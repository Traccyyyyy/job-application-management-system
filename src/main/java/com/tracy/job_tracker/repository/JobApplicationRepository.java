package com.tracy.job_tracker.repository;

import com.tracy.job_tracker.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long>,
        JpaSpecificationExecutor<JobApplication> {

    boolean existsByJobUrl(String jobUrl);
    boolean existsByJobUrlAndIdNot(String jobUrl, Long id);

    @Override
    @EntityGraph(attributePaths = "company")
    Page<JobApplication> findAll(Specification<JobApplication> specification, Pageable pageable);

    @Query("select j.status as status, count(j) as count from JobApplication j group by j.status")
    List<StatusCountProjection> countByStatusGrouped();
}
