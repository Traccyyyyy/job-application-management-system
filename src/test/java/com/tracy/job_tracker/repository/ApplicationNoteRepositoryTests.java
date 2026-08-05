package com.tracy.job_tracker.repository;

import com.tracy.job_tracker.entity.ApplicationNote;
import com.tracy.job_tracker.entity.ApplicationStatus;
import com.tracy.job_tracker.entity.Company;
import com.tracy.job_tracker.entity.JobApplication;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ApplicationNoteRepositoryTests {
    @Autowired CompanyRepository companies;
    @Autowired JobApplicationRepository applications;
    @Autowired ApplicationNoteRepository notes;
    @Autowired EntityManager entityManager;

    @Test
    void savesAndRetrievesNoteWithRequiredLazyApplicationRelationship() {
        JobApplication application = application("one");
        ApplicationNote saved = notes.saveAndFlush(new ApplicationNote(application, "Followed up"));
        entityManager.clear();

        ApplicationNote found = notes.findById(saved.getId()).orElseThrow();
        PersistenceUnitUtil persistence = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(found.getContent()).isEqualTo("Followed up");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(persistence.isLoaded(found.getJobApplication())).isFalse();
        assertThat(found.getJobApplication().getId()).isEqualTo(application.getId());
    }

    @Test
    void nullApplicationForeignKeyIsRejected() {
        assertThatThrownBy(() -> notes.saveAndFlush(new ApplicationNote(null, "Note")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void contentLongerThanDatabaseColumnIsRejected() {
        JobApplication application = application("length");
        assertThatThrownBy(() -> notes.saveAndFlush(new ApplicationNote(application, "x".repeat(2001))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listsOnlyParentNotesNewestFirstWithStableIdTieBreak() {
        JobApplication first = application("first");
        JobApplication second = application("second");
        ApplicationNote old = notes.saveAndFlush(new ApplicationNote(first, "Old"));
        ApplicationNote tiedLowerId = notes.saveAndFlush(new ApplicationNote(first, "Tie lower"));
        ApplicationNote tiedHigherId = notes.saveAndFlush(new ApplicationNote(first, "Tie higher"));
        notes.saveAndFlush(new ApplicationNote(second, "Other application"));
        entityManager.createNativeQuery("update application_notes set created_at = TIMESTAMP '2026-08-05 01:00:00' where id = :id")
                .setParameter("id", old.getId()).executeUpdate();
        entityManager.createNativeQuery("update application_notes set created_at = TIMESTAMP '2026-08-05 02:00:00' where id in (:low, :high)")
                .setParameter("low", tiedLowerId.getId()).setParameter("high", tiedHigherId.getId()).executeUpdate();
        entityManager.clear();

        assertThat(notes.findByJobApplicationIdOrderByCreatedAtDescIdDesc(first.getId()))
                .extracting(ApplicationNote::getContent)
                .containsExactly("Tie higher", "Tie lower", "Old");
    }

    @Test
    void deletingApplicationCascadesNotesAndLeavesNoOrphans() {
        JobApplication withNotes = application("delete");
        notes.saveAndFlush(new ApplicationNote(withNotes, "One"));
        notes.saveAndFlush(new ApplicationNote(withNotes, "Two"));
        assertThat(notes.countByJobApplicationId(withNotes.getId())).isEqualTo(2);

        entityManager.clear();
        applications.deleteById(withNotes.getId());
        applications.flush();

        assertThat(notes.countByJobApplicationId(withNotes.getId())).isZero();
        assertThat(notes.count()).isZero();
    }

    @Test
    void deletingApplicationWithoutNotesStillSucceeds() {
        JobApplication application = application("empty-delete");
        applications.deleteById(application.getId());
        applications.flush();
        assertThat(applications.findById(application.getId())).isEmpty();
    }

    private JobApplication application(String suffix) {
        Company company = companies.save(new Company("Company " + suffix, null, null));
        return applications.saveAndFlush(new JobApplication("Engineer", "https://jobs.test/" + suffix,
                ApplicationStatus.SAVED, null, null, null, company));
    }
}
