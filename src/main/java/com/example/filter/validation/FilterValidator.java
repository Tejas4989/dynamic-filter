package com.example.filter.validation;

import com.example.filter.exception.FilterValidationException;
import com.example.filter.metadata.EntityMetadata;
import com.example.filter.metadata.FieldMetadata;
import com.example.filter.model.FilterCriteria;
import com.example.filter.model.FilterOperator;
import com.example.filter.model.SortCriteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates filter and sort criteria against entity metadata.
 */
public final class FilterValidator {
    
    private static final Set<FilterOperator> STRING_ONLY_OPERATORS = Set.of(
        FilterOperator.STARTS_WITH,
        FilterOperator.ENDS_WITH,
        FilterOperator.CONTAINS
    );
    
    private static final FilterValidator INSTANCE = new FilterValidator();
    
    private FilterValidator() {}
    
    public static FilterValidator getInstance() {
        return INSTANCE;
    }
    
    /**
     * Validates filter criteria against entity metadata.
     */
    public void validateFilters(List<FilterCriteria> filters, EntityMetadata metadata) {
        if (filters == null || filters.isEmpty()) return;
        
        List<String> errors = new ArrayList<>();
        
        for (FilterCriteria filter : filters) {
            validateFilter(filter, metadata, errors);
        }
        
        if (!errors.isEmpty()) {
            throw new FilterValidationException("Filter validation failed", errors);
        }
    }
    
    /**
     * Validates sort criteria against entity metadata.
     */
    public void validateSorts(List<SortCriteria> sorts, EntityMetadata metadata) {
        if (sorts == null || sorts.isEmpty()) return;
        
        List<String> errors = new ArrayList<>();
        
        for (SortCriteria sort : sorts) {
            var field = metadata.getField(sort.field());
            if (field.isEmpty()) {
                errors.add("Unknown sort field '%s'. Valid: %s"
                    .formatted(sort.field(), metadata.getSortableFields()));
            } else if (!field.get().sortable()) {
                errors.add("Field '%s' is not sortable".formatted(sort.field()));
            }
        }
        
        if (!errors.isEmpty()) {
            throw new FilterValidationException("Sort validation failed", errors);
        }
    }
    
    private void validateFilter(FilterCriteria filter, EntityMetadata metadata, List<String> errors) {
        var fieldMeta = metadata.getField(filter.field());
        
        if (fieldMeta.isEmpty()) {
            errors.add("Unknown filter field '%s'. Valid: %s"
                .formatted(filter.field(), metadata.getFilterableFields()));
            return;
        }
        
        FieldMetadata fm = fieldMeta.get();
        
        if (!fm.filterable()) {
            errors.add("Field '%s' is not filterable".formatted(filter.field()));
            return;
        }
        
        // String operators only work with String fields
        if (STRING_ONLY_OPERATORS.contains(filter.operator()) && !fm.isString()) {
            errors.add("Operator '%s' only works with String fields, but '%s' is %s"
                .formatted(filter.operator().getCode(), filter.field(), fm.fieldType().getSimpleName()));
        }
        
        // Validate numeric values
        if (fm.isNumeric() && filter.operator().requiresValue()) {
            if (filter.isMultiValue()) {
                for (String v : filter.values()) {
                    validateNumeric(filter.field(), v, errors);
                }
            } else if (filter.value() != null) {
                validateNumeric(filter.field(), filter.value(), errors);
            }
        }
    }
    
    private void validateNumeric(String field, String value, List<String> errors) {
        try {
            Double.parseDouble(value);
        } catch (NumberFormatException e) {
            errors.add("Field '%s' expects numeric value, got '%s'".formatted(field, value));
        }
    }
}
