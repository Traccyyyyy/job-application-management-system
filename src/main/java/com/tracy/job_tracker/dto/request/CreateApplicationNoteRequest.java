package com.tracy.job_tracker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApplicationNoteRequest(
        @NotBlank(message = "content is required")
        @Size(max = 2000, message = "content must not exceed 2000 characters")
        String content
) {
}
