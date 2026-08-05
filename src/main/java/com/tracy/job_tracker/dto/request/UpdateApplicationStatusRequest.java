package com.tracy.job_tracker.dto.request;

import com.tracy.job_tracker.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateApplicationStatusRequest(@NotNull ApplicationStatus status) {
}
