package com.example.api;

import com.example.filter.exception.FilterParseException;
import com.example.filter.exception.FilterValidationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.List;

/**
 * Global exception handler for filter-related errors.
 * Centralizes error response format across all controllers.
 */
@RestControllerAdvice
public class FilterExceptionHandler {

    private static String extractPath(WebRequest request) {
        String desc = request.getDescription(false);
        if (desc.startsWith("uri=")) {
            return desc.substring(4).split(";")[0].trim();
        }
        return desc;
    }

    @ExceptionHandler(FilterParseException.class)
    public ResponseEntity<ErrorResponse> handleFilterParseException(
            FilterParseException ex, WebRequest request) {
        return ResponseEntity.badRequest().body(buildError(ex.getMessage(), ex.getErrors(), request));
    }

    @ExceptionHandler(FilterValidationException.class)
    public ResponseEntity<ErrorResponse> handleFilterValidationException(
            FilterValidationException ex, WebRequest request) {
        return ResponseEntity.badRequest().body(buildError(ex.getMessage(), ex.getErrors(), request));
    }

    private static ErrorResponse buildError(String message, List<String> details, WebRequest request) {
        return new ErrorResponse(
            Instant.now().toString(),
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            message,
            extractPath(request),
            details != null ? details : List.of()
        );
    }
}
