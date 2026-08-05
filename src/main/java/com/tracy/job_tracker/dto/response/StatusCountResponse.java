package com.tracy.job_tracker.dto.response;

import com.tracy.job_tracker.entity.ApplicationStatus;

public record StatusCountResponse(ApplicationStatus status, long count) {
}
