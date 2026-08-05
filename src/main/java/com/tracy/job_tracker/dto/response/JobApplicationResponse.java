package com.tracy.job_tracker.dto.response;

import com.tracy.job_tracker.entity.ApplicationStatus;

import java.time.Instant;
import java.time.LocalDate;

public record JobApplicationResponse(
        Long id,
        String jobTitle,
        String jobUrl,
        ApplicationStatus status,
        String source,
        String location,
        LocalDate appliedDate,
        CompanySummaryResponse company,
        Instant createdAt,
        Instant updatedAt
) {
}
