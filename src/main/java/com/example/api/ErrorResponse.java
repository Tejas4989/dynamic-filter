package com.example.api;

import java.util.List;

/**
 * Standard error response DTO for API exceptions.
 */
public record ErrorResponse(
    String timestamp,
    int status,
    String error,
    String message,
    String path,
    List<String> details
) {}
