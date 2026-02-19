package com.example.filter.model;

import java.util.List;
import java.util.Map;

/**
 * Unified request object for filtering, sorting, and pagination.
 * 
 * <p>Designed to integrate with existing pagination patterns. Can be extended
 * or composed with your existing PaginationRequest.</p>
 *
 * @param limit maximum number of records to return
 * @param offset number of records to skip
 * @param filterString raw filter string (e.g., "firstName:sw:Jo,roleIds:in:(1,2,3)")
 * @param sortString raw sort string (e.g., "lastName:asc,firstName:desc")
 * @param filters parsed filter criteria (populated after parsing)
 * @param sorts parsed sort criteria (populated after parsing)
 * @param sourceClass the source/DTO class for field mapping
 * @param entityClass the target entity class
 * @param fieldMapping maps source field names to entity field names
 */
public record FilterRequest(
    Integer limit,
    Integer offset,
    String filterString,
    String sortString,
    List<FilterCriteria> filters,
    List<SortCriteria> sorts,
    Class<?> sourceClass,
    Class<?> entityClass,
    Map<String, String> fieldMapping
) {
    public static final int DEFAULT_LIMIT = 20;
    public static final int DEFAULT_OFFSET = 0;
    public static final int MAX_LIMIT = 100;
    
    public FilterRequest {
        limit = limit != null && limit > 0 ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        offset = offset != null && offset >= 0 ? offset : DEFAULT_OFFSET;
        filters = filters != null ? List.copyOf(filters) : List.of();
        sorts = sorts != null ? List.copyOf(sorts) : List.of();
        fieldMapping = fieldMapping != null ? Map.copyOf(fieldMapping) : Map.of();
    }
    
    /**
     * Creates a request from raw strings (filters/sorts will be parsed later).
     */
    public static FilterRequest of(Integer limit, Integer offset, String filterString, String sortString) {
        return new FilterRequest(limit, offset, filterString, sortString, 
                                 List.of(), List.of(), null, null, Map.of());
    }
    
    /**
     * Creates a request with entity class for validation.
     */
    public static FilterRequest of(Integer limit, Integer offset, String filterString, 
                                   String sortString, Class<?> entityClass) {
        return new FilterRequest(limit, offset, filterString, sortString,
                                 List.of(), List.of(), null, entityClass, Map.of());
    }
    
    /**
     * Creates a request with field mapping support.
     */
    public static FilterRequest of(Integer limit, Integer offset, String filterString,
                                   String sortString, Class<?> sourceClass, Class<?> entityClass,
                                   Map<String, String> fieldMapping) {
        return new FilterRequest(limit, offset, filterString, sortString,
                                 List.of(), List.of(), sourceClass, entityClass, fieldMapping);
    }
    
    /**
     * Returns a new request with parsed filters and sorts.
     */
    public FilterRequest withParsedCriteria(List<FilterCriteria> filters, List<SortCriteria> sorts) {
        return new FilterRequest(limit, offset, filterString, sortString,
                                 filters, sorts, sourceClass, entityClass, fieldMapping);
    }
    
    /**
     * Calculates page number from offset and limit.
     */
    public int page() {
        return limit > 0 ? offset / limit : 0;
    }
    
    /**
     * Maps a source field name to entity field name.
     */
    public String mapField(String sourceField) {
        return fieldMapping.getOrDefault(sourceField, sourceField);
    }
    
    /**
     * @return true if there are any filters
     */
    public boolean hasFilters() {
        return !filters.isEmpty() || (filterString != null && !filterString.isBlank());
    }
    
    /**
     * @return true if there are any sorts
     */
    public boolean hasSorts() {
        return !sorts.isEmpty() || (sortString != null && !sortString.isBlank());
    }
}
