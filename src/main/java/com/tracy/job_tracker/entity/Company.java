package com.tracy.job_tracker.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "companies", uniqueConstraints = {
        @UniqueConstraint(name = "uk_company_normalized_name", columnNames = "normalized_name")
})
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 150)
    private String normalizedName;

    @Column(length = 1000)
    private String website;

    @Column(length = 150)
    private String industry;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
    private List<JobApplication> jobApplications = new ArrayList<>();

    protected Company() {
    }

    public Company(String name, String website, String industry) {
        this.name = name;
        this.website = website;
        this.industry = industry;
        this.normalizedName = normalize(name);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        normalizedName = normalize(name);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        normalizedName = normalize(name);
        updatedAt = Instant.now();
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getWebsite() { return website; }
    public String getIndustry() { return industry; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<JobApplication> getJobApplications() { return jobApplications; }
}
