package com.example.filter.exception;

import java.util.List;

/**
 * Exception thrown when filter validation fails.
 */
public class FilterValidationException extends RuntimeException {
    
    private final List<String> errors;
    
    public FilterValidationException(String message) {
        super(message);
        this.errors = List.of(message);
    }
    
    public FilterValidationException(String message, List<String> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }
    
    public List<String> getErrors() {
        return errors;
    }
}
