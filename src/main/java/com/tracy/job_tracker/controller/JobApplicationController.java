package com.tracy.job_tracker.controller;

import com.tracy.job_tracker.dto.request.CreateJobApplicationRequest;
import com.tracy.job_tracker.dto.request.UpdateApplicationStatusRequest;
import com.tracy.job_tracker.dto.request.UpdateJobApplicationRequest;
import com.tracy.job_tracker.dto.response.JobApplicationResponse;
import com.tracy.job_tracker.dto.response.PageResponse;
import com.tracy.job_tracker.dto.response.StatusCountResponse;
import com.tracy.job_tracker.entity.ApplicationStatus;
import com.tracy.job_tracker.service.JobApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class JobApplicationController {
    private final JobApplicationService service;

    public JobApplicationController(JobApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<JobApplicationResponse> create(@Valid @RequestBody CreateJobApplicationRequest request) {
        JobApplicationResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public JobApplicationResponse findById(@PathVariable Long id) { return service.findById(id); }

    @GetMapping
    public PageResponse<JobApplicationResponse> findAll(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return service.findAll(status, company, keyword, page, size, sortBy, sortDir);
    }

    @GetMapping("/summary")
    public List<StatusCountResponse> summary() { return service.summary(); }

    @PutMapping("/{id}")
    public JobApplicationResponse update(@PathVariable Long id,
                                         @Valid @RequestBody UpdateJobApplicationRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public JobApplicationResponse updateStatus(@PathVariable Long id,
                                               @Valid @RequestBody UpdateApplicationStatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
