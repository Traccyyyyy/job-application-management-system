package com.tracy.job_tracker.service;

import com.tracy.job_tracker.dto.request.CreateJobApplicationRequest;
import com.tracy.job_tracker.dto.request.UpdateJobApplicationRequest;
import com.tracy.job_tracker.dto.response.JobApplicationResponse;
import com.tracy.job_tracker.dto.response.PageResponse;
import com.tracy.job_tracker.dto.response.StatusCountResponse;
import com.tracy.job_tracker.entity.ApplicationStatus;
import com.tracy.job_tracker.entity.Company;
import com.tracy.job_tracker.entity.JobApplication;
import com.tracy.job_tracker.exception.BadRequestException;
import com.tracy.job_tracker.exception.CompanyNotFoundException;
import com.tracy.job_tracker.exception.ConflictException;
import com.tracy.job_tracker.exception.JobApplicationNotFoundException;
import com.tracy.job_tracker.mapper.JobApplicationMapper;
import com.tracy.job_tracker.repository.CompanyRepository;
import com.tracy.job_tracker.repository.JobApplicationRepository;
import com.tracy.job_tracker.repository.JobApplicationSpecifications;
import com.tracy.job_tracker.repository.StatusCountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class JobApplicationServiceImpl implements JobApplicationService {
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = Map.of(
            ApplicationStatus.SAVED, Set.of(ApplicationStatus.APPLIED, ApplicationStatus.WITHDRAWN),
            ApplicationStatus.APPLIED, Set.of(ApplicationStatus.SCREENING, ApplicationStatus.REJECTED,
                    ApplicationStatus.WITHDRAWN),
            ApplicationStatus.SCREENING, Set.of(ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED,
                    ApplicationStatus.WITHDRAWN),
            ApplicationStatus.INTERVIEW, Set.of(ApplicationStatus.OFFER, ApplicationStatus.REJECTED,
                    ApplicationStatus.WITHDRAWN)
    );
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "appliedDate", "appliedDate",
            "jobTitle", "jobTitle",
            "status", "status",
            "companyName", "company.name"
    );

    private final JobApplicationRepository repository;
    private final CompanyRepository companyRepository;
    private final JobApplicationMapper mapper;
    private final Clock clock;

    public JobApplicationServiceImpl(JobApplicationRepository repository, CompanyRepository companyRepository,
                                     JobApplicationMapper mapper, Clock clock) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public JobApplicationResponse create(CreateJobApplicationRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new CompanyNotFoundException(request.companyId()));
        String jobUrl = mapper.trim(request.jobUrl());
        ensureUniqueUrl(jobUrl, null);
        ApplicationStatus status = request.status() == null ? ApplicationStatus.SAVED : request.status();
        if (status != ApplicationStatus.SAVED && status != ApplicationStatus.APPLIED) {
            throw new ConflictException("Initial status must be SAVED or APPLIED");
        }
        LocalDate appliedDate = request.appliedDate();
        if (status == ApplicationStatus.APPLIED && appliedDate == null) {
            appliedDate = LocalDate.now(clock);
        }
        return mapper.toResponse(repository.save(mapper.toEntity(request, company, status, appliedDate)));
    }

    @Override
    @Transactional(readOnly = true)
    public JobApplicationResponse findById(Long id) {
        return mapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<JobApplicationResponse> findAll(ApplicationStatus status, String company, String keyword,
                                                        int page, int size, String sortBy, String sortDir) {
        if (page < 0) throw new BadRequestException("page must be zero or greater");
        if (size < 1) throw new BadRequestException("size must be greater than zero");
        String property = SORT_FIELDS.get(sortBy);
        if (property == null) throw new BadRequestException("Unsupported sort field: " + sortBy);
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(sortDir);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("sortDir must be asc or desc");
        }
        Specification<JobApplication> specification = Specification.allOf(
                JobApplicationSpecifications.hasStatus(status),
                JobApplicationSpecifications.companyContains(company),
                JobApplicationSpecifications.keywordContains(keyword));
        Page<JobApplication> result = repository.findAll(specification,
                PageRequest.of(page, size, Sort.by(direction, property).and(Sort.by("id"))));
        return new PageResponse<>(result.getContent().stream().map(mapper::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                result.isFirst(), result.isLast());
    }

    @Override
    @Transactional
    public JobApplicationResponse update(Long id, UpdateJobApplicationRequest request) {
        JobApplication application = findEntity(id);
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new CompanyNotFoundException(request.companyId()));
        String jobUrl = mapper.trim(request.jobUrl());
        ensureUniqueUrl(jobUrl, id);
        application.updateDetails(mapper.trim(request.jobTitle()), jobUrl, mapper.trimToNull(request.source()),
                mapper.trimToNull(request.location()), request.appliedDate(), company);
        return mapper.toResponse(repository.saveAndFlush(application));
    }

    @Override
    @Transactional
    public JobApplicationResponse updateStatus(Long id, ApplicationStatus requestedStatus) {
        JobApplication application = findEntity(id);
        ApplicationStatus current = application.getStatus();
        if (current == requestedStatus) return mapper.toResponse(application);
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(requestedStatus)) {
            throw new ConflictException("Status transition from " + current + " to " + requestedStatus
                    + " is not allowed");
        }
        application.changeStatus(requestedStatus);
        if (requestedStatus == ApplicationStatus.APPLIED && application.getAppliedDate() == null) {
            application.setAppliedDate(LocalDate.now(clock));
        }
        return mapper.toResponse(repository.saveAndFlush(application));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        JobApplication application = findEntity(id);
        repository.delete(application);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatusCountResponse> summary() {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (StatusCountProjection projection : repository.countByStatusGrouped()) {
            counts.put(projection.getStatus(), projection.getCount());
        }
        return Arrays.stream(ApplicationStatus.values())
                .map(status -> new StatusCountResponse(status, counts.getOrDefault(status, 0L)))
                .toList();
    }

    private JobApplication findEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> new JobApplicationNotFoundException(id));
    }

    private void ensureUniqueUrl(String url, Long currentId) {
        boolean duplicate = currentId == null ? repository.existsByJobUrl(url)
                : repository.existsByJobUrlAndIdNot(url, currentId);
        if (duplicate) throw new ConflictException("An application with that job URL already exists");
    }
}
