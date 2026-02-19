package com.example.deal.repository;

import com.example.deal.entity.Deal;
import com.example.deal.entity.DealFilterView;
import com.example.deal.entity.Program;
import com.example.filter.FilterService;
import com.example.filter.jdbc.SqlQueryBuilder;
import com.example.filter.metadata.EntityMetadata;
import com.example.filter.model.FilterRequest;
import com.example.filter.model.PageResponse;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for Deal entity using the Query Entity pattern.
 * 
 * <p>This repository demonstrates the Query Entity vs Domain Entity pattern:
 * <ul>
 *   <li><b>DealFilterView</b> (Query Entity) - Flat structure for filtering across joined tables</li>
 *   <li><b>Deal</b> (Domain Entity) - Rich object with nested Programs returned to API</li>
 * </ul>
 * 
 * <p><b>How it works:</b>
 * <ol>
 *   <li>Use DealFilterView metadata for validating filter fields</li>
 *   <li>Build WHERE clause using SqlQueryBuilder (works on flat view)</li>
 *   <li>Execute query with JOINs - produces multiple rows per deal</li>
 *   <li>Aggregate flat rows into hierarchical Deal objects</li>
 * </ol>
 * 
 * <p><b>Pagination Strategy:</b>
 * Since a deal with N programs produces N rows, we use a two-query approach:
 * <ol>
 *   <li>Query 1: Get paginated DISTINCT deal IDs</li>
 *   <li>Query 2: Get full data for those deal IDs</li>
 * </ol>
 */
@Repository
public class DealRepository {
    
    private final JdbcClient jdbcClient;
    private final SqlQueryBuilder queryBuilder;
    private final FilterService filterService;
    private final EntityMetadata filterViewMetadata;
    
    public DealRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.queryBuilder = SqlQueryBuilder.getInstance();
        this.filterService = new FilterService();
        // Use DealFilterView for filter metadata (flat view with all filterable fields)
        this.filterViewMetadata = filterService.getMetadata(DealFilterView.class);
    }
    
    /**
     * Finds deals matching the filter criteria with pagination.
     * 
     * <p>Uses two-query approach for correct pagination on deals (not rows).</p>
     *
     * @param request the filter request containing criteria, sorts, and pagination
     * @return a page of Deal objects (with nested Programs)
     */
    public PageResponse<Deal> findAll(FilterRequest request) {
        // Create a request without sorts for the deal IDs query
        // (DISTINCT queries can't ORDER BY columns not in SELECT)
        FilterRequest requestWithoutSorts = request.withParsedCriteria(request.filters(), List.of());
        
        // Step 1: Build WHERE clause from filters (no sorts for DISTINCT query)
        SqlQueryBuilder.QueryResult queryResult = queryBuilder.buildQuery(
            DealFilterView.DEAL_IDS_SELECT.trim(),
            requestWithoutSorts,
            filterViewMetadata
        );
        
        // Step 2: Get total count of distinct deals
        long totalElements = executeCountQuery(
            buildCountQuery(requestWithoutSorts),
            queryResult.parameters()
        );
        
        if (totalElements == 0) {
            return PageResponse.<Deal>builder()
                .content(List.of())
                .page(request.page())
                .size(request.limit())
                .totalElements(0)
                .appliedFilters(formatAppliedFilters(request))
                .appliedSorts(formatAppliedSorts(request))
                .build();
        }
        
        // Step 3: Get paginated deal IDs
        List<Long> dealIds = executeDealIdsQuery(queryResult.sql(), queryResult.parameters());
        
        if (dealIds.isEmpty()) {
            return PageResponse.<Deal>builder()
                .content(List.of())
                .page(request.page())
                .size(request.limit())
                .totalElements(totalElements)
                .appliedFilters(formatAppliedFilters(request))
                .appliedSorts(formatAppliedSorts(request))
                .build();
        }
        
        // Step 4: Get full data for those deal IDs (with programs)
        List<DealFilterView> flatRows = fetchFullDataForDeals(dealIds, request);
        
        // Step 5: Aggregate flat rows into Deal objects with nested Programs
        List<Deal> deals = aggregateToDeals(flatRows);
        
        return PageResponse.<Deal>builder()
            .content(deals)
            .page(request.page())
            .size(request.limit())
            .totalElements(totalElements)
            .appliedFilters(formatAppliedFilters(request))
            .appliedSorts(formatAppliedSorts(request))
            .build();
    }
    
    /**
     * Finds a deal by ID with all its programs.
     */
    public Optional<Deal> findById(Long dealId) {
        String sql = DealFilterView.BASE_SELECT.trim() + " WHERE d.deal_id = :dealId";
        
        List<DealFilterView> flatRows = jdbcClient.sql(sql)
            .param("dealId", dealId)
            .query(this::mapToFilterView)
            .list();
        
        if (flatRows.isEmpty()) {
            return Optional.empty();
        }
        
        List<Deal> deals = aggregateToDeals(flatRows);
        return deals.isEmpty() ? Optional.empty() : Optional.of(deals.getFirst());
    }
    
    /**
     * Builds the count query with filters applied.
     */
    private String buildCountQuery(FilterRequest request) {
        SqlQueryBuilder.QueryResult result = queryBuilder.buildQuery(
            DealFilterView.COUNT_SELECT.trim(),
            request,
            filterViewMetadata
        );
        // Remove LIMIT/OFFSET and ORDER BY from count query
        String sql = result.sql();
        sql = sql.replaceAll("\\s+ORDER\\s+BY\\s+[^\\s]+(\\s+(ASC|DESC))?(\\s*,\\s*[^\\s]+(\\s+(ASC|DESC))?)*", "");
        sql = sql.replaceAll("\\s+LIMIT\\s+:\\w+\\s+OFFSET\\s+:\\w+", "");
        return sql;
    }
    
    /**
     * Executes the count query.
     */
    private long executeCountQuery(String sql, Map<String, Object> parameters) {
        var spec = jdbcClient.sql(sql);
        
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (!"limit".equals(entry.getKey()) && !"offset".equals(entry.getKey())) {
                spec = spec.param(entry.getKey(), entry.getValue());
            }
        }
        
        return spec.query(Long.class).single();
    }
    
    /**
     * Executes query to get paginated deal IDs.
     */
    private List<Long> executeDealIdsQuery(String sql, Map<String, Object> parameters) {
        var spec = jdbcClient.sql(sql);
        
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }
        
        return spec.query((rs, rowNum) -> rs.getLong("deal_id")).list();
    }
    
    /**
     * Fetches full data for the given deal IDs.
     * 
     * <p>Applies sorting from the request to maintain order.</p>
     */
    private List<DealFilterView> fetchFullDataForDeals(List<Long> dealIds, FilterRequest request) {
        // Build ORDER BY clause
        String orderBy = queryBuilder.buildOrderByClause(request.sorts(), filterViewMetadata);
        
        StringBuilder sql = new StringBuilder(DealFilterView.BASE_SELECT.trim());
        sql.append(" WHERE d.deal_id IN (:dealIds)");
        
        if (!orderBy.isEmpty()) {
            sql.append(" ORDER BY ").append(orderBy);
        }
        
        return jdbcClient.sql(sql.toString())
            .param("dealIds", dealIds)
            .query(this::mapToFilterView)
            .list();
    }
    
    /**
     * Aggregates flat DealFilterView rows into hierarchical Deal objects.
     * 
     * <p>Groups rows by dealId and collects programs for each deal.</p>
     */
    private List<Deal> aggregateToDeals(List<DealFilterView> flatRows) {
        // Use LinkedHashMap to preserve order
        Map<Long, Deal.Builder> dealBuilders = new LinkedHashMap<>();
        Map<Long, List<Program>> dealPrograms = new LinkedHashMap<>();
        
        for (DealFilterView row : flatRows) {
            Long dealId = row.dealId();
            
            // Create deal builder if not exists
            if (!dealBuilders.containsKey(dealId)) {
                dealBuilders.put(dealId, Deal.builder()
                    .dealId(dealId)
                    .dealName(row.dealName())
                    .analystId(row.analystId())
                    .analystName(row.analystName())
                    .dealStatus(row.dealStatus())
                    .dealAmount(row.dealAmount()));
                dealPrograms.put(dealId, new ArrayList<>());
            }
            
            // Add program if present (LEFT JOIN may produce null program)
            if (row.programId() != null) {
                dealPrograms.get(dealId).add(Program.builder()
                    .programId(row.programId())
                    .programName(row.programName())
                    .programType(row.programType())
                    .budget(row.programBudget())
                    .build());
            }
        }
        
        // Build final Deal objects with programs
        List<Deal> deals = new ArrayList<>();
        for (Map.Entry<Long, Deal.Builder> entry : dealBuilders.entrySet()) {
            Long dealId = entry.getKey();
            Deal deal = entry.getValue()
                .programs(dealPrograms.get(dealId))
                .build();
            deals.add(deal);
        }
        
        return deals;
    }
    
    /**
     * Maps a ResultSet row to DealFilterView.
     */
    private DealFilterView mapToFilterView(ResultSet rs, int rowNum) throws SQLException {
        Long programId = rs.getObject("program_id") != null ? rs.getLong("program_id") : null;
        
        return new DealFilterView(
            rs.getLong("deal_id"),
            rs.getString("deal_name"),
            rs.getObject("analyst_id") != null ? rs.getLong("analyst_id") : null,
            rs.getString("deal_status"),
            rs.getBigDecimal("deal_amount"),
            rs.getString("analyst_name"),
            programId,
            rs.getString("program_name"),
            rs.getString("program_type"),
            rs.getBigDecimal("program_budget")
        );
    }
    
    /**
     * Formats applied filters for the response.
     */
    private List<String> formatAppliedFilters(FilterRequest request) {
        return request.filters().stream()
            .map(Object::toString)
            .toList();
    }
    
    /**
     * Formats applied sorts for the response.
     */
    private List<String> formatAppliedSorts(FilterRequest request) {
        return request.sorts().stream()
            .map(Object::toString)
            .toList();
    }
}
