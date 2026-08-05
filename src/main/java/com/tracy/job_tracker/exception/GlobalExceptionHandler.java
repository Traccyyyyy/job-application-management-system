package com.tracy.job_tracker.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({CompanyNotFoundException.class, JobApplicationNotFoundException.class})
    ResponseEntity<ApiErrorResponse> notFound(RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiErrorResponse> conflict(ConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> constraintConflict(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "A unique or relational constraint was violated", request, Map.of());
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiErrorResponse> badRequest(BadRequestException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception,
                                                HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", request, fields);
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiErrorResponse> binding(BindException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "Request binding failed", request, fields);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> typeMismatch(MethodArgumentTypeMismatchException exception,
                                                  HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Invalid value for " + exception.getName(), request, Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> malformedJson(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Malformed or invalid JSON request", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected request failure", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String message,
                                                       HttpServletRequest request,
                                                       Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(Instant.now(), status.value(),
                status.getReasonPhrase(), message, request.getRequestURI(), fieldErrors));
    }
}
