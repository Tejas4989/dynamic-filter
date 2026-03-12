package com.example.filter.metadata;

import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that builds and caches entity metadata from an explicit field-to-column mapping.
 *
 * <p>Uses a {@code Map<String, String>} to map DTO field names to entity table column names.
 * Field types are still resolved from the entity class (record components) when available.</p>
 *
 * <p>This class is thread-safe and caches metadata for performance.</p>
 */
public final class EntityMetadataRegistry {

    // Thread-safe cache for entity metadata
    private final ConcurrentHashMap<Class<?>, EntityMetadata> metadataCache = new ConcurrentHashMap<>();
    
    // Singleton instance
    private static final EntityMetadataRegistry INSTANCE = new EntityMetadataRegistry();
    
    private EntityMetadataRegistry() {}
    
    /**
     * Gets the singleton instance.
     */
    public static EntityMetadataRegistry getInstance() {
        return INSTANCE;
    }
    
    /**
     * Registers entity metadata using an explicit field-to-column mapping.
     *
     * @param entityClass          the entity/DTO class
     * @param fieldToColumnMapping map of DTO field name to database column name (must not be null)
     * @return the built metadata
     */
    public EntityMetadata register(Class<?> entityClass, Map<String, String> fieldToColumnMapping) {
        Objects.requireNonNull(fieldToColumnMapping, "fieldToColumnMapping cannot be null");
        Map<String, String> copy = Map.copyOf(fieldToColumnMapping);
        return metadataCache.computeIfAbsent(entityClass, c -> buildMetadata(c, copy));
    }

    /**
     * Gets metadata for an entity class if registered.
     */
    public Optional<EntityMetadata> get(Class<?> entityClass) {
        return Optional.ofNullable(metadataCache.get(entityClass));
    }

    /**
     * Gets metadata for an entity. Must be registered first via
     * {@link #register(Class, Map)}.
     *
     * @throws IllegalStateException if the entity has not been registered
     */
    public EntityMetadata getOrRegister(Class<?> entityClass) {
        EntityMetadata metadata = metadataCache.get(entityClass);
        if (metadata == null) {
            throw new IllegalStateException(
                    "Entity " + entityClass.getName() + " is not registered. "
                            + "Call register(entityClass, fieldToColumnMapping) first.");
        }
        return metadata;
    }
    
    /**
     * Clears the metadata cache.
     */
    public void clear() {
        metadataCache.clear();
    }
    
    /**
     * Builds metadata from the entity class and field-to-column mapping.
     */
    private EntityMetadata buildMetadata(Class<?> entityClass, Map<String, String> fieldToColumnMapping) {
        Map<String, FieldMetadata> fields = buildFieldMetadata(entityClass, fieldToColumnMapping);
        return new EntityMetadata(entityClass, fields);
    }

    /**
     * Builds field metadata from the explicit field-to-column mapping.
     * Field types are resolved from record components when the entity is a record.
     */
    private Map<String, FieldMetadata> buildFieldMetadata(Class<?> entityClass,
                                                          Map<String, String> fieldToColumnMapping) {
        Map<String, FieldMetadata> fields = new HashMap<>();
        Map<String, Class<?>> componentTypes = extractRecordComponentTypes(entityClass);

        for (Map.Entry<String, String> entry : fieldToColumnMapping.entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue();

            // Resolve field type from record components when available
            Class<?> fieldType = componentTypes.get(fieldName);
            if (fieldType == null) {
                fieldType = Object.class;
            }

            fields.put(fieldName, FieldMetadata.of(fieldName, columnName, fieldType));
        }

        return fields;
    }
    
    /**
     * Extracts record component names and their types.
     */
    private Map<String, Class<?>> extractRecordComponentTypes(Class<?> entityClass) {
        Map<String, Class<?>> types = new HashMap<>();
        
        if (entityClass.isRecord()) {
            for (RecordComponent component : entityClass.getRecordComponents()) {
                Class<?> type = component.getType();
                // For parameterized types like List<Long>, still use the raw type
                types.put(component.getName(), type);
            }
        }
        
        return types;
    }
}
