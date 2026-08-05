package com.tracy.job_tracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "job_applications", indexes = {
        @Index(name = "idx_job_application_status", columnList = "status"),
        @Index(name = "idx_job_application_applied_date", columnList = "applied_date"),
        @Index(name = "idx_job_application_company", columnList = "company_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_job_application_url", columnNames = "job_url")
})
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_title", nullable = false, length = 150)
    private String jobTitle;

    @Column(name = "job_url", nullable = false, length = 1000)
    private String jobUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(length = 100)
    private String source;

    @Column(length = 150)
    private String location;

    @Column(name = "applied_date")
    private LocalDate appliedDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "jobApplication", fetch = FetchType.LAZY,
            cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<ApplicationNote> notes = new ArrayList<>();

    protected JobApplication() {
    }

    public JobApplication(String jobTitle, String jobUrl, ApplicationStatus status, String source,
                          String location, LocalDate appliedDate, Company company) {
        this.jobTitle = jobTitle;
        this.jobUrl = jobUrl;
        this.status = status;
        this.source = source;
        this.location = location;
        this.appliedDate = appliedDate;
        this.company = company;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getJobTitle() { return jobTitle; }
    public String getJobUrl() { return jobUrl; }
    public ApplicationStatus getStatus() { return status; }
    public String getSource() { return source; }
    public String getLocation() { return location; }
    public LocalDate getAppliedDate() { return appliedDate; }
    public Company getCompany() { return company; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ApplicationNote> getNotes() { return notes; }

    public void updateDetails(String jobTitle, String jobUrl, String source, String location,
                              LocalDate appliedDate, Company company) {
        this.jobTitle = jobTitle;
        this.jobUrl = jobUrl;
        this.source = source;
        this.location = location;
        this.appliedDate = appliedDate;
        this.company = company;
    }

    public void changeStatus(ApplicationStatus status) { this.status = status; }
    public void setAppliedDate(LocalDate appliedDate) { this.appliedDate = appliedDate; }
}
