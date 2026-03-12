package com.test;

import jakarta.validation.constraints.*;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * PaginationRequest is a record that encapsulates pagination parameters including limit, offset,
 * and sorting criteria. It allows for pagination of results with optional sorting capabilities.
 */
public record PaginationRequest(
    @NotNull @Min(1) @Max(100) Integer limit,
    @NotNull @Min(0) Integer offset,
    @Nullable
        @Size(max = 1000)
        @Pattern(
            regexp = "^([a-zA-Z0-9_]+:(ASC|DESC|asc|desc))(,[a-zA-Z0-9_]+:(ASC|DESC|asc|desc))*$",
            message = "Sort must follow the format field:ORDER, where ORDER is ASC or DESC.")
        String sortRequest,
    @Nullable
        @Size(max = 2000)
        @Pattern(
            regexp =
                "^([a-zA-Z0-9_]+(:(eq|ne|gt|gte|lt|lte|sw|ew|contains|in|nin|null|notnull|EQ|NE|GT|GTE|LT|LTE|SW|EW|CONTAINS|IN|NIN|NULL|NOTNULL):([^,:]*)?))(,[a-zA-Z0-9_]+(:(eq|ne|gt|gte|lt|lte|sw|ew|contains|in|nin|null|notnull|EQ|NE|GT|GTE|LT|LTE|SW|EW|CONTAINS|IN|NIN|NULL|NOTNULL):([^,:]*)?)){0,}$",
            message =
                "Filter must follow the format field:OPERATOR:value, where OPERATOR is one of: eq, ne, gt, gte, lt, lte, sw, ew, contains, in, nin, null, notnull.")
        String filterRequest,
    @Nullable Class<?> sourceClass,
    @Nullable Class<?> targetClass,
    @Nullable Map<String, String> sourceToTargetEntityFieldMap) {

  private static final Integer DEFAULT_LIMIT = 20;
  private static final Integer DEFAULT_OFFSET = 0;


    /**
     * Pagination with sorting capability and transformation for the referenced fields in the
     * sortQuery.
     *
     * @param limit Number of records per page. Assigned a default value when null.
     * @param offset Starting index for the current page. Assigned a default value when null.
     * @param sortRequest Sorting criteria in the format field:ORDER, where ORDER is ASC or DESC.
     *     Multiple fields can be specified, separated by commas. Example: "field1:ASC,field2:DESC".
     *     The fields in the sortRequest should be present in the sourceClass. The entries in the
     *     sourceToTargetEntityFieldMap is used to transform the fields from the source entity to the
     *     target entity. Example: if sortRequest is "countryName:ASC", and the
     *     sourceToTargetEntityFieldMap maps "name" to "clientName", then the sort will be applied on
     *     "clientName" in the target entity.
     * @param filterRequest Filtering criteria in the format field:OPERATOR:value, where OPERATOR is
     *     one of: eq, ne, gt, gte, lt, lte, sw, ew, contains, in, nin, null, notnull.
     * @param sortSourceClass Class of the source entity for sorting. This is used to determine the
     *     fields. The fields in the sortRequest should be present in this class.
     * @param sortTargetClass Class of the target entity for sorting. This is used to determine the
     *     fields.
     * @param sourceToTargetEntityFieldMap Map to transform fields from source entity to target
     *     entity.
     * @return PaginationRequest with default values for limit and offset, and sorting criteria
     *     utilizing field transformation.
     */
    public static PaginationRequest of(
            @Nullable Integer limit,
            @Nullable Integer offset,
            @Nullable String sortRequest,
            @Nullable Class<?> sortSourceClass,
            @Nullable Class<?> sortTargetClass,
            @Nullable Map<String, String> sourceToTargetEntityFieldMap) {
        return new PaginationRequest(
                limit != null ? limit : DEFAULT_LIMIT,
                offset != null ? offset : DEFAULT_OFFSET,
                sortRequest,
                null,
                sortSourceClass,
                sortTargetClass,
                sourceToTargetEntityFieldMap);
    }

  /**
   * Pagination with sorting capability and transformation for the referenced fields in the
   * sortQuery.
   *
   * @param limit Number of records per page. Assigned a default value when null.
   * @param offset Starting index for the current page. Assigned a default value when null.
   * @param sortRequest Sorting criteria in the format field:ORDER, where ORDER is ASC or DESC.
   *     Multiple fields can be specified, separated by commas. Example: "field1:ASC,field2:DESC".
   *     The fields in the sortRequest should be present in the sourceClass. The entries in the
   *     sourceToTargetEntityFieldMap is used to transform the fields from the source entity to the
   *     target entity. Example: if sortRequest is "countryName:ASC", and the
   *     sourceToTargetEntityFieldMap maps "name" to "clientName", then the sort will be applied on
   *     "clientName" in the target entity.
   * @param filterRequest Filtering criteria in the format field:OPERATOR:value, where OPERATOR is
   *     one of: eq, ne, gt, gte, lt, lte, sw, ew, contains, in, nin, null, notnull.
   * @param sortSourceClass Class of the source entity for sorting. This is used to determine the
   *     fields. The fields in the sortRequest should be present in this class.
   * @param sortTargetClass Class of the target entity for sorting. This is used to determine the
   *     fields.
   * @param sourceToTargetEntityFieldMap Map to transform fields from source entity to target
   *     entity.
   * @return PaginationRequest with default values for limit and offset, and sorting criteria
   *     utilizing field transformation.
   */
  public static PaginationRequest of(
      @Nullable Integer limit,
      @Nullable Integer offset,
      @Nullable String sortRequest,
      @Nullable String filterRequest,
      @Nullable Class<?> sortSourceClass,
      @Nullable Class<?> sortTargetClass,
      @Nullable Map<String, String> sourceToTargetEntityFieldMap) {
    return new PaginationRequest(
        limit != null ? limit : DEFAULT_LIMIT,
        offset != null ? offset : DEFAULT_OFFSET,
        sortRequest,
        filterRequest,
        sortSourceClass,
        sortTargetClass,
        sourceToTargetEntityFieldMap);
  }

  /**
   * Pagination with sorting capability without the need for a transformation for the referenced
   * fields in the sortQuery.
   *
   * @param limit Number of records per page. Assigned a default value when null.
   * @param offset Starting index for the current page. Assigned a default value when null.
   * @param sortRequest Sorting criteria in the format field:ORDER, where ORDER is ASC or DESC.
   *     Multiple fields cwan be specified, separated by commas. Example: "field1:ASC,field2:DESC".
   * @param sortSourceClass Class of the source entity for sorting. This is used to determine the
   *     fields.
   * @return PaginationRequest with default values for limit and offset, and sorting criteria.
   */
  public static PaginationRequest of(
      @Nullable Integer limit,
      @Nullable Integer offset,
      @Nullable String sortRequest,
      @Nullable Class<?> sortSourceClass) {
    return PaginationRequest.of(limit, offset, sortRequest, null, sortSourceClass, null, null);
  }

  /**
   * Pagination is only performed using limit and offset without providing any sorting capability.
   *
   * @param limit Number of records per page. Assigned a default value when null.
   * @param offset Starting index for the current page. Assigned a default value when null.
   * @return PaginationRequest with default values for limit and offset, and no sorting.
   */
  public static PaginationRequest of(@Nullable Integer limit, @Nullable Integer offset) {
    return PaginationRequest.of(limit, offset, null, null, null, null, null);
  }
}
