package com.tracy.job_tracker.service;

import com.tracy.job_tracker.dto.request.CreateApplicationNoteRequest;
import com.tracy.job_tracker.entity.ApplicationNote;
import com.tracy.job_tracker.entity.ApplicationStatus;
import com.tracy.job_tracker.entity.Company;
import com.tracy.job_tracker.entity.JobApplication;
import com.tracy.job_tracker.exception.JobApplicationNotFoundException;
import com.tracy.job_tracker.mapper.ApplicationNoteMapper;
import com.tracy.job_tracker.repository.ApplicationNoteRepository;
import com.tracy.job_tracker.repository.JobApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationNoteServiceTests {
    @Mock ApplicationNoteRepository notes;
    @Mock JobApplicationRepository applications;
    private ApplicationNoteServiceImpl service;
    private JobApplication application;

    @BeforeEach
    void setUp() {
        application = new JobApplication("Engineer", "https://jobs.test/notes", ApplicationStatus.SAVED,
                null, null, null, new Company("Acme", null, null));
        service = new ApplicationNoteServiceImpl(notes, applications, new ApplicationNoteMapper());
    }

    @Test
    void createsNoteAssociatedWithApplicationAndMapsResponse() {
        when(applications.findById(10L)).thenReturn(Optional.of(application));
        when(notes.save(any(ApplicationNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(10L, new CreateApplicationNoteRequest(" Followed up. "));

        ArgumentCaptor<ApplicationNote> captor = ArgumentCaptor.forClass(ApplicationNote.class);
        verify(notes).save(captor.capture());
        assertThat(captor.getValue().getJobApplication()).isSameAs(application);
        assertThat(response.content()).isEqualTo("Followed up.");
        assertThat(response.applicationId()).isEqualTo(application.getId());
        assertThat(response.id()).isNull();
        assertThat(response.createdAt()).isNull();
    }

    @Test
    void createRejectsMissingApplication() {
        when(applications.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(99L, new CreateApplicationNoteRequest("Note")))
                .isInstanceOf(JobApplicationNotFoundException.class);
        verify(notes, never()).save(any());
    }

    @Test
    void listsNotesUsingRepositoryStableOrderAndMapsFields() throws Exception {
        setField(application, "id", 10L);
        ApplicationNote newer = note(2L, "Newer", Instant.parse("2026-08-05T02:00:00Z"));
        ApplicationNote older = note(1L, "Older", Instant.parse("2026-08-05T01:00:00Z"));
        when(applications.findById(10L)).thenReturn(Optional.of(application));
        when(notes.findByJobApplicationIdOrderByCreatedAtDescIdDesc(10L)).thenReturn(List.of(newer, older));

        var result = service.findAll(10L);

        assertThat(result).extracting(item -> item.content()).containsExactly("Newer", "Older");
        assertThat(result.get(0).id()).isEqualTo(2L);
        assertThat(result.get(0).applicationId()).isEqualTo(10L);
        assertThat(result.get(0).createdAt()).isEqualTo(Instant.parse("2026-08-05T02:00:00Z"));
        verify(notes).findByJobApplicationIdOrderByCreatedAtDescIdDesc(10L);
    }

    @Test
    void existingApplicationWithNoNotesReturnsEmptyList() {
        when(applications.findById(10L)).thenReturn(Optional.of(application));
        when(notes.findByJobApplicationIdOrderByCreatedAtDescIdDesc(10L)).thenReturn(List.of());
        assertThat(service.findAll(10L)).isEmpty();
    }

    @Test
    void listRejectsMissingApplicationWithoutQueryingNotes() {
        when(applications.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findAll(99L)).isInstanceOf(JobApplicationNotFoundException.class);
        verify(notes, never()).findByJobApplicationIdOrderByCreatedAtDescIdDesc(any());
    }

    private ApplicationNote note(long id, String content, Instant createdAt) throws Exception {
        ApplicationNote note = new ApplicationNote(application, content);
        setField(note, "id", id);
        setField(note, "createdAt", createdAt);
        return note;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
