package com.example.filter.jdbc;

import com.example.filter.metadata.EntityMetadata;
import com.example.filter.metadata.EntityMetadataRegistry;
import com.example.filter.model.FilterCriteria;
import com.example.filter.model.FilterOperator;
import com.example.filter.model.FilterRequest;
import com.example.filter.model.SortCriteria;
import com.example.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SqlQueryBuilder.
 */
class SqlQueryBuilderTest {
    
    private SqlQueryBuilder builder;
    private EntityMetadata metadata;
    
    @BeforeEach
    void setUp() {
        builder = SqlQueryBuilder.getInstance();
        metadata = EntityMetadataRegistry.getInstance().register(User.class);
    }
    
    @Nested
    @DisplayName("WHERE Clause Building")
    class WhereClauseTests {
        
        @Test
        @DisplayName("Should build equals condition with parameter")
        void shouldBuildEqualsCondition() {
            var filter = FilterCriteria.of("firstName", FilterOperator.EQ, "John");
            Map<String, Object> params = new HashMap<>();
            
            String where = builder.buildWhereClause(
                List.of(filter), 
                metadata, 
                params, 
                new AtomicInteger(0)
            );
            
            assertEquals("first_name = :p1", where);
            assertEquals("John", params.get("p1"));
        }
        
        @Test
        @DisplayName("Should build LIKE condition for starts-with")
        void shouldBuildLikeCondition() {
            var filter = FilterCriteria.of("firstName", FilterOperator.STARTS_WITH, "Jo");
            Map<String, Object> params = new HashMap<>();
            
            String where = builder.buildWhereClause(
                List.of(filter), 
                metadata, 
                params, 
                new AtomicInteger(0)
            );
            
            assertEquals("first_name LIKE :p1", where);
            assertEquals("Jo%", params.get("p1")); // Value transformed for LIKE
        }
        
        @Test
        @DisplayName("Should build IN clause with list parameter")
        void shouldBuildInClause() {
            var filter = FilterCriteria.ofMulti("roleIds", FilterOperator.IN, List.of("1", "2", "3"));
            Map<String, Object> params = new HashMap<>();
            
            String where = builder.buildWhereClause(
                List.of(filter), 
                metadata, 
                params, 
                new AtomicInteger(0)
            );
            
            assertEquals("role_ids IN (:p1)", where);
            assertInstanceOf(List.class, params.get("p1"));
        }
        
        @Test
        @DisplayName("Should build IS NULL condition without parameter")
        void shouldBuildIsNullCondition() {
            var filter = FilterCriteria.ofNullCheck("lastName", FilterOperator.IS_NULL);
            Map<String, Object> params = new HashMap<>();
            
            String where = builder.buildWhereClause(
                List.of(filter), 
                metadata, 
                params, 
                new AtomicInteger(0)
            );
            
            assertEquals("last_name IS NULL", where);
            assertTrue(params.isEmpty()); // No parameters for NULL check
        }
        
        @Test
        @DisplayName("Should combine multiple filters with AND")
        void shouldCombineFiltersWithAnd() {
            var filter1 = FilterCriteria.of("firstName", FilterOperator.EQ, "John");
            var filter2 = FilterCriteria.of("lastName", FilterOperator.EQ, "Doe");
            Map<String, Object> params = new HashMap<>();
            
            String where = builder.buildWhereClause(
                List.of(filter1, filter2), 
                metadata, 
                params, 
                new AtomicInteger(0)
            );
            
            assertEquals("first_name = :p1 AND last_name = :p2", where);
            assertEquals("John", params.get("p1"));
            assertEquals("Doe", params.get("p2"));
        }
        
        @Test
        @DisplayName("Should return empty string for empty filters")
        void shouldReturnEmptyForEmptyFilters() {
            String where = builder.buildWhereClause(
                List.of(), 
                metadata, 
                new HashMap<>(), 
                new AtomicInteger(0)
            );
            
            assertEquals("", where);
        }
    }
    
    @Nested
    @DisplayName("ORDER BY Clause Building")
    class OrderByClauseTests {
        
        @Test
        @DisplayName("Should build single sort clause")
        void shouldBuildSingleSort() {
            String orderBy = builder.buildOrderByClause(
                List.of(SortCriteria.asc("lastName")),
                metadata
            );
            
            assertEquals("last_name ASC", orderBy);
        }
        
        @Test
        @DisplayName("Should build multiple sort clauses")
        void shouldBuildMultipleSorts() {
            String orderBy = builder.buildOrderByClause(
                List.of(
                    SortCriteria.asc("lastName"),
                    SortCriteria.desc("firstName")
                ),
                metadata
            );
            
            assertEquals("last_name ASC, first_name DESC", orderBy);
        }
    }
    
    @Nested
    @DisplayName("Full Query Building")
    class FullQueryTests {
        
        @Test
        @DisplayName("Should build complete query with all parts")
        void shouldBuildCompleteQuery() {
            FilterRequest request = new FilterRequest(
                20, 0, null, null,
                List.of(FilterCriteria.of("firstName", FilterOperator.STARTS_WITH, "Jo")),
                List.of(SortCriteria.asc("lastName")),
                null, User.class, Map.of()
            );
            
            SqlQueryBuilder.QueryResult result = builder.buildQuery(
                "SELECT * FROM users u",
                request,
                metadata
            );
            
            assertTrue(result.sql().contains("WHERE first_name LIKE :p1"));
            assertTrue(result.sql().contains("ORDER BY last_name ASC"));
            assertTrue(result.sql().contains("LIMIT :limit OFFSET :offset"));
            
            assertEquals("Jo%", result.parameters().get("p1"));
            assertEquals(20, result.parameters().get("limit"));
            assertEquals(0, result.parameters().get("offset"));
        }
        
        @Test
        @DisplayName("Should prevent SQL injection via parameterized queries")
        void shouldPreventSqlInjection() {
            // Attempt SQL injection through filter value
            var maliciousFilter = FilterCriteria.of("firstName", FilterOperator.EQ, "'; DROP TABLE users; --");
            
            FilterRequest request = new FilterRequest(
                20, 0, null, null,
                List.of(maliciousFilter),
                List.of(),
                null, User.class, Map.of()
            );
            
            SqlQueryBuilder.QueryResult result = builder.buildQuery(
                "SELECT * FROM users u",
                request,
                metadata
            );
            
            // SQL should NOT contain the injection - value should be parameterized
            assertFalse(result.sql().contains("DROP TABLE"));
            // Value should be safely stored in parameters
            assertEquals("'; DROP TABLE users; --", result.parameters().get("p1"));
        }
    }
}
