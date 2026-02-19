package com.example.user.entity;

import java.util.List;

/**
 * User entity represented as a Java 21 Record.
 * 
 * <p>Contains public static final constants for field names that are used by
 * the reflection-based metadata utility to validate filter/sort fields.</p>
 * 
 * <p>The constants follow a naming convention: FIELD_{FIELD_NAME} where the value
 * matches the actual record component name and database column mapping.</p>
 */
public record User(
    Long userId,
    String username,
    String firstName,
    String lastName,
    List<Long> roleIds
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // FILTERABLE/SORTABLE FIELD CONSTANTS
    // These constants are discovered via reflection for filter validation
    // ═══════════════════════════════════════════════════════════════════════════
    
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_USERNAME = "username";
    public static final String FIELD_FIRST_NAME = "firstName";
    public static final String FIELD_LAST_NAME = "lastName";
    public static final String FIELD_ROLE_IDS = "roleIds";
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DATABASE COLUMN MAPPINGS
    // Maps Java field names to database column names
    // ═══════════════════════════════════════════════════════════════════════════
    
    public static final String TABLE_NAME = "users";
    
    public static final String COL_USER_ID = "user_id";
    public static final String COL_USERNAME = "username";
    public static final String COL_FIRST_NAME = "first_name";
    public static final String COL_LAST_NAME = "last_name";
    
    // Role IDs stored in separate junction table: user_roles(user_id, role_id)
    public static final String TABLE_USER_ROLES = "user_roles";
    public static final String COL_ROLE_ID = "role_id";
    
    /**
     * Compact constructor for validation.
     */
    public User {
        if (userId != null && userId < 0) {
            throw new IllegalArgumentException("userId cannot be negative");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username cannot be null or blank");
        }
        // Defensive copy for roleIds
        roleIds = roleIds != null ? List.copyOf(roleIds) : List.of();
    }
    
    /**
     * Builder pattern for convenient User construction.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private Long userId;
        private String username;
        private String firstName;
        private String lastName;
        private List<Long> roleIds;
        
        private Builder() {}
        
        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }
        
        public Builder username(String username) {
            this.username = username;
            return this;
        }
        
        public Builder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }
        
        public Builder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }
        
        public Builder roleIds(List<Long> roleIds) {
            this.roleIds = roleIds;
            return this;
        }
        
        public User build() {
            return new User(userId, username, firstName, lastName, roleIds);
        }
    }
}
