package com.tracy.job_tracker.exception;

public class JobApplicationNotFoundException extends RuntimeException {
    public JobApplicationNotFoundException(Long id) {
        super("Job application " + id + " was not found");
    }
}
