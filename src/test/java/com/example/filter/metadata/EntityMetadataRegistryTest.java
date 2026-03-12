package com.example.filter.metadata;

import com.example.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EntityMetadataRegistry.
 */
class EntityMetadataRegistryTest {

    private static final Map<String, String> USER_FIELD_TO_COLUMN = Map.of(
        "userId", "user_id",
        "username", "username",
        "firstName", "first_name",
        "lastName", "last_name",
        "roleIds", "role_id"
    );

    private EntityMetadataRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = EntityMetadataRegistry.getInstance();
        registry.clear(); // Clear cache before each test
    }
    
    @Test
    @DisplayName("Should extract metadata from User record")
    void shouldExtractUserMetadata() {
        EntityMetadata metadata = registry.register(User.class, USER_FIELD_TO_COLUMN);

        assertNotNull(metadata);
        assertEquals(User.class, metadata.entityClass());
    }
    
    @Test
    @DisplayName("Should discover all FIELD_* constants")
    void shouldDiscoverFieldConstants() {
        EntityMetadata metadata = registry.register(User.class, USER_FIELD_TO_COLUMN);
        
        Set<String> fields = metadata.getFilterableFields();
        
        assertTrue(fields.contains("userId"));
        assertTrue(fields.contains("username"));
        assertTrue(fields.contains("firstName"));
        assertTrue(fields.contains("lastName"));
        assertTrue(fields.contains("roleIds"));
    }
    
    @Test
    @DisplayName("Should map field names to column names")
    void shouldMapColumnNames() {
        EntityMetadata metadata = registry.register(User.class, USER_FIELD_TO_COLUMN);
        
        assertEquals("user_id", metadata.getColumnName("userId").orElse(null));
        assertEquals("username", metadata.getColumnName("username").orElse(null));
        assertEquals("first_name", metadata.getColumnName("firstName").orElse(null));
        assertEquals("last_name", metadata.getColumnName("lastName").orElse(null));
    }
    
    @Test
    @DisplayName("Should extract correct field types")
    void shouldExtractFieldTypes() {
        EntityMetadata metadata = registry.register(User.class, USER_FIELD_TO_COLUMN);
        
        FieldMetadata userId = metadata.getField("userId").orElseThrow();
        FieldMetadata username = metadata.getField("username").orElseThrow();
        FieldMetadata roleIds = metadata.getField("roleIds").orElseThrow();
        
        assertEquals(Long.class, userId.fieldType());
        assertEquals(String.class, username.fieldType());
        assertEquals(List.class, roleIds.fieldType());
    }
    
    @Test
    @DisplayName("Should cache metadata for subsequent calls")
    void shouldCacheMetadata() {
        EntityMetadata first = registry.register(User.class, USER_FIELD_TO_COLUMN);
        EntityMetadata second = registry.register(User.class, USER_FIELD_TO_COLUMN);
        
        assertSame(first, second);
    }
    
    @Test
    @DisplayName("Should validate filterable fields")
    void shouldValidateFilterableFields() {
        EntityMetadata metadata = registry.register(User.class, USER_FIELD_TO_COLUMN);
        
        assertTrue(metadata.isFilterableField("firstName"));
        assertFalse(metadata.isFilterableField("nonExistentField"));
    }
    
    @Test
    @DisplayName("Should validate sortable fields")
    void shouldValidateSortableFields() {
        EntityMetadata metadata = registry.register(User.class, USER_FIELD_TO_COLUMN);
        
        assertTrue(metadata.isSortableField("lastName"));
        assertFalse(metadata.isSortableField("nonExistentField"));
    }

}
