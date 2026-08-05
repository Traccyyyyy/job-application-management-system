package com.tracy.job_tracker.dto.request;

import com.tracy.job_tracker.entity.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateJobApplicationRequest(
        @NotBlank @Size(max = 150) String jobTitle,
        @NotBlank @Size(max = 1000)
        @Pattern(regexp = "^https?://.+", message = "must begin with http:// or https://") String jobUrl,
        ApplicationStatus status,
        @Size(max = 100) String source,
        @Size(max = 150) String location,
        @PastOrPresent LocalDate appliedDate,
        @NotNull Long companyId
) {
}
