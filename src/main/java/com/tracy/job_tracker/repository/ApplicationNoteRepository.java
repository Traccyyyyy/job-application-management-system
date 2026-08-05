package com.tracy.job_tracker.repository;

import com.tracy.job_tracker.entity.ApplicationNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationNoteRepository extends JpaRepository<ApplicationNote, Long> {
    List<ApplicationNote> findByJobApplicationIdOrderByCreatedAtDescIdDesc(Long applicationId);
    long countByJobApplicationId(Long applicationId);
}
