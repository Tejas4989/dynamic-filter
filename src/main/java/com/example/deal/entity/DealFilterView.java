package com.example.deal.entity;

import java.math.BigDecimal;

/**
 * Query/Filter Entity for Deal searches.
 * 
 * <p>This is a FLATTENED view that joins multiple tables for filtering purposes:
 * <ul>
 *   <li>deals table (d) - main entity</li>
 *   <li>users table (u) - analyst info via FK</li>
 *   <li>programs table (p) - one-to-many programs</li>
 *   <li>contracts table (c) - one-to-many contracts per program</li>
 * </ul>
 *
 * <p><b>KEY CONCEPT:</b> This entity produces ONE ROW PER CONTRACT (or one row per program when no contracts).
 * A deal with 3 programs will appear as 3 rows in the result set.
 * The repository layer aggregates these flat rows back into hierarchical Deal objects.</p>
 * 
 * <p><b>Why use this pattern?</b>
 * <ul>
 *   <li>Enables filtering on ANY field from ANY joined table</li>
 *   <li>Filter by analystName → filters via users table</li>
 *   <li>Filter by programName → filters via programs table</li>
 *   <li>The SqlQueryBuilder works generically on this flat structure</li>
 * </ul>
 * 
 * <p><b>Example Query:</b>
 * <pre>
 * GET /api/v1/deals?filter=analystName:sw:John,programType:eq:DEVELOPMENT
 * 
 * Generates:
 * SELECT d.deal_id, d.deal_name, ..., u.first_name, ..., p.program_name, ...
 * FROM deals d
 * LEFT JOIN users u ON d.analyst_id = u.user_id
 * LEFT JOIN programs p ON d.deal_id = p.deal_id
 * WHERE CONCAT(u.first_name, ' ', u.last_name) LIKE 'John%'
 *   AND p.program_type = 'DEVELOPMENT'
 * </pre>
 *
 * @param dealId deal identifier
 * @param dealName deal name
 * @param analystId analyst user ID (FK)
 * @param analystName analyst full name (from users table)
 * @param dealStatus deal status
 * @param dealAmount deal amount
 * @param programId program identifier (from programs table)
 * @param programName program name (from programs table)
 * @param programType program type (from programs table)
 * @param programBudget program budget (from programs table)
 * @param contractId contract identifier (from contracts table)
 * @param contractName contract name (from contracts table)
 */
public record DealFilterView(
    // === Deal fields (from deals table) ===
    Long dealId,
    String dealName,
    Long analystId,
    String dealStatus,
    BigDecimal dealAmount,
    
    // === Analyst fields (from users table via FK) ===
    String analystName,
    
    // === Program fields (from programs table via 1:N) ===
    Long programId,
    String programName,
    String programType,
    BigDecimal programBudget,

    // === Contract fields (from contracts table via 1:N under program) ===
    Long contractId,
    String contractName
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // FILTERABLE FIELD CONSTANTS
    // These are discovered via reflection by EntityMetadataRegistry
    // ═══════════════════════════════════════════════════════════════════════════
    
    // Deal fields
    public static final String FIELD_DEAL_ID = "dealId";
    public static final String FIELD_DEAL_NAME = "dealName";
    public static final String FIELD_ANALYST_ID = "analystId";
    public static final String FIELD_DEAL_STATUS = "dealStatus";
    public static final String FIELD_DEAL_AMOUNT = "dealAmount";
    
    // Analyst fields (from users table)
    public static final String FIELD_ANALYST_NAME = "analystName";
    
    // Program fields (from programs table)
    public static final String FIELD_PROGRAM_ID = "programId";
    public static final String FIELD_PROGRAM_NAME = "programName";
    public static final String FIELD_PROGRAM_TYPE = "programType";
    public static final String FIELD_PROGRAM_BUDGET = "programBudget";

    // Contract fields (from contracts table)
    public static final String FIELD_CONTRACT_ID = "contractId";
    public static final String FIELD_CONTRACT_NAME = "contractName";
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COLUMN MAPPINGS
    // Maps Java field names to SQL column names/expressions
    // ═══════════════════════════════════════════════════════════════════════════
    
    // Deal columns
    public static final String COL_DEAL_ID = "d.deal_id";
    public static final String COL_DEAL_NAME = "d.deal_name";
    public static final String COL_ANALYST_ID = "d.analyst_id";
    public static final String COL_DEAL_STATUS = "d.deal_status";
    public static final String COL_DEAL_AMOUNT = "d.deal_amount";
    
    // Analyst columns (computed from users table)
    // NOTE: For WHERE clause, we must use the expression, not the alias
    public static final String COL_ANALYST_NAME = "CONCAT(u.first_name, ' ', u.last_name)";
    
    // Program columns
    public static final String COL_PROGRAM_ID = "p.program_id";
    public static final String COL_PROGRAM_NAME = "p.program_name";
    public static final String COL_PROGRAM_TYPE = "p.program_type";
    public static final String COL_PROGRAM_BUDGET = "p.budget";

    // Contract columns
    public static final String COL_CONTRACT_ID = "c.contract_id";
    public static final String COL_CONTRACT_NAME = "c.contract_name";
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BASE QUERY WITH ALL JOINS
    // This query produces the flattened view for filtering
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Base SELECT query that joins all related tables.
     *
     * <p>Produces one row per (deal, program, contract) combination.
     * LEFT JOINs ensure deals without analysts, programs, or contracts are included.</p>
     */
    public static final String BASE_SELECT = """
        SELECT 
            d.deal_id,
            d.deal_name,
            d.analyst_id,
            d.deal_status,
            d.deal_amount,
            CONCAT(u.first_name, ' ', u.last_name) AS analyst_name,
            p.program_id,
            p.program_name,
            p.program_type,
            p.budget AS program_budget,
            c.contract_id,
            c.contract_name
        FROM deals d
        LEFT JOIN users u ON d.analyst_id = u.user_id
        LEFT JOIN programs p ON d.deal_id = p.deal_id
        LEFT JOIN contracts c ON p.program_id = c.program_id
        """;
    
    /**
     * Base query to get deal IDs for pagination.
     * 
     * <p>Since the main query produces multiple rows per deal (one per program),
     * we need to paginate on unique deal_ids first, then fetch full data.</p>
     * 
     * <p>Note: This query uses GROUP BY (added dynamically) instead of DISTINCT
     * to support ORDER BY on any column. The repository will append:
     * <ul>
     *   <li>WHERE clause (filters)</li>
     *   <li>GROUP BY d.deal_id, [sort columns]</li>
     *   <li>ORDER BY [sort columns]</li>
     *   <li>LIMIT/OFFSET</li>
     * </ul>
     */
    public static final String DEAL_IDS_SELECT = """
        SELECT d.deal_id
        FROM deals d
        LEFT JOIN users u ON d.analyst_id = u.user_id
        LEFT JOIN programs p ON d.deal_id = p.deal_id
        LEFT JOIN contracts c ON p.program_id = c.program_id
        """;
    
    /**
     * Count query for total distinct deals.
     */
    public static final String COUNT_SELECT = """
        SELECT COUNT(DISTINCT d.deal_id)
        FROM deals d
        LEFT JOIN users u ON d.analyst_id = u.user_id
        LEFT JOIN programs p ON d.deal_id = p.deal_id
        LEFT JOIN contracts c ON p.program_id = c.program_id
        """;
    
    // Virtual table name (for metadata registry)
    public static final String TABLE_NAME = "deal_filter_view";
}
