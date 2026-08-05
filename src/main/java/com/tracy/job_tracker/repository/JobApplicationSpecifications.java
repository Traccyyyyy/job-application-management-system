package com.tracy.job_tracker.repository;

import com.tracy.job_tracker.entity.ApplicationStatus;
import com.tracy.job_tracker.entity.JobApplication;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class JobApplicationSpecifications {
    private JobApplicationSpecifications() {
    }

    public static Specification<JobApplication> hasStatus(ApplicationStatus status) {
        return status == null ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<JobApplication> companyContains(String company) {
        if (company == null || company.isBlank()) return Specification.unrestricted();
        String pattern = containsPattern(company);
        return (root, query, cb) -> cb.like(cb.lower(root.join("company").get("name")), pattern, '\\');
    }

    public static Specification<JobApplication> keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) return Specification.unrestricted();
        String pattern = containsPattern(keyword);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("jobTitle")), pattern, '\\'),
                cb.like(cb.lower(root.join("company").get("name")), pattern, '\\'),
                cb.like(cb.lower(root.get("source")), pattern, '\\'),
                cb.like(cb.lower(root.get("location")), pattern, '\\')
        );
    }

    private static String containsPattern(String value) {
        String escaped = value.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
