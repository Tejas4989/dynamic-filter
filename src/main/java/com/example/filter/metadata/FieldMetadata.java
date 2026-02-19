package com.example.filter.metadata;

import java.util.Objects;

/**
 * Metadata for a filterable/sortable field.
 *
 * @param fieldName the Java field name (used in filter expressions)
 * @param columnName the database column name
 * @param fieldType the Java type of the field
 * @param filterable whether this field can be filtered
 * @param sortable whether this field can be sorted
 */
public record FieldMetadata(
    String fieldName,
    String columnName,
    Class<?> fieldType,
    boolean filterable,
    boolean sortable
) {
    public FieldMetadata {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        Objects.requireNonNull(columnName, "columnName cannot be null");
        Objects.requireNonNull(fieldType, "fieldType cannot be null");
    }
    
    /**
     * Creates a field metadata that is both filterable and sortable.
     */
    public static FieldMetadata of(String fieldName, String columnName, Class<?> fieldType) {
        return new FieldMetadata(fieldName, columnName, fieldType, true, true);
    }
    
    /**
     * Creates a field metadata with specified filter/sort capabilities.
     */
    public static FieldMetadata of(String fieldName, String columnName, Class<?> fieldType, 
                                   boolean filterable, boolean sortable) {
        return new FieldMetadata(fieldName, columnName, fieldType, filterable, sortable);
    }
    
    /**
     * Checks if the field type is numeric.
     */
    public boolean isNumeric() {
        return Number.class.isAssignableFrom(fieldType) 
            || fieldType == int.class 
            || fieldType == long.class 
            || fieldType == double.class 
            || fieldType == float.class;
    }
    
    /**
     * Checks if the field type is a String.
     */
    public boolean isString() {
        return String.class.isAssignableFrom(fieldType);
    }
    
    /**
     * Checks if the field is a collection type.
     */
    public boolean isCollection() {
        return java.util.Collection.class.isAssignableFrom(fieldType);
    }
}
