package com.tracy.job_tracker.exception;

public class CompanyNotFoundException extends RuntimeException {
    public CompanyNotFoundException(Long id) {
        super("Company " + id + " was not found");
    }
}
