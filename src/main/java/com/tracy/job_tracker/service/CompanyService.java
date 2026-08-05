package com.tracy.job_tracker.service;

import com.tracy.job_tracker.dto.request.CreateCompanyRequest;
import com.tracy.job_tracker.dto.response.CompanyResponse;

import java.util.List;

public interface CompanyService {
    CompanyResponse create(CreateCompanyRequest request);
    List<CompanyResponse> findAll();
    CompanyResponse findById(Long id);
}
