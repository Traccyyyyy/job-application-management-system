package com.tracy.job_tracker.integration;

import com.tracy.job_tracker.entity.ApplicationNote;
import com.tracy.job_tracker.entity.ApplicationStatus;
import com.tracy.job_tracker.entity.Company;
import com.tracy.job_tracker.entity.JobApplication;
import com.tracy.job_tracker.repository.ApplicationNoteRepository;
import com.tracy.job_tracker.repository.CompanyRepository;
import com.tracy.job_tracker.repository.JobApplicationRepository;
import com.tracy.job_tracker.repository.JobApplicationSpecifications;
import com.tracy.job_tracker.repository.StatusCountProjection;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@Testcontainers
@ActiveProfiles("postgres")
class PostgreSqlPersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/postgresql");
    }

    @Autowired CompanyRepository companies;
    @Autowired JobApplicationRepository applications;
    @Autowired ApplicationNoteRepository notes;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    void migrationsCreateValidatedSchemaAndCurrentPersistenceFlowWorks() {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class)).isEqualTo(1);

        Company company = companies.saveAndFlush(new Company("PostgreSQL Co", null, "Technology"));
        JobApplication application = applications.saveAndFlush(new JobApplication("Database Engineer",
                "https://jobs.test/postgresql", ApplicationStatus.APPLIED, "Referral", "Sydney",
                java.time.LocalDate.of(2026, 8, 6), company));
        JobApplication tiedLowerApplication = applications.saveAndFlush(new JobApplication("Platform Engineer",
                "https://jobs.test/postgresql-platform-1", ApplicationStatus.SAVED, null, null, null, company));
        JobApplication tiedHigherApplication = applications.saveAndFlush(new JobApplication("Platform Engineer",
                "https://jobs.test/postgresql-platform-2", ApplicationStatus.APPLIED, null, null,
                java.time.LocalDate.of(2026, 8, 6), company));

        ApplicationNote oldestNote = notes.saveAndFlush(new ApplicationNote(application, "Oldest PostgreSQL note"));
        ApplicationNote tiedLowerNote = notes.saveAndFlush(new ApplicationNote(application, "Tied lower note"));
        ApplicationNote tiedHigherNote = notes.saveAndFlush(new ApplicationNote(application, "Tied higher note"));
        jdbc.update("update application_notes set created_at = TIMESTAMPTZ '2026-08-06 01:00:00+00' where id = ?",
                oldestNote.getId());
        jdbc.update("update application_notes set created_at = TIMESTAMPTZ '2026-08-06 02:00:00+00' where id in (?, ?)",
                tiedLowerNote.getId(), tiedHigherNote.getId());
        entityManager.clear();

        assertThat(applications.findById(application.getId())).isPresent();
        assertThat(applications.findAll(JobApplicationSpecifications.hasStatus(ApplicationStatus.APPLIED)))
                .extracting(JobApplication::getId).contains(application.getId());
        assertThat(applications.findAll(JobApplicationSpecifications.keywordContains("database")))
                .extracting(JobApplication::getId).contains(application.getId());
        var page = applications.findAll(JobApplicationSpecifications.companyContains("postgresql"),
                PageRequest.of(0, 2, Sort.by("jobTitle").ascending().and(Sort.by("id").ascending())));
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(JobApplication::getId)
                .containsExactly(application.getId(), tiedLowerApplication.getId());

        assertThat(applications.countByStatusGrouped())
                .extracting(StatusCountProjection::getStatus, StatusCountProjection::getCount)
                .containsExactlyInAnyOrder(tuple(ApplicationStatus.APPLIED, 2L), tuple(ApplicationStatus.SAVED, 1L));

        assertThat(notes.findByJobApplicationIdOrderByCreatedAtDescIdDesc(application.getId()))
                .extracting(ApplicationNote::getContent)
                .containsExactly("Tied higher note", "Tied lower note", "Oldest PostgreSQL note");

        assertThatThrownBy(() -> applications.saveAndFlush(new JobApplication("Duplicate URL",
                "https://jobs.test/postgresql", ApplicationStatus.SAVED, null, null, null, company)))
                .isInstanceOf(DataIntegrityViolationException.class);
        entityManager.clear();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> jdbc.update("""
                    insert into job_applications
                        (job_title, job_url, status, company_id, created_at, updated_at)
                    values ('Invalid parent', 'https://jobs.test/invalid-parent', 'SAVED', -1, now(), now())
                    """))).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                jdbc.update("delete from job_applications where id = ?", application.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(applications.findById(application.getId())).isPresent();
        assertThat(notes.countByJobApplicationId(application.getId())).isEqualTo(3);

        applications.deleteById(application.getId());
        applications.flush();
        assertThat(notes.countByJobApplicationId(application.getId())).isZero();
    }
}
