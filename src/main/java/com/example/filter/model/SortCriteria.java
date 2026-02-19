package com.example.filter.model;

import java.util.Objects;

/**
 * Represents a single sort criterion.
 *
 * @param field the field to sort by
 * @param direction the sort direction
 */
public record SortCriteria(
    String field,
    SortDirection direction
) {
    public SortCriteria {
        Objects.requireNonNull(field, "field cannot be null");
        direction = direction != null ? direction : SortDirection.ASC;
    }
    
    public static SortCriteria asc(String field) {
        return new SortCriteria(field, SortDirection.ASC);
    }
    
    public static SortCriteria desc(String field) {
        return new SortCriteria(field, SortDirection.DESC);
    }
    
    @Override
    public String toString() {
        return "%s:%s".formatted(field, direction.getCode());
    }
}
