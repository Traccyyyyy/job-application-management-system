package com.tracy.job_tracker.controller;

import com.tracy.job_tracker.dto.request.CreateCompanyRequest;
import com.tracy.job_tracker.dto.response.CompanyResponse;
import com.tracy.job_tracker.service.CompanyService;
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
@RequestMapping("/api/companies")
public class CompanyController {
    private final CompanyService service;

    public CompanyController(CompanyService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CreateCompanyRequest request) {
        CompanyResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<CompanyResponse> findAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public CompanyResponse findById(@PathVariable Long id) { return service.findById(id); }
}
