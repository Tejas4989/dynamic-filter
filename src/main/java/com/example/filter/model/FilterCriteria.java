package com.example.filter.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a single filter criterion.
 *
 * @param field the field name to filter on
 * @param operator the filter operator
 * @param value the filter value (single value for most operators)
 * @param values list of values for IN/NOT IN operators
 */
public record FilterCriteria(
    String field,
    FilterOperator operator,
    String value,
    List<String> values
) {
    public FilterCriteria {
        Objects.requireNonNull(field, "field cannot be null");
        Objects.requireNonNull(operator, "operator cannot be null");
        values = values != null ? List.copyOf(values) : List.of();
    }
    
    /**
     * Creates a single-value filter criterion.
     */
    public static FilterCriteria of(String field, FilterOperator operator, String value) {
        return new FilterCriteria(field, operator, value, List.of());
    }
    
    /**
     * Creates a multi-value filter criterion (for IN/NOT IN).
     */
    public static FilterCriteria ofMulti(String field, FilterOperator operator, List<String> values) {
        return new FilterCriteria(field, operator, null, values);
    }
    
    /**
     * Creates a null-check filter criterion.
     */
    public static FilterCriteria ofNullCheck(String field, FilterOperator operator) {
        return new FilterCriteria(field, operator, null, List.of());
    }
    
    /**
     * @return true if this is a multi-value filter (IN/NOT IN)
     */
    public boolean isMultiValue() {
        return operator.isCollectionOperator();
    }
    
    /**
     * @return true if this is a null-check filter
     */
    public boolean isNullCheck() {
        return !operator.requiresValue();
    }
    
    @Override
    public String toString() {
        if (isNullCheck()) {
            return "%s:%s".formatted(field, operator.getCode());
        } else if (isMultiValue()) {
            return "%s:%s:(%s)".formatted(field, operator.getCode(), String.join(",", values));
        }
        return "%s:%s:%s".formatted(field, operator.getCode(), value);
    }
}
