package com.tracy.job_tracker.controller;

import com.tracy.job_tracker.dto.response.CompanyResponse;
import com.tracy.job_tracker.dto.response.CompanySummaryResponse;
import com.tracy.job_tracker.dto.response.JobApplicationResponse;
import com.tracy.job_tracker.dto.response.PageResponse;
import com.tracy.job_tracker.entity.ApplicationStatus;
import com.tracy.job_tracker.exception.ConflictException;
import com.tracy.job_tracker.exception.GlobalExceptionHandler;
import com.tracy.job_tracker.exception.JobApplicationNotFoundException;
import com.tracy.job_tracker.service.CompanyService;
import com.tracy.job_tracker.service.JobApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ControllerTests {
    @Mock CompanyService companyService;
    @Mock JobApplicationService applicationService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new CompanyController(companyService),
                        new JobApplicationController(applicationService), new HealthController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void companyCreateReturns201AndLocation() throws Exception {
        when(companyService.create(any())).thenReturn(companyResponse());
        mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"website\":\"https://acme.test\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "http://localhost/api/companies/1"));
    }

    @Test
    void invalidCompanyReturns400() throws Exception {
        mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void applicationCreateReturns201AndLocation() throws Exception {
        when(applicationService.create(any())).thenReturn(applicationResponse());
        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Engineer\",\"jobUrl\":\"https://jobs.test/1\",\"companyId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/applications/10"));
    }

    @Test
    void invalidApplicationReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobTitle\":\"\",\"jobUrl\":\"ftp://bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.jobTitle").exists())
                .andExpect(jsonPath("$.fieldErrors.jobUrl").exists())
                .andExpect(jsonPath("$.fieldErrors.companyId").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Engineer\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or invalid JSON request"));
    }

    @Test
    void missingResourceReturns404() throws Exception {
        when(applicationService.findById(99L)).thenThrow(new JobApplicationNotFoundException(99L));
        mvc.perform(get("/api/applications/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void duplicateConflictReturns409() throws Exception {
        when(companyService.create(any())).thenThrow(new ConflictException("duplicate"));
        mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("duplicate"));
    }

    @Test
    void databaseConstraintConflictReturnsSafe409() throws Exception {
        when(companyService.create(any())).thenThrow(new DataIntegrityViolationException("sensitive SQL detail"));
        mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A unique or relational constraint was violated"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("sensitive SQL detail"))));
    }

    @Test
    void invalidTransitionReturns409() throws Exception {
        when(applicationService.updateStatus(10L, ApplicationStatus.OFFER))
                .thenThrow(new ConflictException("invalid transition"));
        mvc.perform(patch("/api/applications/10/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listHasStablePageShape() throws Exception {
        when(applicationService.findAll(null, null, null, 0, 20, "createdAt", "desc"))
                .thenReturn(new PageResponse<>(List.of(applicationResponse()), 0, 20, 1, 1, true, true));
        mvc.perform(get("/api/applications")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].jobTitle").value("Engineer"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.first").value(true));
    }

    @Test
    void summaryIncludesEveryStatusIncludingZeroCounts() throws Exception {
        when(applicationService.summary()).thenReturn(Arrays.stream(ApplicationStatus.values())
                .map(value -> new com.tracy.job_tracker.dto.response.StatusCountResponse(
                        value, value == ApplicationStatus.SAVED ? 2L : 0L))
                .toList());
        mvc.perform(get("/api/applications/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(ApplicationStatus.values().length))
                .andExpect(jsonPath("$[?(@.status == 'SAVED')].count").value(2))
                .andExpect(jsonPath("$[?(@.status == 'OFFER')].count").value(0));
    }

    @Test
    void invalidQueryInputReturns400() throws Exception {
        mvc.perform(get("/api/applications?status=UNKNOWN")).andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns204() throws Exception {
        doNothing().when(applicationService).delete(10L);
        mvc.perform(delete("/api/applications/10")).andExpect(status().isNoContent());
    }

    @Test
    void healthReturnsOK() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    private CompanyResponse companyResponse() {
        return new CompanyResponse(1L, "Acme", "https://acme.test", null, Instant.EPOCH, Instant.EPOCH);
    }

    private JobApplicationResponse applicationResponse() {
        return new JobApplicationResponse(10L, "Engineer", "https://jobs.test/1", ApplicationStatus.SAVED,
                "LinkedIn", "Sydney", null, new CompanySummaryResponse(1L, "Acme"),
                Instant.EPOCH, Instant.EPOCH);
    }
}
