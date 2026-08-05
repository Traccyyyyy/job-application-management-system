package com.tracy.job_tracker.integration;

import com.tracy.job_tracker.repository.CompanyRepository;
import com.tracy.job_tracker.repository.JobApplicationRepository;
import com.tracy.job_tracker.repository.ApplicationNoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JobApplicationRepository applications;
    @Autowired CompanyRepository companies;
    @Autowired ApplicationNoteRepository notes;

    @BeforeEach
    void cleanDatabase() {
        notes.deleteAll();
        applications.deleteAll();
        companies.deleteAll();
    }

    @Test
    void completeCompanyAndApplicationLifecycle() throws Exception {
        String companyJson = mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"website\":\"https://acme.test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long companyId = Long.parseLong(companyJson.replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

        String applicationJson = mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Engineer\",\"jobUrl\":\"https://jobs.test/full-flow\","
                                + "\"source\":\"Referral\",\"location\":\"Sydney\",\"companyId\":" + companyId + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SAVED"))
                .andReturn().getResponse().getContentAsString();
        long applicationId = Long.parseLong(applicationJson.replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

        mvc.perform(post("/api/applications/{id}/notes", applicationId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Note\",\"unknown\":true}"))
                .andExpect(status().isBadRequest());

        String firstNoteJson = mvc.perform(post("/api/applications/{id}/notes", applicationId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"First note\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.applicationId").value(applicationId))
                .andReturn().getResponse().getContentAsString();
        long firstNoteId = Long.parseLong(firstNoteJson.replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));
        String secondNoteJson = mvc.perform(post("/api/applications/{id}/notes", applicationId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Second note\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.content").value("Second note"))
                .andReturn().getResponse().getContentAsString();
        long secondNoteId = Long.parseLong(secondNoteJson.replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));
        mvc.perform(get("/api/applications/{id}/notes", applicationId)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(secondNoteId))
                .andExpect(jsonPath("$[0].content").value("Second note"))
                .andExpect(jsonPath("$[1].id").value(firstNoteId))
                .andExpect(jsonPath("$[1].applicationId").value(applicationId));
        assertThat(notes.findById(firstNoteId).orElseThrow().getJobApplication().getId()).isEqualTo(applicationId);

        mvc.perform(put("/api/applications/{id}", applicationId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Engineer\",\"jobUrl\":\"https://jobs.test/full-flow\","
                                + "\"companyId\":" + companyId + ",\"status\":\"APPLIED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/applications/{id}", applicationId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.company.companyName").value("Acme"));
        mvc.perform(patch("/api/applications/{id}/status", applicationId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPLIED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.appliedDate").isNotEmpty());
        mvc.perform(patch("/api/applications/{id}/status", applicationId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SCREENING\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCREENING"));
        mvc.perform(get("/api/applications/{id}", applicationId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCREENING"))
                .andExpect(jsonPath("$.appliedDate").isNotEmpty());
        mvc.perform(delete("/api/applications/{id}", applicationId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/applications/{id}", applicationId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/applications/{id}/notes", applicationId)).andExpect(status().isNotFound());
        assertThat(notes.countByJobApplicationId(applicationId)).isZero();
    }
}
