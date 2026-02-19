package com.example.filter.model;

import java.util.Optional;

/**
 * Sort direction enumeration.
 */
public enum SortDirection {
    ASC("asc", "ASC"),
    DESC("desc", "DESC");
    
    private final String code;
    private final String sql;
    
    SortDirection(String code, String sql) {
        this.code = code;
        this.sql = sql;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getSql() {
        return sql;
    }
    
    /**
     * Looks up direction by code (case-insensitive).
     */
    public static Optional<SortDirection> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String lower = code.toLowerCase();
        for (SortDirection dir : values()) {
            if (dir.code.equals(lower)) {
                return Optional.of(dir);
            }
        }
        return Optional.empty();
    }
}
