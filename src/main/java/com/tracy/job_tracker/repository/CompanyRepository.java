package com.tracy.job_tracker.repository;

import com.tracy.job_tracker.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByNameIgnoreCase(String name);
    Optional<Company> findByNameIgnoreCase(String name);
    List<Company> findAllByOrderByNameAsc();
}
