package com.tracy.job_tracker.service;

import com.tracy.job_tracker.dto.request.CreateJobApplicationRequest;
import com.tracy.job_tracker.dto.request.UpdateJobApplicationRequest;
import com.tracy.job_tracker.dto.response.JobApplicationResponse;
import com.tracy.job_tracker.dto.response.PageResponse;
import com.tracy.job_tracker.dto.response.StatusCountResponse;
import com.tracy.job_tracker.entity.ApplicationStatus;

import java.util.List;

public interface JobApplicationService {
    JobApplicationResponse create(CreateJobApplicationRequest request);
    JobApplicationResponse findById(Long id);
    PageResponse<JobApplicationResponse> findAll(ApplicationStatus status, String company, String keyword,
                                                 int page, int size, String sortBy, String sortDir);
    JobApplicationResponse update(Long id, UpdateJobApplicationRequest request);
    JobApplicationResponse updateStatus(Long id, ApplicationStatus status);
    void delete(Long id);
    List<StatusCountResponse> summary();
}
