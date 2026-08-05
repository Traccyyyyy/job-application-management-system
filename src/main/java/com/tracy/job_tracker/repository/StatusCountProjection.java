package com.tracy.job_tracker.repository;

import com.tracy.job_tracker.entity.ApplicationStatus;

public interface StatusCountProjection {
    ApplicationStatus getStatus();
    long getCount();
}
