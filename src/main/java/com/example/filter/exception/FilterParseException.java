package com.example.filter.exception;

import java.util.List;

/**
 * Exception thrown when filter parsing fails.
 */
public class FilterParseException extends RuntimeException {
    
    private final List<String> errors;
    
    public FilterParseException(String message) {
        super(message);
        this.errors = List.of(message);
    }
    
    public FilterParseException(String message, List<String> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }
    
    public FilterParseException(String message, Throwable cause) {
        super(message, cause);
        this.errors = List.of(message);
    }
    
    public List<String> getErrors() {
        return errors;
    }
}
