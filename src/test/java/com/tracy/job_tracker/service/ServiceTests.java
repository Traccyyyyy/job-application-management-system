package com.tracy.job_tracker.service;

import com.tracy.job_tracker.dto.request.CreateCompanyRequest;
import com.tracy.job_tracker.dto.request.CreateJobApplicationRequest;
import com.tracy.job_tracker.dto.request.UpdateJobApplicationRequest;
import com.tracy.job_tracker.entity.ApplicationStatus;
import com.tracy.job_tracker.entity.Company;
import com.tracy.job_tracker.entity.JobApplication;
import com.tracy.job_tracker.exception.CompanyNotFoundException;
import com.tracy.job_tracker.exception.ConflictException;
import com.tracy.job_tracker.exception.JobApplicationNotFoundException;
import com.tracy.job_tracker.mapper.CompanyMapper;
import com.tracy.job_tracker.mapper.JobApplicationMapper;
import com.tracy.job_tracker.repository.CompanyRepository;
import com.tracy.job_tracker.repository.JobApplicationRepository;
import com.tracy.job_tracker.repository.StatusCountProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ServiceTests {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 5);

    @Mock CompanyRepository companyRepository;
    @Mock JobApplicationRepository applicationRepository;

    private CompanyServiceImpl companyService;
    private JobApplicationServiceImpl applicationService;
    private Company company;

    @BeforeEach
    void setUp() {
        companyService = new CompanyServiceImpl(companyRepository, new CompanyMapper());
        applicationService = new JobApplicationServiceImpl(applicationRepository, companyRepository,
                new JobApplicationMapper(), Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC));
        company = new Company("Acme", "https://acme.test", "Technology");
        lenient().when(applicationRepository.save(any(JobApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(applicationRepository.saveAndFlush(any(JobApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsCompany() {
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(companyService.create(new CreateCompanyRequest(" Acme ", "", " Tech ")).name())
                .isEqualTo("Acme");
    }

    @Test
    void rejectsDuplicateCompanyIgnoringCase() {
        when(companyRepository.existsByNameIgnoreCase("acme")).thenReturn(true);
        assertThatThrownBy(() -> companyService.create(new CreateCompanyRequest(" acme ", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createsApplication() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        var response = applicationService.create(createRequest(ApplicationStatus.SAVED, null));
        assertThat(response.jobTitle()).isEqualTo("Engineer");
        assertThat(response.company().companyName()).isEqualTo("Acme");
    }

    @Test
    void defaultsNewApplicationToSaved() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        assertThat(applicationService.create(createRequest(null, null)).status()).isEqualTo(ApplicationStatus.SAVED);
    }

    @Test
    void rejectsMissingCompanyOnCreate() {
        when(companyRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> applicationService.create(createRequest(null, null)))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void assignsAppliedDateWhenCreatedAsApplied() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        assertThat(applicationService.create(createRequest(ApplicationStatus.APPLIED, null)).appliedDate())
                .isEqualTo(TODAY);
    }

    @Test
    void rejectsDuplicateJobUrl() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(applicationRepository.existsByJobUrl("https://jobs.test/1")).thenReturn(true);
        assertThatThrownBy(() -> applicationService.create(createRequest(null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsMissingApplication() {
        when(applicationRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> applicationService.findById(99L))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void permitsEveryAllowedTransition(ApplicationStatus current, ApplicationStatus requested) {
        JobApplication application = application(current, current == ApplicationStatus.SAVED ? null : TODAY);
        when(applicationRepository.findById(2L)).thenReturn(Optional.of(application));
        assertThat(applicationService.updateStatus(2L, requested).status()).isEqualTo(requested);
    }

    @ParameterizedTest
    @MethodSource("disallowedTransitions")
    void rejectsRepresentativeInvalidTransitions(ApplicationStatus current, ApplicationStatus requested) {
        when(applicationRepository.findById(2L)).thenReturn(Optional.of(application(current, TODAY)));
        assertThatThrownBy(() -> applicationService.updateStatus(2L, requested))
                .isInstanceOf(ConflictException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationStatus.class, names = {"OFFER", "REJECTED", "WITHDRAWN"})
    void everyTerminalStatusRejectsFurtherTransition(ApplicationStatus terminal) {
        when(applicationRepository.findById(2L)).thenReturn(Optional.of(application(terminal, TODAY)));
        assertThatThrownBy(() -> applicationService.updateStatus(2L, ApplicationStatus.SAVED))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void sameStatusUpdateIsIdempotent() {
        when(applicationRepository.findById(2L)).thenReturn(Optional.of(application(ApplicationStatus.SCREENING, TODAY)));
        assertThat(applicationService.updateStatus(2L, ApplicationStatus.SCREENING).status())
                .isEqualTo(ApplicationStatus.SCREENING);
        verify(applicationRepository, never()).saveAndFlush(any(JobApplication.class));
    }

    @Test
    void savedToAppliedAssignsAppliedDate() {
        when(applicationRepository.findById(2L)).thenReturn(Optional.of(application(ApplicationStatus.SAVED, null)));
        assertThat(applicationService.updateStatus(2L, ApplicationStatus.APPLIED).appliedDate()).isEqualTo(TODAY);
    }

    @Test
    void putDoesNotChangeStatus() {
        JobApplication application = application(ApplicationStatus.SCREENING, TODAY);
        when(applicationRepository.findById(2L)).thenReturn(Optional.of(application));
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        var request = new UpdateJobApplicationRequest("Updated", "https://jobs.test/2", "Direct", "Sydney",
                TODAY, 1L);
        assertThat(applicationService.update(2L, request).status()).isEqualTo(ApplicationStatus.SCREENING);
    }

    @Test
    void deleteMissingApplicationFails() {
        when(applicationRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> applicationService.delete(99L)).isInstanceOf(JobApplicationNotFoundException.class);
        verify(applicationRepository, never()).delete(any(JobApplication.class));
    }

    @Test
    void optionalFiltersCanBeCombinedWithPaging() {
        when(applicationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());
        assertThat(applicationService.findAll(null, "acme", null, 0, 20, "createdAt", "desc").content())
                .isEmpty();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(applicationRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void summaryIncludesEveryStatusAndZeroCounts() {
        when(applicationRepository.countByStatusGrouped()).thenReturn(List.of(count(ApplicationStatus.SAVED, 2L)));
        var summary = applicationService.summary();
        assertThat(summary).hasSize(ApplicationStatus.values().length);
        assertThat(summary).anySatisfy(item -> {
            assertThat(item.status()).isEqualTo(ApplicationStatus.SAVED);
            assertThat(item.count()).isEqualTo(2L);
        });
        assertThat(summary).filteredOn(item -> item.status() != ApplicationStatus.SAVED)
                .allSatisfy(item -> assertThat(item.count()).isZero());
    }

    private static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED),
                Arguments.of(ApplicationStatus.SAVED, ApplicationStatus.WITHDRAWN),
                Arguments.of(ApplicationStatus.APPLIED, ApplicationStatus.SCREENING),
                Arguments.of(ApplicationStatus.APPLIED, ApplicationStatus.REJECTED),
                Arguments.of(ApplicationStatus.APPLIED, ApplicationStatus.WITHDRAWN),
                Arguments.of(ApplicationStatus.SCREENING, ApplicationStatus.INTERVIEW),
                Arguments.of(ApplicationStatus.SCREENING, ApplicationStatus.REJECTED),
                Arguments.of(ApplicationStatus.SCREENING, ApplicationStatus.WITHDRAWN),
                Arguments.of(ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER),
                Arguments.of(ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED),
                Arguments.of(ApplicationStatus.INTERVIEW, ApplicationStatus.WITHDRAWN));
    }

    private static Stream<Arguments> disallowedTransitions() {
        return Stream.of(
                Arguments.of(ApplicationStatus.SAVED, ApplicationStatus.INTERVIEW),
                Arguments.of(ApplicationStatus.APPLIED, ApplicationStatus.OFFER),
                Arguments.of(ApplicationStatus.SCREENING, ApplicationStatus.APPLIED));
    }

    private static StatusCountProjection count(ApplicationStatus status, long count) {
        return new StatusCountProjection() {
            @Override public ApplicationStatus getStatus() { return status; }
            @Override public long getCount() { return count; }
        };
    }

    private CreateJobApplicationRequest createRequest(ApplicationStatus status, LocalDate appliedDate) {
        return new CreateJobApplicationRequest(" Engineer ", "https://jobs.test/1", status, "LinkedIn",
                "Sydney", appliedDate, 1L);
    }

    private JobApplication application(ApplicationStatus status, LocalDate appliedDate) {
        return new JobApplication("Engineer", "https://jobs.test/existing", status, "LinkedIn", "Sydney",
                appliedDate, company);
    }
}
