package com.tracy.job_tracker.controller;

import com.tracy.job_tracker.dto.request.CreateApplicationNoteRequest;
import com.tracy.job_tracker.dto.response.ApplicationNoteResponse;
import com.tracy.job_tracker.service.ApplicationNoteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/applications/{applicationId}/notes")
public class ApplicationNoteController {
    private final ApplicationNoteService service;

    public ApplicationNoteController(ApplicationNoteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApplicationNoteResponse> create(@PathVariable Long applicationId,
                                                          @Valid @RequestBody CreateApplicationNoteRequest request) {
        ApplicationNoteResponse response = service.create(applicationId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{noteId}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<ApplicationNoteResponse> findAll(@PathVariable Long applicationId) {
        return service.findAll(applicationId);
    }
}
