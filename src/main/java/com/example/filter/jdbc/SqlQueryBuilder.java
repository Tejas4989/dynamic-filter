package com.example.filter.jdbc;

import com.example.filter.metadata.EntityMetadata;
import com.example.filter.metadata.FieldMetadata;
import com.example.filter.model.FilterCriteria;
import com.example.filter.model.FilterOperator;
import com.example.filter.model.FilterRequest;
import com.example.filter.model.SortCriteria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds parameterized SQL queries from filter criteria.
 * 
 * <p>CRITICAL: This class generates SQL with named parameters only.
 * Values are NEVER concatenated into SQL strings - they are always
 * bound via parameters to prevent SQL injection.</p>
 * 
 * <p>This class is stateless and thread-safe.</p>
 */
public final class SqlQueryBuilder {
    
    private static final SqlQueryBuilder INSTANCE = new SqlQueryBuilder();
    
    private SqlQueryBuilder() {}
    
    public static SqlQueryBuilder getInstance() {
        return INSTANCE;
    }
    
    /**
     * Result of building a SQL query - contains the SQL and parameters.
     */
    public record QueryResult(
        String sql,
        String countSql,
        Map<String, Object> parameters
    ) {}
    
    /**
     * Builds a complete SELECT query with WHERE, ORDER BY, and pagination.
     *
     * @param baseSelect the base SELECT statement (e.g., "SELECT * FROM users u")
     * @param request the filter request
     * @param metadata the entity metadata
     * @return the query result with SQL and parameters
     */
    public QueryResult buildQuery(String baseSelect, FilterRequest request, EntityMetadata metadata) {
        Map<String, Object> parameters = new HashMap<>();
        AtomicInteger paramCounter = new AtomicInteger(0);
        
        // Build WHERE clause
        String whereClause = buildWhereClause(request.filters(), metadata, parameters, paramCounter);
        
        // Build ORDER BY clause
        String orderByClause = buildOrderByClause(request.sorts(), metadata);
        
        // Build full query
        StringBuilder sql = new StringBuilder(baseSelect);
        
        // Build count query by replacing SELECT ... FROM with SELECT COUNT(*) FROM
        String countSqlStr = baseSelect.replaceFirst("(?i)SELECT\\s+.+?\\s+FROM", "SELECT COUNT(*) FROM");
        StringBuilder countSql = new StringBuilder(countSqlStr);
        
        if (!whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
            countSql.append(" WHERE ").append(whereClause);
        }
        
        if (!orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }
        
        // Add pagination
        sql.append(" LIMIT :limit OFFSET :offset");
        parameters.put("limit", request.limit());
        parameters.put("offset", request.offset());
        
        return new QueryResult(sql.toString(), countSql.toString(), Map.copyOf(parameters));
    }
    
    /**
     * Builds the WHERE clause from filter criteria.
     * 
     * @return the WHERE clause (without "WHERE" keyword), or empty string if no filters
     */
    public String buildWhereClause(List<FilterCriteria> filters, 
                                   EntityMetadata metadata,
                                   Map<String, Object> parameters,
                                   AtomicInteger paramCounter) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        
        List<String> conditions = new ArrayList<>();
        
        for (FilterCriteria criterion : filters) {
            String condition = buildCondition(criterion, metadata, parameters, paramCounter);
            if (condition != null && !condition.isEmpty()) {
                conditions.add(condition);
            }
        }
        
        return String.join(" AND ", conditions);
    }
    
    /**
     * Builds a single SQL condition from a filter criterion.
     */
    private String buildCondition(FilterCriteria criterion,
                                  EntityMetadata metadata,
                                  Map<String, Object> parameters,
                                  AtomicInteger paramCounter) {
        String fieldName = criterion.field();
        
        // Get the database column name from metadata
        String columnName = metadata.getColumnName(fieldName)
            .orElse(toSnakeCase(fieldName));
        
        // Get field metadata for type information
        FieldMetadata fieldMeta = metadata.getField(fieldName).orElse(null);
        FilterOperator operator = criterion.operator();
        
        // Handle null-check operators (no value needed)
        if (criterion.isNullCheck()) {
            return "%s %s".formatted(columnName, operator.getSqlOperator());
        }
        
        // Handle multi-value operators (IN, NOT IN)
        if (criterion.isMultiValue()) {
            List<Object> convertedValues = criterion.values().stream()
                .map(v -> convertValue(v, fieldMeta))
                .toList();
            
            String paramName = "p" + paramCounter.incrementAndGet();
            parameters.put(paramName, convertedValues);
            return "%s %s (:%s)".formatted(columnName, operator.getSqlOperator(), paramName);
        }
        
        // Handle single-value operators
        String paramName = "p" + paramCounter.incrementAndGet();
        Object value = convertValue(criterion.value(), fieldMeta);
        
        // Transform value for LIKE operations
        if (operator.requiresPatternValue()) {
            value = operator.transformValueForLike(value.toString());
        }
        
        parameters.put(paramName, value);
        return "%s %s :%s".formatted(columnName, operator.getSqlOperator(), paramName);
    }
    
    /**
     * Builds the ORDER BY clause from sort criteria.
     */
    public String buildOrderByClause(List<SortCriteria> sorts, EntityMetadata metadata) {
        if (sorts == null || sorts.isEmpty()) {
            return "";
        }
        
        List<String> orderParts = new ArrayList<>();
        
        for (SortCriteria sort : sorts) {
            String columnName = metadata.getColumnName(sort.field())
                .orElse(toSnakeCase(sort.field()));
            
            // Column name is from trusted metadata, direction is from enum
            orderParts.add("%s %s".formatted(columnName, sort.direction().getSql()));
        }
        
        return String.join(", ", orderParts);
    }
    
    /**
     * Converts a string value to the appropriate type based on field metadata.
     */
    private Object convertValue(String value, FieldMetadata fieldMeta) {
        if (fieldMeta == null) {
            return value;
        }
        
        Class<?> type = fieldMeta.fieldType();
        
        try {
            if (type == Long.class || type == long.class) {
                return Long.parseLong(value);
            } else if (type == Integer.class || type == int.class) {
                return Integer.parseInt(value);
            } else if (type == Double.class || type == double.class) {
                return Double.parseDouble(value);
            } else if (type == Float.class || type == float.class) {
                return Float.parseFloat(value);
            } else if (type == Boolean.class || type == boolean.class) {
                return Boolean.parseBoolean(value);
            }
        } catch (NumberFormatException e) {
            // Fall through to return as string
        }
        
        return value;
    }
    
    /**
     * Converts camelCase to snake_case.
     */
    private String toSnakeCase(String str) {
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
}
