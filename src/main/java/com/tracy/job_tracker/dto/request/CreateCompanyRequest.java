package com.tracy.job_tracker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000)
        @Pattern(regexp = "^$|^https?://.+", message = "must begin with http:// or https://") String website,
        @Size(max = 150) String industry
) {
}
