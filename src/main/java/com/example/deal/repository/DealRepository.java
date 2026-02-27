package com.example.deal.repository;

import com.example.deal.entity.Contract;
import com.example.deal.entity.Deal;
import com.example.deal.entity.DealFilterView;
import com.example.deal.entity.Program;
import com.example.deal.entity.UserOption;
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
    
    public DealRepository(JdbcClient jdbcClient, FilterService filterService) {
        this.jdbcClient = jdbcClient;
        this.queryBuilder = SqlQueryBuilder.getInstance();
        this.filterService = filterService;
        this.filterViewMetadata = filterService.getMetadata(DealFilterView.class);
    }
    
    /**
     * Finds deals matching the filter criteria with pagination.
     * 
     * <p>Uses two-query approach for correct pagination on deals (not rows):</p>
     * <ol>
     *   <li>Query 1: Get paginated deal IDs (using GROUP BY for sort support)</li>
     *   <li>Query 2: Get full data for those deal IDs</li>
     * </ol>
     * 
     * <p><b>Why GROUP BY instead of DISTINCT?</b><br>
     * DISTINCT requires ORDER BY columns to be in SELECT list.
     * GROUP BY allows ORDER BY on any grouped column, enabling sorting.</p>
     *
     * @param request the filter request containing criteria, sorts, and pagination
     * @return a page of Deal objects (with nested Programs)
     */
    public PageResponse<Deal> findAll(FilterRequest request) {
        // Step 1: Build the deal IDs query with GROUP BY (supports sorting)
        DealIdsQueryResult dealIdsQueryResult = buildDealIdsQuery(request);
        
        // Step 2: Get total count of distinct deals
        // Build count query using same parameters (excluding limit/offset)
        Map<String, Object> countParams = new LinkedHashMap<>();
        String countSql = buildCountQuery(request, countParams);
        long totalElements = executeCountQuery(countSql, countParams);
        
        if (totalElements == 0) {
            return PageResponse.<Deal>builder()
                .content(List.of())
                .page(request.page())
                .size(request.limit())
                .totalElements(0)
                .appliedFilters(request.appliedFiltersAsStrings())
                .appliedSorts(request.appliedSortsAsStrings())
                .build();
        }
        
        // Step 3: Get paginated deal IDs (with sorting applied via GROUP BY)
        List<Long> dealIds = executeDealIdsQuery(
            dealIdsQueryResult.sql(), 
            dealIdsQueryResult.parameters()
        );
        
        if (dealIds.isEmpty()) {
            return PageResponse.<Deal>builder()
                .content(List.of())
                .page(request.page())
                .size(request.limit())
                .totalElements(totalElements)
                .appliedFilters(request.appliedFiltersAsStrings())
                .appliedSorts(request.appliedSortsAsStrings())
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
                .appliedFilters(request.appliedFiltersAsStrings())
                .appliedSorts(request.appliedSortsAsStrings())
                .build();
    }
    
    /**
     * Result holder for the deal IDs query.
     */
    private record DealIdsQueryResult(String sql, Map<String, Object> parameters) {}
    
    /**
     * Builds the deal IDs query with GROUP BY to support sorting.
     * 
     * <p>Uses GROUP BY instead of DISTINCT because:</p>
     * <ul>
     *   <li>DISTINCT requires ORDER BY columns to be in SELECT</li>
     *   <li>GROUP BY allows ORDER BY on any grouped column</li>
     * </ul>
     * 
     * <p>Generated SQL example:</p>
     * <pre>
     * SELECT d.deal_id
     * FROM deals d
     * LEFT JOIN users u ON d.analyst_id = u.user_id
     * LEFT JOIN programs p ON d.deal_id = p.deal_id
     * WHERE d.deal_status = :p1
     * GROUP BY d.deal_id, d.deal_amount
     * ORDER BY d.deal_amount DESC
     * LIMIT :limit OFFSET :offset
     * </pre>
     */
    private DealIdsQueryResult buildDealIdsQuery(FilterRequest request) {
        StringBuilder sql = new StringBuilder(DealFilterView.DEAL_IDS_SELECT.trim());
        Map<String, Object> parameters = new LinkedHashMap<>();
        java.util.concurrent.atomic.AtomicInteger paramCounter = new java.util.concurrent.atomic.AtomicInteger(1);
        
        // Add WHERE clause if there are filters
        if (!request.filters().isEmpty()) {
            String whereClause = queryBuilder.buildWhereClause(
                request.filters(), filterViewMetadata, parameters, paramCounter);
            if (!whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
            }
        }
        
        // Build GROUP BY clause - always include deal_id, plus any sort columns
        List<String> groupByColumns = new ArrayList<>();
        groupByColumns.add("d.deal_id");
        
        // Add sort columns to GROUP BY (required for ORDER BY to work with GROUP BY)
        if (!request.sorts().isEmpty()) {
            for (var sort : request.sorts()) {
                String column = filterViewMetadata.getColumnName(sort.field())
                    .orElse(null);
                if (column != null && !groupByColumns.contains(column)) {
                    groupByColumns.add(column);
                }
            }
        }
        
        sql.append(" GROUP BY ").append(String.join(", ", groupByColumns));
        
        // Add ORDER BY if sorts are specified
        if (!request.sorts().isEmpty()) {
            String orderBy = queryBuilder.buildOrderByClause(request.sorts(), filterViewMetadata);
            if (!orderBy.isEmpty()) {
                sql.append(" ORDER BY ").append(orderBy);
            }
        }
        
        // Add pagination
        sql.append(" LIMIT :limit OFFSET :offset");
        parameters.put("limit", request.limit());
        parameters.put("offset", request.offset());
        
        return new DealIdsQueryResult(sql.toString(), parameters);
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
     * 
     * <p>Uses the same parameter names as buildDealIdsQuery so they can share
     * the same parameter map.</p>
     */
    private String buildCountQuery(FilterRequest request, Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder(DealFilterView.COUNT_SELECT.trim());
        
        // Add WHERE clause if there are filters (reuse same param names as deal IDs query)
        if (!request.filters().isEmpty()) {
            java.util.concurrent.atomic.AtomicInteger paramCounter = new java.util.concurrent.atomic.AtomicInteger(1);
            String whereClause = queryBuilder.buildWhereClause(
                request.filters(), filterViewMetadata, parameters, paramCounter);
            if (!whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
            }
        }
        
        return sql.toString();
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
     * <p>Groups rows by dealId, collects programs (deduplicated by programId),
     * and nests contracts under each program.</p>
     */
    private List<Deal> aggregateToDeals(List<DealFilterView> flatRows) {
        // Use LinkedHashMap to preserve order
        Map<Long, Deal.Builder> dealBuilders = new LinkedHashMap<>();
        // dealId -> programId -> (Program builder, List<Contract>)
        Map<Long, Map<Long, ProgramAccumulator>> dealPrograms = new LinkedHashMap<>();

        for (DealFilterView row : flatRows) {
            Long dealId = row.dealId();

            // Create deal builder if not exists
            if (!dealBuilders.containsKey(dealId)) {
                dealBuilders.put(
                    dealId,
                    Deal.builder()
                        .dealId(dealId)
                        .dealName(row.dealName())
                        .analyst(new UserOption(row.analystId(), row.analystName()))
                        .dealStatus(row.dealStatus())
                        .dealAmount(row.dealAmount()));
                dealPrograms.put(dealId, new LinkedHashMap<>());
            }

            // Add program if present (LEFT JOIN may produce null program)
            if (row.programId() != null) {
                Map<Long, ProgramAccumulator> programs = dealPrograms.get(dealId);
                programs.computeIfAbsent(row.programId(), pid -> new ProgramAccumulator(
                    row.programId(),
                    row.programName(),
                    row.programType(),
                    row.programBudget()
                ));

                // Add contract if present
                if (row.contractId() != null && row.contractName() != null) {
                    programs.get(row.programId()).contracts.add(
                        Contract.builder()
                            .contractId(row.contractId())
                            .contractName(row.contractName())
                            .build()
                    );
                }
            }
        }

        // Build final Deal objects with programs and nested contracts
        List<Deal> deals = new ArrayList<>();
        for (Map.Entry<Long, Deal.Builder> entry : dealBuilders.entrySet()) {
            Long dealId = entry.getKey();
            List<Program> programs = dealPrograms.get(dealId).values().stream()
                .map(acc -> Program.builder()
                    .programId(acc.programId)
                    .programName(acc.programName)
                    .programType(acc.programType)
                    .budget(acc.programBudget)
                    .contracts(List.copyOf(acc.contracts))
                    .build())
                .toList();
            Deal deal = entry.getValue()
                .programs(programs)
                .build();
            deals.add(deal);
        }

        return deals;
    }

    /**
     * Accumulates program data and its contracts during aggregation.
     */
    private static class ProgramAccumulator {
        final Long programId;
        final String programName;
        final String programType;
        final BigDecimal programBudget;
        final List<Contract> contracts = new ArrayList<>();

        ProgramAccumulator(Long programId, String programName, String programType,
                          BigDecimal programBudget) {
            this.programId = programId;
            this.programName = programName;
            this.programType = programType;
            this.programBudget = programBudget;
        }
    }
    
    /**
     * Maps a ResultSet row to DealFilterView.
     */
    private DealFilterView mapToFilterView(ResultSet rs, int rowNum) throws SQLException {
        Long programId = rs.getObject("program_id") != null ? rs.getLong("program_id") : null;
        Long contractId = rs.getObject("contract_id") != null ? rs.getLong("contract_id") : null;

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
            rs.getBigDecimal("program_budget"),
            contractId,
            rs.getString("contract_name")
        );
    }
    
}
