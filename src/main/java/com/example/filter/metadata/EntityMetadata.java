package com.example.filter.metadata;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Holds metadata for an entity including all filterable/sortable fields.
 *
 * @param entityClass the entity class
 * @param tableName the database table name
 * @param fields map of field name to field metadata
 */
public record EntityMetadata(
    Class<?> entityClass,
    String tableName,
    Map<String, FieldMetadata> fields
) {
    public EntityMetadata {
        Objects.requireNonNull(entityClass, "entityClass cannot be null");
        Objects.requireNonNull(tableName, "tableName cannot be null");
        fields = fields != null ? Map.copyOf(fields) : Map.of();
    }
    
    /**
     * Gets metadata for a specific field.
     */
    public Optional<FieldMetadata> getField(String fieldName) {
        return Optional.ofNullable(fields.get(fieldName));
    }
    
    /**
     * Checks if a field is valid for filtering.
     */
    public boolean isFilterableField(String fieldName) {
        return getField(fieldName).map(FieldMetadata::filterable).orElse(false);
    }
    
    /**
     * Checks if a field is valid for sorting.
     */
    public boolean isSortableField(String fieldName) {
        return getField(fieldName).map(FieldMetadata::sortable).orElse(false);
    }
    
    /**
     * Gets the column name for a field.
     */
    public Optional<String> getColumnName(String fieldName) {
        return getField(fieldName).map(FieldMetadata::columnName);
    }
    
    /**
     * Gets all filterable field names.
     */
    public Set<String> getFilterableFields() {
        return fields.entrySet().stream()
            .filter(e -> e.getValue().filterable())
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    
    /**
     * Gets all sortable field names.
     */
    public Set<String> getSortableFields() {
        return fields.entrySet().stream()
            .filter(e -> e.getValue().sortable())
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
