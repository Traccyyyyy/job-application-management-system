package com.tracy.job_tracker.mapper;

import com.tracy.job_tracker.dto.request.CreateJobApplicationRequest;
import com.tracy.job_tracker.dto.response.CompanySummaryResponse;
import com.tracy.job_tracker.dto.response.JobApplicationResponse;
import com.tracy.job_tracker.entity.ApplicationStatus;
import com.tracy.job_tracker.entity.Company;
import com.tracy.job_tracker.entity.JobApplication;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class JobApplicationMapper {

    public JobApplication toEntity(CreateJobApplicationRequest request, Company company,
                                   ApplicationStatus status, LocalDate appliedDate) {
        return new JobApplication(trim(request.jobTitle()), trim(request.jobUrl()), status,
                trimToNull(request.source()), trimToNull(request.location()), appliedDate, company);
    }

    public JobApplicationResponse toResponse(JobApplication application) {
        Company company = application.getCompany();
        return new JobApplicationResponse(application.getId(), application.getJobTitle(),
                application.getJobUrl(), application.getStatus(), application.getSource(),
                application.getLocation(), application.getAppliedDate(),
                new CompanySummaryResponse(company.getId(), company.getName()),
                application.getCreatedAt(), application.getUpdatedAt());
    }

    public String trim(String value) { return value.trim(); }

    public String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
