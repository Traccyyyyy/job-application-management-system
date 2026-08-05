package com.tracy.job_tracker.dto.response;

import java.time.Instant;

public record CompanyResponse(
        Long id,
        String name,
        String website,
        String industry,
        Instant createdAt,
        Instant updatedAt
) {
}
