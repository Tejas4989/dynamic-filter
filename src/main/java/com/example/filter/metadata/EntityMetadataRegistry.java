package com.example.filter.metadata;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry that extracts and caches entity metadata using reflection.
 * 
 * <p>Discovers filterable fields by scanning for public static final String constants
 * that follow the naming convention: {@code FIELD_*} where the value matches
 * a record component name.</p>
 * 
 * <p>Also discovers column mappings from {@code COL_*} constants.</p>
 * 
 * <p>This class is thread-safe and caches metadata for performance.</p>
 */
public final class EntityMetadataRegistry {
    
    private static final Pattern FIELD_CONSTANT_PATTERN = Pattern.compile("FIELD_(.+)");
    private static final Pattern COL_CONSTANT_PATTERN = Pattern.compile("COL_(.+)");
    private static final String TABLE_NAME_CONSTANT = "TABLE_NAME";
    
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
     * Registers and extracts metadata for an entity class.
     * 
     * @param entityClass the entity class to register
     * @return the extracted metadata
     */
    public EntityMetadata register(Class<?> entityClass) {
        return metadataCache.computeIfAbsent(entityClass, this::extractMetadata);
    }
    
    /**
     * Gets metadata for an entity class if registered.
     */
    public Optional<EntityMetadata> get(Class<?> entityClass) {
        return Optional.ofNullable(metadataCache.get(entityClass));
    }
    
    /**
     * Gets metadata for an entity, registering it if not already present.
     */
    public EntityMetadata getOrRegister(Class<?> entityClass) {
        return register(entityClass);
    }
    
    /**
     * Clears the metadata cache.
     */
    public void clear() {
        metadataCache.clear();
    }
    
    /**
     * Extracts metadata from an entity class using reflection.
     */
    private EntityMetadata extractMetadata(Class<?> entityClass) {
        String tableName = extractTableName(entityClass);
        Map<String, String> columnMappings = extractColumnMappings(entityClass);
        Map<String, FieldMetadata> fields = extractFieldMetadata(entityClass, columnMappings);
        
        return new EntityMetadata(entityClass, tableName, fields);
    }
    
    /**
     * Extracts table name from TABLE_NAME constant or derives from class name.
     */
    private String extractTableName(Class<?> entityClass) {
        try {
            Field tableNameField = entityClass.getDeclaredField(TABLE_NAME_CONSTANT);
            if (isPublicStaticFinalString(tableNameField)) {
                return (String) tableNameField.get(null);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Fall through to derive from class name
        }
        // Convert class name to snake_case (e.g., User -> users, UserRole -> user_roles)
        return toSnakeCase(entityClass.getSimpleName()) + "s";
    }
    
    /**
     * Extracts column mappings from COL_* constants.
     */
    private Map<String, String> extractColumnMappings(Class<?> entityClass) {
        Map<String, String> mappings = new HashMap<>();
        
        for (Field field : entityClass.getDeclaredFields()) {
            if (!isPublicStaticFinalString(field)) {
                continue;
            }
            
            Matcher matcher = COL_CONSTANT_PATTERN.matcher(field.getName());
            if (matcher.matches()) {
                String constantSuffix = matcher.group(1); // e.g., "USER_ID" from "COL_USER_ID"
                try {
                    String columnName = (String) field.get(null);
                    // Convert constant suffix to camelCase field name
                    String fieldName = constantSuffixToFieldName(constantSuffix);
                    mappings.put(fieldName, columnName);
                } catch (IllegalAccessException e) {
                    // Skip inaccessible fields
                }
            }
        }
        
        return mappings;
    }
    
    /**
     * Extracts field metadata from FIELD_* constants and record components.
     */
    private Map<String, FieldMetadata> extractFieldMetadata(Class<?> entityClass, 
                                                             Map<String, String> columnMappings) {
        Map<String, FieldMetadata> fields = new HashMap<>();
        
        // Get record components for type information
        Map<String, Class<?>> componentTypes = extractRecordComponentTypes(entityClass);
        
        // Scan for FIELD_* constants
        for (Field field : entityClass.getDeclaredFields()) {
            if (!isPublicStaticFinalString(field)) {
                continue;
            }
            
            Matcher matcher = FIELD_CONSTANT_PATTERN.matcher(field.getName());
            if (matcher.matches()) {
                try {
                    String fieldName = (String) field.get(null);
                    
                    // Validate that this field exists as a record component
                    Class<?> fieldType = componentTypes.get(fieldName);
                    if (fieldType == null) {
                        // Not a valid record component, skip
                        continue;
                    }
                    
                    // Get column name from mappings or derive from field name
                    String columnName = columnMappings.getOrDefault(fieldName, toSnakeCase(fieldName));
                    
                    // Create field metadata (all discovered fields are filterable and sortable by default)
                    fields.put(fieldName, FieldMetadata.of(fieldName, columnName, fieldType));
                    
                } catch (IllegalAccessException e) {
                    // Skip inaccessible fields
                }
            }
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
    
    /**
     * Checks if a field is public static final String.
     */
    private boolean isPublicStaticFinalString(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isPublic(modifiers)
            && Modifier.isStatic(modifiers)
            && Modifier.isFinal(modifiers)
            && field.getType() == String.class;
    }
    
    /**
     * Converts a constant suffix like "USER_ID" to field name "userId".
     */
    private String constantSuffixToFieldName(String suffix) {
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        
        for (char c : suffix.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }
        
        return result.toString();
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
