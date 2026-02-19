package com.example.user.repository;

import com.example.filter.FilterService;
import com.example.filter.jdbc.SqlQueryBuilder;
import com.example.filter.metadata.EntityMetadata;
import com.example.filter.model.FilterCriteria;
import com.example.filter.model.FilterOperator;
import com.example.filter.model.FilterRequest;
import com.example.filter.model.PageResponse;
import com.example.user.entity.User;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Repository for User entity using Spring JdbcClient.
 * 
 * <p>Implements dynamic filtering with full SQL injection protection
 * via parameterized queries. All user input is bound through named parameters,
 * never concatenated into SQL strings.</p>
 * 
 * <p>This implementation uses:
 * <ul>
 *   <li>JdbcClient for modern, fluent JDBC access</li>
 *   <li>Named parameters for all dynamic values</li>
 *   <li>Entity metadata for column mapping</li>
 * </ul>
 * 
 * <p>Special handling for roleIds filter:
 * Since roleIds are stored in a junction table (user_roles), filters on roleIds
 * use a subquery: {@code u.user_id IN (SELECT user_id FROM user_roles WHERE role_id IN (:values))}
 */
@Repository
public class UserRepository {
    
    private static final String BASE_SELECT = """
        SELECT u.user_id, u.username, u.first_name, u.last_name
        FROM users u
        """;
    
    private static final String ROLE_IDS_FIELD = "roleIds";
    
    private static final String SELECT_BY_ID = """
        SELECT u.user_id, u.username, u.first_name, u.last_name
        FROM users u
        WHERE u.user_id = :userId
        """;
    
    private static final String SELECT_ROLE_IDS = """
        SELECT role_id FROM user_roles WHERE user_id = :userId
        """;
    
    private final JdbcClient jdbcClient;
    private final SqlQueryBuilder queryBuilder;
    private final FilterService filterService;
    private final EntityMetadata metadata;
    
    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.queryBuilder = SqlQueryBuilder.getInstance();
        this.filterService = new FilterService();
        this.metadata = filterService.getMetadata(User.class);
    }
    
    /**
     * Finds users matching the filter criteria with pagination.
     *
     * @param request the filter request containing criteria, sorts, and pagination
     * @return a page of users matching the criteria
     */
    public PageResponse<User> findAll(FilterRequest request) {
        // Separate roleIds filters from other filters (roleIds requires junction table query)
        List<FilterCriteria> roleIdFilters = request.filters().stream()
            .filter(f -> ROLE_IDS_FIELD.equals(f.field()))
            .toList();
        
        List<FilterCriteria> standardFilters = request.filters().stream()
            .filter(f -> !ROLE_IDS_FIELD.equals(f.field()))
            .toList();
        
        // Create modified request with only standard filters
        FilterRequest modifiedRequest = request.withParsedCriteria(standardFilters, request.sorts());
        
        // Build the base query with standard filters
        SqlQueryBuilder.QueryResult queryResult = queryBuilder.buildQuery(
            BASE_SELECT.trim(), 
            modifiedRequest, 
            metadata
        );
        
        // Add roleIds subquery conditions if present
        Map<String, Object> allParameters = new HashMap<>(queryResult.parameters());
        String sql = queryResult.sql();
        String countSql = queryResult.countSql();
        
        if (!roleIdFilters.isEmpty()) {
            String roleIdConditions = buildRoleIdConditions(roleIdFilters, allParameters);
            
            // Insert roleIds conditions into the query
            if (sql.contains("WHERE")) {
                // Add to existing WHERE clause
                sql = sql.replace("WHERE", "WHERE " + roleIdConditions + " AND");
                countSql = countSql.replace("WHERE", "WHERE " + roleIdConditions + " AND");
            } else {
                // Insert WHERE clause before ORDER BY or LIMIT
                if (sql.contains("ORDER BY")) {
                    sql = sql.replace("ORDER BY", "WHERE " + roleIdConditions + " ORDER BY");
                    countSql = countSql + " WHERE " + roleIdConditions;
                } else {
                    sql = sql.replace("LIMIT", "WHERE " + roleIdConditions + " LIMIT");
                    countSql = countSql + " WHERE " + roleIdConditions;
                }
            }
        }
        
        // Execute count query for total elements
        long totalElements = executeCountQuery(countSql, allParameters);
        
        // Execute main query
        List<User> users = executeQuery(sql, allParameters);
        
        // Load role IDs for each user
        users = users.stream()
            .map(this::loadRoleIds)
            .toList();
        
        // Build response with applied filter/sort info
        List<String> appliedFilters = request.filters().stream()
            .map(Object::toString)
            .toList();
        List<String> appliedSorts = request.sorts().stream()
            .map(Object::toString)
            .toList();
        
        return PageResponse.<User>builder()
            .content(users)
            .page(request.page())
            .size(request.limit())
            .totalElements(totalElements)
            .appliedFilters(appliedFilters)
            .appliedSorts(appliedSorts)
            .build();
    }
    
    /**
     * Builds SQL conditions for roleIds filters using subqueries to the user_roles table.
     * 
     * <p>Supports IN, NOT IN, and equality operators on roleIds.</p>
     *
     * @param roleIdFilters the roleIds filter criteria
     * @param parameters map to add query parameters to
     * @return SQL condition string
     */
    private String buildRoleIdConditions(List<FilterCriteria> roleIdFilters, Map<String, Object> parameters) {
        List<String> conditions = new ArrayList<>();
        int paramIndex = parameters.size() + 1;
        
        for (FilterCriteria filter : roleIdFilters) {
            String paramName = "roleId" + paramIndex++;
            FilterOperator op = filter.operator();
            
            switch (op) {
                case IN -> {
                    // User has ANY of the specified roles
                    List<Long> roleIds = filter.values().stream()
                        .map(Long::parseLong)
                        .toList();
                    parameters.put(paramName, roleIds);
                    conditions.add("u.user_id IN (SELECT ur.user_id FROM user_roles ur WHERE ur.role_id IN (:" + paramName + "))");
                }
                case NOT_IN -> {
                    // User does NOT have any of the specified roles
                    List<Long> roleIds = filter.values().stream()
                        .map(Long::parseLong)
                        .toList();
                    parameters.put(paramName, roleIds);
                    conditions.add("u.user_id NOT IN (SELECT ur.user_id FROM user_roles ur WHERE ur.role_id IN (:" + paramName + "))");
                }
                case EQ -> {
                    // User has exactly this role
                    Long roleId = Long.parseLong(filter.value());
                    parameters.put(paramName, roleId);
                    conditions.add("u.user_id IN (SELECT ur.user_id FROM user_roles ur WHERE ur.role_id = :" + paramName + ")");
                }
                case NE -> {
                    // User does not have this specific role
                    Long roleId = Long.parseLong(filter.value());
                    parameters.put(paramName, roleId);
                    conditions.add("u.user_id NOT IN (SELECT ur.user_id FROM user_roles ur WHERE ur.role_id = :" + paramName + ")");
                }
                default -> {
                    // For unsupported operators, skip (validation should catch this earlier)
                }
            }
        }
        
        return String.join(" AND ", conditions);
    }
    
    /**
     * Finds a user by ID.
     */
    public Optional<User> findById(Long userId) {
        List<User> results = jdbcClient.sql(SELECT_BY_ID)
            .param("userId", userId)
            .query(this::mapRow)
            .list();
        
        if (results.isEmpty()) {
            return Optional.empty();
        }
        
        return Optional.of(loadRoleIds(results.getFirst()));
    }
    
    /**
     * Executes the main query and returns the list of users.
     */
    private List<User> executeQuery(String sql, Map<String, Object> parameters) {
        var spec = jdbcClient.sql(sql);
        
        // Bind all parameters safely
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }
        
        return spec.query(this::mapRow).list();
    }
    
    /**
     * Executes the count query and returns the total count.
     */
    private long executeCountQuery(String sql, Map<String, Object> parameters) {
        var spec = jdbcClient.sql(sql);
        
        // Bind all parameters safely (except pagination params)
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (!"limit".equals(entry.getKey()) && !"offset".equals(entry.getKey())) {
                spec = spec.param(entry.getKey(), entry.getValue());
            }
        }
        
        return spec.query(Long.class).single();
    }
    
    /**
     * Loads role IDs for a user from the junction table.
     */
    private User loadRoleIds(User user) {
        List<Long> roleIds = jdbcClient.sql(SELECT_ROLE_IDS)
            .param("userId", user.userId())
            .query((rs, rowNum) -> rs.getLong("role_id"))
            .list();
        
        return User.builder()
            .userId(user.userId())
            .username(user.username())
            .firstName(user.firstName())
            .lastName(user.lastName())
            .roleIds(roleIds)
            .build();
    }
    
    /**
     * Maps a ResultSet row to a User (without role IDs).
     */
    private User mapRow(ResultSet rs, int rowNum) throws SQLException {
        return User.builder()
            .userId(rs.getLong("user_id"))
            .username(rs.getString("username"))
            .firstName(rs.getString("first_name"))
            .lastName(rs.getString("last_name"))
            .roleIds(List.of()) // Will be loaded separately
            .build();
    }
}
