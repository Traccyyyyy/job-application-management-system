package com.tracy.job_tracker.mapper;

import com.tracy.job_tracker.dto.request.CreateApplicationNoteRequest;
import com.tracy.job_tracker.dto.response.ApplicationNoteResponse;
import com.tracy.job_tracker.entity.ApplicationNote;
import com.tracy.job_tracker.entity.JobApplication;
import org.springframework.stereotype.Component;

@Component
public class ApplicationNoteMapper {
    public ApplicationNote toEntity(CreateApplicationNoteRequest request, JobApplication application) {
        return new ApplicationNote(application, request.content().trim());
    }

    public ApplicationNoteResponse toResponse(ApplicationNote note) {
        return new ApplicationNoteResponse(note.getId(), note.getJobApplication().getId(),
                note.getContent(), note.getCreatedAt());
    }
}
