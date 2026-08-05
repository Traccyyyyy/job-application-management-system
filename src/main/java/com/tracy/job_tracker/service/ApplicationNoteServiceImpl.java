package com.tracy.job_tracker.service;

import com.tracy.job_tracker.dto.request.CreateApplicationNoteRequest;
import com.tracy.job_tracker.dto.response.ApplicationNoteResponse;
import com.tracy.job_tracker.entity.JobApplication;
import com.tracy.job_tracker.exception.JobApplicationNotFoundException;
import com.tracy.job_tracker.mapper.ApplicationNoteMapper;
import com.tracy.job_tracker.repository.ApplicationNoteRepository;
import com.tracy.job_tracker.repository.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ApplicationNoteServiceImpl implements ApplicationNoteService {
    private final ApplicationNoteRepository noteRepository;
    private final JobApplicationRepository applicationRepository;
    private final ApplicationNoteMapper mapper;

    public ApplicationNoteServiceImpl(ApplicationNoteRepository noteRepository,
                                      JobApplicationRepository applicationRepository,
                                      ApplicationNoteMapper mapper) {
        this.noteRepository = noteRepository;
        this.applicationRepository = applicationRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ApplicationNoteResponse create(Long applicationId, CreateApplicationNoteRequest request) {
        JobApplication application = findApplication(applicationId);
        return mapper.toResponse(noteRepository.save(mapper.toEntity(request, application)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationNoteResponse> findAll(Long applicationId) {
        findApplication(applicationId);
        return noteRepository.findByJobApplicationIdOrderByCreatedAtDescIdDesc(applicationId).stream()
                .map(mapper::toResponse).toList();
    }

    private JobApplication findApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new JobApplicationNotFoundException(applicationId));
    }
}
