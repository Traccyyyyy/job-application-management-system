package com.tracy.job_tracker.service;

import com.tracy.job_tracker.dto.request.CreateApplicationNoteRequest;
import com.tracy.job_tracker.dto.response.ApplicationNoteResponse;

import java.util.List;

public interface ApplicationNoteService {
    ApplicationNoteResponse create(Long applicationId, CreateApplicationNoteRequest request);
    List<ApplicationNoteResponse> findAll(Long applicationId);
}
