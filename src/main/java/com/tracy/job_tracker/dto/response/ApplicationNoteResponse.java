package com.tracy.job_tracker.dto.response;

import java.time.Instant;

public record ApplicationNoteResponse(Long id, Long applicationId, String content, Instant createdAt) {
}
