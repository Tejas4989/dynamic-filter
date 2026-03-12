package com.test;

import static com.aig.gi.talbot.merged.model.common.SanitizationUtil.sanitize;
import static java.lang.reflect.Modifier.*;

import com.aig.gi.talbot.merged.model.domain.common.VcapsRuntimeException;
import com.aig.gi.talbot.merged.model.neo.common.validation.ValidationViolation;
import com.aig.gi.talbot.merged.model.neo.generated.web.rest.model.PaginationInfoSort;
import jakarta.validation.ConstraintViolationException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Utility class for handling sort operations in pagination. Converts sortRequest strings (like
 * "createdAt:DESC,username:ASC") into SQL order clauses with proper validation and sanitization.
 */
@Component
public class SortUtil {

  private static final int PART_COUNT = 2;
  // No-Op Sort For SQL Server. Syntactically valid, has no impact on the result
  // order, and it avoids unnecessary index usage or sorting steps.
  private static final String EMPTY_SORT_STATEMENT = "(SELECT NULL)";
  private static final String INVALID_SORT_FIELD_MAPPING_MESSAGE =
      "invalid source to target entity field mapping";
  private static final String INVALID_SORT_FIELD_MESSAGE = "invalid sortRequest field";
  private static final String SORT_REQUEST_FIELD_NAME = "sortRequest";
  private static final String VIOLATION_FIELD_NAME_PATTERN = "sortRequest.%s";
  private static final Set<String> VALID_SORT_DIRECTIONS =
      Arrays.stream(PaginationInfoSort.OrderEnum.values())
          .map(PaginationInfoSort.OrderEnum::getValue)
          .collect(Collectors.toSet());
  // Cache for entity fields to avoid repeated reflection
  private final Map<Class<?>, Set<String>> entityFieldsCache = new ConcurrentHashMap<>();

  /**
   * Builds an SQL ORDER BY clause from a pagination request.
   *
   * @param request PaginationRequest
   * @return SQL ORDER BY clause
   * @throws ConstraintViolationException if validation fails
   * @implNote If no sortRequest string is provided, returns an empty sort statement.
   */
  public String buildOrderClause(PaginationRequest request) {
    return buildOrderClause(request, List.of());
  }

  /**
   * Builds an SQL ORDER BY clause from a pagination request and default sortRequest items.
   *
   * @param request PaginationRequest
   * @param defaultSortItems Default sortRequest items to use if no sortRequest string is provided
   * @return SQL ORDER BY clause
   * @throws ConstraintViolationException if validation fails
   * @implNote If no sortRequest string is provided, returns an empty sort statement.
   */
  public String buildOrderClause(PaginationRequest request, List<SortItem> defaultSortItems) {
    String sortRequest = request.sortRequest();
    Class<?> sortSourceClass = request.sourceClass();
    var sourceClassName = (sortSourceClass != null) ? sortSourceClass.getSimpleName() : "Unknown";
    boolean hasSortRequest = sortRequest != null && !sortRequest.trim().isEmpty();
    boolean hasSourceClass = request.sourceClass() != null;
    boolean hasTargetClass = request.targetClass() != null;
    boolean useDefaultSortItems =
        !hasSortRequest && defaultSortItems != null && !defaultSortItems.isEmpty();

    if (!hasSortRequest && !useDefaultSortItems) {
      return EMPTY_SORT_STATEMENT;
    }

    // Whether we need to transform source fields to target fields
    boolean requireTransformation = !useDefaultSortItems && hasTargetClass;
    var targetFields = requireTransformation ? getValidFields(request.targetClass()) : null;

    var sourceFields =
        extractSourceFields(
            request, useDefaultSortItems, hasTargetClass, hasSourceClass, sortSourceClass);

    Set<ValidationViolation<Object>> violations = new HashSet<>();

    // If no sortRequest string is provided, use default sort items
    var sortItems =
        useDefaultSortItems
            ? defaultSortItems
            : parseSortRequest(Objects.requireNonNull(sortRequest), violations);

    // Process all sort items through validation
    String orderClause =
        sortItems.stream()
            .map(sortItem -> validateSortField(sortItem, sourceFields, violations))
            .filter(Objects::nonNull)
            .map(
                sortItem -> {
                  // If transformation is required, map source fields to target fields
                  if (requireTransformation && request.sourceToTargetEntityFieldMap() != null) {
                    return transformSortItemField(
                        sortItem, targetFields, request.sourceToTargetEntityFieldMap(), violations);
                  }
                  return sortItem;
                })
            .filter(Objects::nonNull)
            .map(sortItem -> "%s %s".formatted(sortItem.field(), sortItem.direction()))
            .collect(Collectors.joining(", "));

    if (!violations.isEmpty()) {
      // In order to mention this only once, attaching it as an instruction when a violation occurs
      if (violations.stream().anyMatch(v -> INVALID_SORT_FIELD_MESSAGE.equals(v.getMessage()))) {
        violations.add(
            new ValidationViolation<>(
                SORT_REQUEST_FIELD_NAME,
                sortRequest,
                "invalid sortRequest field for the resource type %s (must be one of %s)"
                    .formatted(sourceClassName, String.join(", ", sourceFields))));
      }
      throw new ConstraintViolationException(violations);
    }

    return !orderClause.isEmpty() ? orderClause : EMPTY_SORT_STATEMENT;
  }

  private Set<String> extractSourceFields(
      PaginationRequest request,
      boolean useDefaultSortItems,
      boolean hasTargetClass,
      boolean hasSourceClass,
      Class<?> sortSourceClass) {
    return (useDefaultSortItems && hasTargetClass)
        ? getValidFields(request.targetClass())
        : (hasSourceClass ? getValidFields(sortSourceClass) : Collections.emptySet());
  }

  // Transform the sort item field by mapping source fields to target fields if necessary,
  private SortItem transformSortItemField(
      SortItem sortItem,
      Set<String> targetFields,
      Map<String, String> sourceToTargetEntityFieldMap,
      Set<ValidationViolation<Object>> violations) {
    var finalField = sortItem.field();
    var targetField =
        sourceToTargetEntityFieldMap.getOrDefault(
            finalField, targetFields.contains(finalField) ? finalField : null);
    if (targetField == null) {
      var sortItemField = VIOLATION_FIELD_NAME_PATTERN.formatted(sortItem.field());
      violations.add(
          new ValidationViolation<>(sortItemField, finalField, INVALID_SORT_FIELD_MAPPING_MESSAGE));
      return null;
    }
    finalField = targetField;
    return new SortItem(finalField, sortItem.direction());
  }

  /**
   * Extracts a SortItem from parts of a sortRequest string entry.
   *
   * @param parts Parts of a sortRequest string entry (field and direction)
   * @return Optional containing the SortItem if valid, empty otherwise
   */
  public Optional<SortItem> extractSortItem(String[] parts) {
    if (parts.length != PART_COUNT) {
      return Optional.empty();
    }

    // The field information is coming from the clients via API, so we sanitize it
    var field = sanitize(parts[0].trim());
    var direction = parts[1].trim().toUpperCase();

    // If any of the field or direction is empty, return empty
    if (field.isEmpty() || direction.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new SortItem(field, direction));
  }

  // Gets the valid public static final String fields that represent olumn names for an entity
  // class, using a cache for performance
  private Set<String> getValidFields(Class<?> entityClass) {
    return entityFieldsCache.computeIfAbsent(
        entityClass,
        clazz -> {
          Set<String> validFields = new LinkedHashSet<>();

          // Collect public static final String constants
          Arrays.stream(clazz.getDeclaredFields())
              .filter(
                  field ->
                      isPublic(field.getModifiers())
                          && isStatic(field.getModifiers())
                          && isFinal(field.getModifiers())
                          && field.getType().equals(String.class))
              .forEach(
                  field -> {
                    try {
                      validFields.add((String) field.get(null));
                    } catch (IllegalAccessException e) {
                      throw new VcapsRuntimeException(e);
                    }
                  });

          // If no constants found, fallback to private instance fields
          if (validFields.isEmpty()) {
            Arrays.stream(clazz.getDeclaredFields())
                .filter(
                    field ->
                        isPrivate(field.getModifiers())
                            && !isStatic(field.getModifiers())
                            && !isFinal(field.getModifiers()))
                .forEach(field -> validFields.add(field.getName()));
          }

          return validFields;
        });
  }

  // Parses the sortRequest string into a list of SortItem objects, validating format of each item
  private List<SortItem> parseSortRequest(
      String sortRequest, Set<ValidationViolation<Object>> violations) {
    return Arrays.stream(sortRequest.split(","))
        .map(
            entry -> {
              String[] parts = entry.split(":");
              Optional<SortItem> sortItemOpt = extractSortItem(parts);

              if (sortItemOpt.isEmpty()) {
                violations.add(
                    new ValidationViolation<>(
                        SORT_REQUEST_FIELD_NAME, entry, "invalid sort item format"));
              }

              return sortItemOpt.orElse(null);
            })
        .filter(Objects::nonNull)
        .toList();
  }

  // Validates a sortRequest field against valid fields and sortRequest directions
  private SortItem validateSortField(
      SortItem sortItem, Set<String> validFields, Set<ValidationViolation<Object>> violations) {

    if (sortItem == null) {
      violations.add(new ValidationViolation<>(SORT_REQUEST_FIELD_NAME, null, "must not be null"));
      return null;
    }

    boolean hasViolation = false;
    var sortItemField = VIOLATION_FIELD_NAME_PATTERN.formatted(sortItem.field());

    // Validate the field name against the entity's fields
    if (!validFields.contains(sortItem.field())) {
      violations.add(
          new ValidationViolation<>(sortItemField, sortItem.field(), INVALID_SORT_FIELD_MESSAGE));
      hasViolation = true;
    }

    // Validate the direction
    if (!VALID_SORT_DIRECTIONS.contains(sortItem.direction().toUpperCase())) {
      violations.add(
          new ValidationViolation<>(
              sortItemField,
              sortItem.direction(),
              "invalid sortRequest direction (must be one of %s)"
                  .formatted(String.join(", ", VALID_SORT_DIRECTIONS))));
      hasViolation = true;
    }

    return hasViolation ? null : sortItem;
  }
}
