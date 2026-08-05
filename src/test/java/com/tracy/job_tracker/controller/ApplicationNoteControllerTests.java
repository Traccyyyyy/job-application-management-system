package com.tracy.job_tracker.controller;

import com.tracy.job_tracker.dto.response.ApplicationNoteResponse;
import com.tracy.job_tracker.exception.GlobalExceptionHandler;
import com.tracy.job_tracker.exception.JobApplicationNotFoundException;
import com.tracy.job_tracker.service.ApplicationNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApplicationNoteControllerTests {
    @Mock ApplicationNoteService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ApplicationNoteController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator).build();
    }

    @Test
    void createReturns201LocationAndResponseShape() throws Exception {
        when(service.create(org.mockito.ArgumentMatchers.eq(10L), any())).thenReturn(note(2L, "Followed up"));
        mvc.perform(post("/api/applications/10/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Followed up\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/applications/10/notes/2"))
                .andExpect(jsonPath("$.id").value(2)).andExpect(jsonPath("$.applicationId").value(10))
                .andExpect(jsonPath("$.content").value("Followed up"))
                .andExpect(jsonPath("$.createdAt").value("2026-08-05T01:00:00Z"));
    }

    @Test
    void blankAndMissingContentReturnFieldError() throws Exception {
        mvc.perform(post("/api/applications/10/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.content").exists());
        mvc.perform(post("/api/applications/10/notes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    void excessiveContentReturns400() throws Exception {
        String body = "{\"content\":\"" + "x".repeat(2001) + "\"}";
        mvc.perform(post("/api/applications/10/notes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/applications/10/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"broken\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or invalid JSON request"));
    }

    @Test
    void createForMissingParentReturns404() throws Exception {
        when(service.create(org.mockito.ArgumentMatchers.eq(99L), any()))
                .thenThrow(new JobApplicationNotFoundException(99L));
        mvc.perform(post("/api/applications/99/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Note\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.path")
                        .value("/api/applications/99/notes"));
    }

    @Test
    void listReturnsArrayAndEmptyList() throws Exception {
        when(service.findAll(10L)).thenReturn(List.of(note(2L, "New"), note(1L, "Old")));
        when(service.findAll(11L)).thenReturn(List.of());
        mvc.perform(get("/api/applications/10/notes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].content").value("New"))
                .andExpect(jsonPath("$[0].applicationId").value(10));
        mvc.perform(get("/api/applications/11/notes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listForMissingParentReturns404() throws Exception {
        when(service.findAll(99L)).thenThrow(new JobApplicationNotFoundException(99L));
        mvc.perform(get("/api/applications/99/notes")).andExpect(status().isNotFound());
    }

    private ApplicationNoteResponse note(long id, String content) {
        return new ApplicationNoteResponse(id, 10L, content, Instant.parse("2026-08-05T01:00:00Z"));
    }
}
