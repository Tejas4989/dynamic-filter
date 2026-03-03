package com.example.modelfile.entity;

import java.time.Instant;
import java.util.Objects;

/**
 * ModelFile domain entity.
 *
 * <p>Represents a model file with filterable/sortable fields.
 * Uses full table names (model_files) in column mappings for consistency.</p>
 *
 * @param id          unique identifier
 * @param name        model file name
 * @param type        model type (REGRESSION, CLASSIFICATION, etc.)
 * @param status      status (ACTIVE, PENDING, DEPRECATED, DRAFT)
 * @param dealId      optional FK to deals table
 * @param createdDate creation timestamp
 */
public record ModelFile(
    Long id,
    String name,
    String type,
    String status,
    Long dealId,
    Instant createdDate
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // FILTERABLE/SORTABLE FIELD CONSTANTS
    // Discovered via reflection by EntityMetadataRegistry
    // ═══════════════════════════════════════════════════════════════════════════

    public static final String FIELD_ID = "id";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_DEAL_ID = "dealId";
    public static final String FIELD_CREATED_DATE = "createdDate";

    // ═══════════════════════════════════════════════════════════════════════════
    // COLUMN MAPPINGS (full table names, no aliases)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final String TABLE_NAME = "model_files";

    public static final String COL_ID = "model_files.id";
    public static final String COL_NAME = "model_files.name";
    public static final String COL_TYPE = "model_files.type";
    public static final String COL_STATUS = "model_files.status";
    public static final String COL_DEAL_ID = "model_files.deal_id";
    public static final String COL_CREATED_DATE = "model_files.created_date";

    public ModelFile {
        Objects.requireNonNull(name, "name cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long id;
        private String name;
        private String type;
        private String status;
        private Long dealId;
        private Instant createdDate;

        private Builder() {}

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder dealId(Long dealId) {
            this.dealId = dealId;
            return this;
        }

        public Builder createdDate(Instant createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public ModelFile build() {
            return new ModelFile(id, name, type, status, dealId, createdDate);
        }
    }
}
