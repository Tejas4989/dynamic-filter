package com.example.filter.parser;

import com.example.filter.exception.FilterParseException;
import com.example.filter.model.FilterCriteria;
import com.example.filter.model.FilterOperator;
import com.example.filter.model.SortCriteria;
import com.example.filter.model.SortDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FilterParser.
 */
class FilterParserTest {
    
    private FilterParser parser;
    
    @BeforeEach
    void setUp() {
        parser = FilterParser.getInstance();
    }
    
    @Nested
    @DisplayName("Filter Parsing Tests")
    class FilterParsingTests {
        
        @Test
        @DisplayName("Should parse simple equals filter")
        void shouldParseSimpleEqualsFilter() {
            List<FilterCriteria> filters = parser.parseFilters("firstName:eq:John");
            
            assertEquals(1, filters.size());
            FilterCriteria filter = filters.getFirst();
            assertEquals("firstName", filter.field());
            assertEquals(FilterOperator.EQ, filter.operator());
            assertEquals("John", filter.value());
            assertFalse(filter.isMultiValue());
            assertFalse(filter.isNullCheck());
        }
        
        @Test
        @DisplayName("Should parse starts-with filter")
        void shouldParseStartsWithFilter() {
            List<FilterCriteria> filters = parser.parseFilters("firstName:sw:Jo");
            
            assertEquals(1, filters.size());
            FilterCriteria filter = filters.getFirst();
            assertEquals(FilterOperator.STARTS_WITH, filter.operator());
            assertEquals("Jo", filter.value());
        }
        
        @Test
        @DisplayName("Should parse IN clause filter")
        void shouldParseInClauseFilter() {
            List<FilterCriteria> filters = parser.parseFilters("roleIds:in:(1,2,3)");
            
            assertEquals(1, filters.size());
            FilterCriteria filter = filters.getFirst();
            assertEquals("roleIds", filter.field());
            assertEquals(FilterOperator.IN, filter.operator());
            assertEquals(List.of("1", "2", "3"), filter.values());
            assertTrue(filter.isMultiValue());
        }
        
        @Test
        @DisplayName("Should parse IS NULL filter")
        void shouldParseIsNullFilter() {
            List<FilterCriteria> filters = parser.parseFilters("lastName:null");
            
            assertEquals(1, filters.size());
            FilterCriteria filter = filters.getFirst();
            assertEquals("lastName", filter.field());
            assertEquals(FilterOperator.IS_NULL, filter.operator());
            assertTrue(filter.isNullCheck());
        }
        
        @Test
        @DisplayName("Should parse multiple filters")
        void shouldParseMultipleFilters() {
            List<FilterCriteria> filters = parser.parseFilters(
                "firstName:sw:Jo,lastName:eq:Doe,roleIds:in:(1,2)"
            );
            
            assertEquals(3, filters.size());
        }
        
        @Test
        @DisplayName("Should return empty list for null or blank input")
        void shouldReturnEmptyListForNullInput() {
            assertTrue(parser.parseFilters(null).isEmpty());
            assertTrue(parser.parseFilters("").isEmpty());
            assertTrue(parser.parseFilters("   ").isEmpty());
        }
        
        @Test
        @DisplayName("Should throw exception for invalid operator")
        void shouldThrowForInvalidOperator() {
            FilterParseException ex = assertThrows(
                FilterParseException.class,
                () -> parser.parseFilters("firstName:invalid:John")
            );
            assertTrue(ex.getMessage().contains("parsing failed"));
        }
        
        @Test
        @DisplayName("Should throw exception for missing value")
        void shouldThrowForMissingValue() {
            FilterParseException ex = assertThrows(
                FilterParseException.class,
                () -> parser.parseFilters("firstName:eq:")
            );
            assertTrue(ex.getMessage().contains("parsing failed"));
        }
    }
    
    @Nested
    @DisplayName("Sort Parsing Tests")
    class SortParsingTests {
        
        @Test
        @DisplayName("Should parse simple ascending sort")
        void shouldParseAscendingSort() {
            List<SortCriteria> sorts = parser.parseSorts("lastName:asc");
            
            assertEquals(1, sorts.size());
            assertEquals("lastName", sorts.getFirst().field());
            assertEquals(SortDirection.ASC, sorts.getFirst().direction());
        }
        
        @Test
        @DisplayName("Should parse descending sort")
        void shouldParseDescendingSort() {
            List<SortCriteria> sorts = parser.parseSorts("firstName:desc");
            
            assertEquals(1, sorts.size());
            assertEquals(SortDirection.DESC, sorts.getFirst().direction());
        }
        
        @Test
        @DisplayName("Should default to ASC when direction omitted")
        void shouldDefaultToAscending() {
            List<SortCriteria> sorts = parser.parseSorts("lastName");
            
            assertEquals(1, sorts.size());
            assertEquals(SortDirection.ASC, sorts.getFirst().direction());
        }
        
        @Test
        @DisplayName("Should parse multiple sorts")
        void shouldParseMultipleSorts() {
            List<SortCriteria> sorts = parser.parseSorts("lastName:asc,firstName:desc");
            
            assertEquals(2, sorts.size());
            assertEquals("lastName", sorts.get(0).field());
            assertEquals(SortDirection.ASC, sorts.get(0).direction());
            assertEquals("firstName", sorts.get(1).field());
            assertEquals(SortDirection.DESC, sorts.get(1).direction());
        }
        
        @Test
        @DisplayName("Should return empty list for null or blank input")
        void shouldReturnEmptyListForNullInput() {
            assertTrue(parser.parseSorts(null).isEmpty());
            assertTrue(parser.parseSorts("").isEmpty());
        }
    }
}
