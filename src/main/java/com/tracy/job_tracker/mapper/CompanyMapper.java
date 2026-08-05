package com.tracy.job_tracker.mapper;

import com.tracy.job_tracker.dto.request.CreateCompanyRequest;
import com.tracy.job_tracker.dto.response.CompanyResponse;
import com.tracy.job_tracker.entity.Company;
import org.springframework.stereotype.Component;

@Component
public class CompanyMapper {

    public Company toEntity(CreateCompanyRequest request) {
        return new Company(trim(request.name()), trimToNull(request.website()), trimToNull(request.industry()));
    }

    public CompanyResponse toResponse(Company company) {
        return new CompanyResponse(company.getId(), company.getName(), company.getWebsite(),
                company.getIndustry(), company.getCreatedAt(), company.getUpdatedAt());
    }

    private String trim(String value) { return value.trim(); }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
