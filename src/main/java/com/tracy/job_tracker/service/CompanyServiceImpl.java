package com.tracy.job_tracker.service;

import com.tracy.job_tracker.dto.request.CreateCompanyRequest;
import com.tracy.job_tracker.dto.response.CompanyResponse;
import com.tracy.job_tracker.entity.Company;
import com.tracy.job_tracker.exception.CompanyNotFoundException;
import com.tracy.job_tracker.exception.ConflictException;
import com.tracy.job_tracker.mapper.CompanyMapper;
import com.tracy.job_tracker.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompanyServiceImpl implements CompanyService {
    private final CompanyRepository repository;
    private final CompanyMapper mapper;

    public CompanyServiceImpl(CompanyRepository repository, CompanyMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public CompanyResponse create(CreateCompanyRequest request) {
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A company with that name already exists");
        }
        Company company = repository.save(mapper.toEntity(request));
        return mapper.toResponse(company);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyResponse> findAll() {
        return repository.findAllByOrderByNameAsc().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyResponse findById(Long id) {
        return mapper.toResponse(repository.findById(id).orElseThrow(() -> new CompanyNotFoundException(id)));
    }
}
