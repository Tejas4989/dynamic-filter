package com.example.deal.service;

import com.example.deal.entity.Deal;
import com.example.deal.entity.DealFilterView;
import com.example.deal.repository.DealRepository;
import com.example.filter.FilterService;
import com.example.filter.model.FilterRequest;
import com.example.filter.model.PageResponse;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/**
 * Service layer for Deal operations.
 * 
 * <p>Bridges the Controller and Repository layers, handling:
 * <ul>
 *   <li>Filter/sort parsing and validation against DealFilterView metadata</li>
 *   <li>Business logic</li>
 *   <li>Transaction management (if needed)</li>
 * </ul>
 * 
 * <p>Note: Filters are validated against {@link DealFilterView} which is the
 * flattened query entity containing fields from all joined tables.</p>
 */
@Service
public class DealService {
    
    private final DealRepository dealRepository;
    private final FilterService filterService;
    
    public DealService(DealRepository dealRepository) {
        this.dealRepository = dealRepository;
        this.filterService = new FilterService();
    }
    
    /**
     * Retrieves deals matching the given filter and sort criteria.
     * 
     * <p>Filterable fields include:
     * <ul>
     *   <li>Deal fields: dealId, dealName, analystId, dealStatus, dealAmount</li>
     *   <li>Analyst fields: analystName (from users table)</li>
     *   <li>Program fields: programId, programName, programType, programBudget</li>
     * </ul>
     *
     * @param filterString the filter query string (e.g., "analystName:sw:John,programType:eq:DEVELOPMENT")
     * @param sortString the sort query string (e.g., "dealAmount:desc,dealName:asc")
     * @param limit maximum records to return
     * @param offset number of records to skip
     * @return a page of deals (with nested programs)
     */
    public PageResponse<Deal> findDeals(String filterString,
                                        String sortString,
                                        Integer limit,
                                        Integer offset) {
        // Parse and validate against DealFilterView (flat query entity)
        FilterRequest request = filterService.parseAndValidate(
            DealFilterView.class,  // Validates against the query entity
            filterString,
            sortString,
            limit,
            offset
        );
        
        // Delegate to repository
        return dealRepository.findAll(request);
    }
    
    /**
     * Retrieves a deal by its ID.
     *
     * @param dealId the deal ID
     * @return the deal if found (with all programs)
     */
    public Optional<Deal> findById(Long dealId) {
        if (dealId == null || dealId < 0) {
            return Optional.empty();
        }
        return dealRepository.findById(dealId);
    }
    
    /**
     * Gets the filterable fields for Deal queries.
     * 
     * <p>Returns fields from DealFilterView which includes
     * fields from deals, users, and programs tables.</p>
     */
    public Set<String> getFilterableFields() {
        return filterService.getMetadata(DealFilterView.class).getFilterableFields();
    }
    
    /**
     * Gets the sortable fields for Deal queries.
     */
    public Set<String> getSortableFields() {
        return filterService.getMetadata(DealFilterView.class).getSortableFields();
    }
}
