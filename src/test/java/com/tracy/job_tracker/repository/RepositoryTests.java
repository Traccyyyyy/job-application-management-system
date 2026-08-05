package com.tracy.job_tracker.repository;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RepositoryTests {
    @Autowired CompanyRepository companies;
    @Autowired JobApplicationRepository applications;
    @Autowired EntityManager entityManager;

    @Test
    void companySaveAndFind() {
        Company saved = companies.save(new Company("Acme", "https://acme.test", "Tech"));
        assertThat(companies.findById(saved.getId())).isPresent();
    }

    @Test
    void companyLookupIsCaseInsensitive() {
        companies.save(new Company("Acme", null, null));
        assertThat(companies.findByNameIgnoreCase("aCmE")).isPresent();
    }

    @Test
    void companyNameConstraintIsCaseInsensitiveAtDatabaseLevel() {
        companies.saveAndFlush(new Company("Acme", null, null));
        assertThatThrownBy(() -> companies.saveAndFlush(new Company("acme", null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void applicationSaveAndFindIncludesCompanyRelationship() {
        Company company = company("Acme");
        JobApplication saved = applications.save(application(company, "Engineer", "https://jobs.test/1",
                ApplicationStatus.SAVED, "LinkedIn", "Sydney"));
        assertThat(applications.findById(saved.getId()).orElseThrow().getCompany().getName()).isEqualTo("Acme");
    }

    @Test
    void jobUrlConstraintIsUnique() {
        Company company = company("Acme");
        applications.saveAndFlush(application(company, "One", "https://jobs.test/same",
                ApplicationStatus.SAVED, null, null));
        assertThatThrownBy(() -> applications.saveAndFlush(application(company, "Two", "https://jobs.test/same",
                ApplicationStatus.SAVED, null, null))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void filtersByStatus() {
        Company company = company("Acme");
        applications.save(application(company, "One", "https://jobs.test/1", ApplicationStatus.SAVED, null, null));
        applications.save(application(company, "Two", "https://jobs.test/2", ApplicationStatus.APPLIED, null, null));
        var result = applications.findAll(JobApplicationSpecifications.hasStatus(ApplicationStatus.APPLIED),
                PageRequest.of(0, 20));
        assertThat(result.getContent()).extracting(JobApplication::getJobTitle).containsExactly("Two");
    }

    @Test
    void searchesByCompanyName() {
        applications.save(application(company("Acme Labs"), "One", "https://jobs.test/1",
                ApplicationStatus.SAVED, null, null));
        var result = applications.findAll(JobApplicationSpecifications.companyContains("ME la"),
                PageRequest.of(0, 20));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void keywordSearchesTitleCompanySourceAndLocation() {
        applications.save(application(company("Acme"), "Platform Engineer", "https://jobs.test/1",
                ApplicationStatus.SAVED, "Referral", "Sydney"));
        assertThat(applications.findAll(JobApplicationSpecifications.keywordContains("platform"))).isNotEmpty();
        assertThat(applications.findAll(JobApplicationSpecifications.keywordContains("referral"))).isNotEmpty();
        assertThat(applications.findAll(JobApplicationSpecifications.keywordContains("sydney"))).isNotEmpty();
    }

    @Test
    void wildcardCharactersAreTreatedLiterally() {
        applications.save(application(company("Under_score"), "100% Remote", "https://jobs.test/1",
                ApplicationStatus.SAVED, null, null));
        applications.save(application(company("UnderXscore"), "1000 Remote", "https://jobs.test/2",
                ApplicationStatus.SAVED, null, null));

        assertThat(applications.findAll(JobApplicationSpecifications.keywordContains("%")))
                .extracting(JobApplication::getJobTitle).containsExactly("100% Remote");
        assertThat(applications.findAll(JobApplicationSpecifications.companyContains("_")))
                .extracting(JobApplication::getCompany).extracting(Company::getName)
                .containsExactly("Under_score");
    }

    @Test
    void combinesFilters() {
        Company acme = company("Acme");
        applications.save(application(acme, "Engineer", "https://jobs.test/1", ApplicationStatus.APPLIED,
                "Referral", "Sydney"));
        applications.save(application(acme, "Designer", "https://jobs.test/2", ApplicationStatus.SAVED,
                "Direct", "Melbourne"));
        Specification<JobApplication> spec = Specification.allOf(
                JobApplicationSpecifications.hasStatus(ApplicationStatus.APPLIED),
                JobApplicationSpecifications.companyContains("acm"),
                JobApplicationSpecifications.keywordContains("engineer"));
        assertThat(applications.findAll(spec)).extracting(JobApplication::getJobTitle).containsExactly("Engineer");
    }

    @Test
    void paginatesAndSortsInDatabase() {
        Company company = company("Acme");
        applications.save(application(company, "Zulu", "https://jobs.test/1", ApplicationStatus.SAVED, null, null));
        applications.save(application(company, "Alpha", "https://jobs.test/2", ApplicationStatus.SAVED, null, null));
        var page = applications.findAll(Specification.unrestricted(),
                PageRequest.of(0, 1, Sort.by("jobTitle")));
        assertThat(page.getSize()).isEqualTo(1);
        assertThat(page.getContent()).extracting(JobApplication::getJobTitle).containsExactly("Alpha");
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getNumber()).isZero();
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void sortsByCompanyNameInBothDirections() {
        applications.save(application(company("Beta"), "Two", "https://jobs.test/2",
                ApplicationStatus.SAVED, null, null));
        applications.save(application(company("Alpha"), "One", "https://jobs.test/1",
                ApplicationStatus.SAVED, null, null));

        var ascending = applications.findAll(Specification.unrestricted(),
                PageRequest.of(0, 20, Sort.by("company.name").ascending()));
        var descending = applications.findAll(Specification.unrestricted(),
                PageRequest.of(0, 20, Sort.by("company.name").descending()));

        assertThat(ascending).extracting(JobApplication::getCompany).extracting(Company::getName)
                .containsExactly("Alpha", "Beta");
        assertThat(descending).extracting(JobApplication::getCompany).extracting(Company::getName)
                .containsExactly("Beta", "Alpha");
    }

    @Test
    void pagedListingFetchesCompanyRelationship() {
        applications.saveAndFlush(application(company("Acme"), "One", "https://jobs.test/1",
                ApplicationStatus.SAVED, null, null));
        applications.saveAndFlush(application(company("Beta"), "Two", "https://jobs.test/2",
                ApplicationStatus.SAVED, null, null));
        entityManager.clear();

        var page = applications.findAll(Specification.unrestricted(), PageRequest.of(0, 20));
        PersistenceUnitUtil persistence = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allSatisfy(application ->
                assertThat(persistence.isLoaded(application.getCompany())).isTrue());
    }

    @Test
    void aggregatesStatusSummary() {
        Company company = company("Acme");
        applications.save(application(company, "One", "https://jobs.test/1", ApplicationStatus.SAVED, null, null));
        applications.save(application(company, "Two", "https://jobs.test/2", ApplicationStatus.SAVED, null, null));
        applications.save(application(company, "Three", "https://jobs.test/3", ApplicationStatus.APPLIED, null, null));
        Map<ApplicationStatus, Long> counts = applications.countByStatusGrouped().stream()
                .collect(Collectors.toMap(StatusCountProjection::getStatus, StatusCountProjection::getCount));
        assertThat(counts).containsEntry(ApplicationStatus.SAVED, 2L)
                .containsEntry(ApplicationStatus.APPLIED, 1L);
    }

    private Company company(String name) { return companies.save(new Company(name, null, null)); }

    private JobApplication application(Company company, String title, String url, ApplicationStatus status,
                                       String source, String location) {
        return new JobApplication(title, url, status, source, location, null, company);
    }
}
