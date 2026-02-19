package com.example.filter.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enumeration of supported filter operators with their SQL mappings.
 * 
 * <p>Each operator defines how a field value comparison is translated to SQL.</p>
 */
public enum FilterOperator {
    
    // Equality operators
    EQ("eq", "=", false, "Equals"),
    NE("ne", "<>", false, "Not Equals"),
    
    // Comparison operators
    GT("gt", ">", false, "Greater Than"),
    GTE("gte", ">=", false, "Greater Than or Equal"),
    LT("lt", "<", false, "Less Than"),
    LTE("lte", "<=", false, "Less Than or Equal"),
    
    // String operators (use LIKE)
    STARTS_WITH("sw", "LIKE", true, "Starts With"),
    ENDS_WITH("ew", "LIKE", true, "Ends With"),
    CONTAINS("contains", "LIKE", true, "Contains"),
    
    // Collection operators
    IN("in", "IN", false, "In List"),
    NOT_IN("nin", "NOT IN", false, "Not In List"),
    
    // Null operators
    IS_NULL("null", "IS NULL", false, "Is Null"),
    IS_NOT_NULL("notnull", "IS NOT NULL", false, "Is Not Null");
    
    private final String code;
    private final String sqlOperator;
    private final boolean requiresPatternValue;
    private final String description;
    
    // Cache for O(1) lookup by code
    private static final Map<String, FilterOperator> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                FilterOperator::getCode,
                Function.identity()
            ));
    
    FilterOperator(String code, String sqlOperator, boolean requiresPatternValue, String description) {
        this.code = code;
        this.sqlOperator = sqlOperator;
        this.requiresPatternValue = requiresPatternValue;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getSqlOperator() {
        return sqlOperator;
    }
    
    public boolean requiresPatternValue() {
        return requiresPatternValue;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Checks if this operator requires a value (non-null operators do).
     */
    public boolean requiresValue() {
        return this != IS_NULL && this != IS_NOT_NULL;
    }
    
    /**
     * Checks if this operator works with collections.
     */
    public boolean isCollectionOperator() {
        return this == IN || this == NOT_IN;
    }
    
    /**
     * Looks up an operator by its code (case-insensitive).
     *
     * @param code the operator code (e.g., "eq", "sw", "in")
     * @return Optional containing the operator, or empty if not found
     */
    public static Optional<FilterOperator> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CODE_MAP.get(code.toLowerCase()));
    }
    
    /**
     * Transforms the value for LIKE operations.
     *
     * @param value the original value
     * @return the pattern value for SQL LIKE
     */
    public String transformValueForLike(String value) {
        return switch (this) {
            case STARTS_WITH -> value + "%";
            case ENDS_WITH -> "%" + value;
            case CONTAINS -> "%" + value + "%";
            default -> value;
        };
    }
}
