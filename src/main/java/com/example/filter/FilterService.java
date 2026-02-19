package com.example.filter;

import com.example.filter.metadata.EntityMetadata;
import com.example.filter.metadata.EntityMetadataRegistry;
import com.example.filter.model.FilterCriteria;
import com.example.filter.model.FilterRequest;
import com.example.filter.model.SortCriteria;
import com.example.filter.parser.FilterParser;
import com.example.filter.validation.FilterValidator;

import java.util.List;

/**
 * Main service for parsing and validating filter requests.
 */
public final class FilterService {
    
    private final EntityMetadataRegistry metadataRegistry;
    private final FilterParser parser;
    private final FilterValidator validator;
    
    public FilterService() {
        this.metadataRegistry = EntityMetadataRegistry.getInstance();
        this.parser = FilterParser.getInstance();
        this.validator = FilterValidator.getInstance();
    }
    
    /**
     * Parses and validates a FilterRequest, returning an enriched request with parsed criteria.
     *
     * @param request the raw request with filter/sort strings
     * @return enriched request with parsed and validated criteria
     */
    public FilterRequest parseAndValidate(FilterRequest request) {
        // Parse
        List<FilterCriteria> filters = parser.parseFilters(request.filterString());
        List<SortCriteria> sorts = parser.parseSorts(request.sortString());
        
        // Validate against entity metadata if entity class is provided
        if (request.entityClass() != null) {
            EntityMetadata metadata = metadataRegistry.getOrRegister(request.entityClass());
            validator.validateFilters(filters, metadata);
            validator.validateSorts(sorts, metadata);
        }
        
        return request.withParsedCriteria(filters, sorts);
    }
    
    /**
     * Convenience method: parse and validate from raw parameters.
     */
    public FilterRequest parseAndValidate(Class<?> entityClass, String filterString, 
                                          String sortString, Integer limit, Integer offset) {
        FilterRequest request = FilterRequest.of(limit, offset, filterString, sortString, entityClass);
        return parseAndValidate(request);
    }
    
    /**
     * Gets metadata for an entity class.
     */
    public EntityMetadata getMetadata(Class<?> entityClass) {
        return metadataRegistry.getOrRegister(entityClass);
    }
    
    /**
     * Parses filters without validation.
     */
    public List<FilterCriteria> parseFilters(String filterString) {
        return parser.parseFilters(filterString);
    }
    
    /**
     * Parses sorts without validation.
     */
    public List<SortCriteria> parseSorts(String sortString) {
        return parser.parseSorts(sortString);
    }
}
