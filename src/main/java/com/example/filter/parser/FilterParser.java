package com.example.filter.parser;

import com.example.filter.exception.FilterParseException;
import com.example.filter.model.FilterCriteria;
import com.example.filter.model.FilterOperator;
import com.example.filter.model.SortCriteria;
import com.example.filter.model.SortDirection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses filter and sort query strings into structured criteria.
 * 
 * <p>Filter syntax: {@code field:operator:value} or {@code field:operator:(v1,v2,v3)}</p>
 * <p>Sort syntax: {@code field:direction} (direction defaults to asc)</p>
 */
public final class FilterParser {
    
    private static final Pattern IN_VALUES_PATTERN = Pattern.compile("^\\((.*)\\)$");
    private static final Pattern FILTER_SPLIT_PATTERN = Pattern.compile(",(?![^()]*\\))");
    
    private static final FilterParser INSTANCE = new FilterParser();
    
    private FilterParser() {}
    
    public static FilterParser getInstance() {
        return INSTANCE;
    }
    
    /**
     * Parses a filter string into criteria list.
     */
    public List<FilterCriteria> parseFilters(String filterString) {
        if (filterString == null || filterString.isBlank()) {
            return List.of();
        }
        
        List<FilterCriteria> criteria = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        for (String part : FILTER_SPLIT_PATTERN.split(filterString)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            
            try {
                criteria.add(parseSingleFilter(trimmed));
            } catch (IllegalArgumentException e) {
                errors.add("Invalid filter '%s': %s".formatted(trimmed, e.getMessage()));
            }
        }
        
        if (!errors.isEmpty()) {
            throw new FilterParseException("Filter parsing failed", errors);
        }
        return List.copyOf(criteria);
    }
    
    /**
     * Parses a sort string into criteria list.
     */
    public List<SortCriteria> parseSorts(String sortString) {
        if (sortString == null || sortString.isBlank()) {
            return List.of();
        }
        
        List<SortCriteria> criteria = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        for (String part : sortString.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            
            try {
                criteria.add(parseSingleSort(trimmed));
            } catch (IllegalArgumentException e) {
                errors.add("Invalid sort '%s': %s".formatted(trimmed, e.getMessage()));
            }
        }
        
        if (!errors.isEmpty()) {
            throw new FilterParseException("Sort parsing failed", errors);
        }
        return List.copyOf(criteria);
    }
    
    private FilterCriteria parseSingleFilter(String expression) {
        String[] parts = expression.split(":", 3);
        
        if (parts.length < 2) {
            throw new IllegalArgumentException("Expected format 'field:operator' or 'field:operator:value'");
        }
        
        String field = parts[0].trim();
        String opCode = parts[1].trim().toLowerCase();
        
        if (field.isEmpty()) {
            throw new IllegalArgumentException("Field name cannot be empty");
        }
        
        FilterOperator operator = FilterOperator.fromCode(opCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown operator: " + opCode));
        
        // Null check operators (no value)
        if (!operator.requiresValue()) {
            return FilterCriteria.ofNullCheck(field, operator);
        }
        
        // Value required
        if (parts.length < 3 || parts[2].trim().isEmpty()) {
            throw new IllegalArgumentException("Operator '%s' requires a value".formatted(opCode));
        }
        
        String value = parts[2].trim();
        
        // IN/NOT IN operators (multi-value)
        if (operator.isCollectionOperator()) {
            return FilterCriteria.ofMulti(field, operator, parseInValues(value));
        }
        
        // Single value
        return FilterCriteria.of(field, operator, value);
    }
    
    private List<String> parseInValues(String value) {
        String inner = value;
        Matcher matcher = IN_VALUES_PATTERN.matcher(value);
        if (matcher.matches()) {
            inner = matcher.group(1);
        }
        
        if (inner.isEmpty()) {
            throw new IllegalArgumentException("IN clause cannot be empty");
        }
        
        return Arrays.stream(inner.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
    
    private SortCriteria parseSingleSort(String expression) {
        String[] parts = expression.split(":", 2);
        String field = parts[0].trim();
        
        if (field.isEmpty()) {
            throw new IllegalArgumentException("Field name cannot be empty");
        }
        
        SortDirection direction = SortDirection.ASC;
        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
            direction = SortDirection.fromCode(parts[1].trim())
                .orElseThrow(() -> new IllegalArgumentException("Unknown direction: " + parts[1]));
        }
        
        return new SortCriteria(field, direction);
    }
}
