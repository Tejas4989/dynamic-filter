package com.example.deal.api;

import com.example.deal.entity.Deal;
import com.example.deal.service.DealService;
import com.example.filter.model.PageResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * REST Controller for Deal API.
 * 
 * <p>Demonstrates the Query Entity pattern where filtering is done on a
 * flattened view (DealFilterView) that joins multiple tables, but the
 * response returns rich domain objects (Deal with nested Programs).</p>
 * 
 * <p><b>Filterable Fields:</b>
 * <ul>
 *   <li>Deal fields: dealId, dealName, analystId, dealStatus, dealAmount</li>
 *   <li>Analyst fields: analystName (from users table via FK)</li>
 *   <li>Program fields: programId, programName, programType, programBudget</li>
 *   <li>Contract fields: contractId, contractName (from contracts table under program)</li>
 * </ul>
 * 
 * <p><b>Example requests:</b>
 * <ul>
 *   <li>GET /api/v1/deals - All deals</li>
 *   <li>GET /api/v1/deals?filter=analystName:sw:John - Deals by analyst</li>
 *   <li>GET /api/v1/deals?filter=programType:eq:DEVELOPMENT - Deals with DEVELOPMENT programs</li>
 *   <li>GET /api/v1/deals?filter=dealAmount:gte:1000000 - High-value deals</li>
 *   <li>GET /api/v1/deals?filter=analystName:sw:John,programType:eq:RESEARCH&sort=dealAmount:desc</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/deals")
public class DealController {
    
    private final DealService dealService;
    
    public DealController(DealService dealService) {
        this.dealService = dealService;
    }
    
    /**
     * GET /api/v1/deals
     * 
     * Retrieves deals with optional filtering, sorting, and pagination.
     * 
     * <p>Filters can target fields from deals, users (analyst), or programs tables.</p>
     *
     * @param filter filter string (e.g., "analystName:sw:John,dealStatus:eq:ACTIVE")
     * @param sort sort string (e.g., "dealAmount:desc,dealName:asc")
     * @param limit maximum records to return (default 20, max 100)
     * @param offset number of records to skip (default 0)
     * @return paginated list of deals with nested programs
     */
    @GetMapping
    public ResponseEntity<PageResponse<Deal>> getDeals(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset) {
        
        PageResponse<Deal> response = dealService.findDeals(filter, sort, limit, offset);
        return ResponseEntity.ok(response);
    }
    
    /**
     * GET /api/v1/deals/{dealId}
     * 
     * Retrieves a single deal by ID with all its programs.
     *
     * @param dealId the deal ID
     * @return the deal if found, 404 otherwise
     */
    @GetMapping("/{dealId}")
    public ResponseEntity<Deal> getDealById(@PathVariable Long dealId) {
        return dealService.findById(dealId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * GET /api/v1/deals/metadata/fields
     * 
     * Returns the available filterable and sortable fields.
     * 
     * <p>Useful for client-side filter builders. Fields include columns from
     * deals, users (analyst), and programs tables.</p>
     */
    @GetMapping("/metadata/fields")
    public ResponseEntity<Map<String, Set<String>>> getFieldMetadata() {
        return ResponseEntity.ok(Map.of(
            "filterableFields", dealService.getFilterableFields(),
            "sortableFields", dealService.getSortableFields()
        ));
    }
}
