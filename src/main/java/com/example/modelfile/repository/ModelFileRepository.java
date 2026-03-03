package com.example.modelfile.repository;

import com.example.filter.FilterService;
import com.example.filter.jdbc.SqlQueryBuilder;
import com.example.filter.metadata.EntityMetadata;
import com.example.filter.model.FilterRequest;
import com.example.filter.model.PageResponse;
import com.example.modelfile.entity.ModelFile;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for ModelFile entity using Spring JdbcClient.
 *
 * <p>Implements dynamic filtering with full SQL injection protection
 * via parameterized queries. Uses SqlQueryBuilder for WHERE/ORDER BY.</p>
 */
@Repository
public class ModelFileRepository {

    private static final String BASE_SELECT = """
        SELECT model_files.id, model_files.name, model_files.type, model_files.status,
               model_files.deal_id, model_files.created_date
        FROM model_files
        """;

    private static final String SELECT_BY_ID = """
        SELECT model_files.id, model_files.name, model_files.type, model_files.status,
               model_files.deal_id, model_files.created_date
        FROM model_files
        WHERE model_files.id = :id
        """;

    private final JdbcClient jdbcClient;
    private final SqlQueryBuilder queryBuilder;
    private final EntityMetadata metadata;

    public ModelFileRepository(JdbcClient jdbcClient, FilterService filterService) {
        this.jdbcClient = jdbcClient;
        this.queryBuilder = SqlQueryBuilder.getInstance();
        this.metadata = filterService.getMetadata(ModelFile.class);
    }

    /**
     * Finds model files matching the filter criteria with pagination.
     */
    public PageResponse<ModelFile> findAll(FilterRequest request) {
        SqlQueryBuilder.QueryResult queryResult = queryBuilder.buildQuery(
            BASE_SELECT.trim(),
            request,
            metadata
        );

        long totalElements = executeCountQuery(queryResult.countSql(), queryResult.parameters());
        List<ModelFile> content = executeQuery(queryResult.sql(), queryResult.parameters());

        return PageResponse.<ModelFile>builder()
            .content(content)
            .page(request.page())
            .size(request.limit())
            .totalElements(totalElements)
            .appliedFilters(request.appliedFiltersAsStrings())
            .appliedSorts(request.appliedSortsAsStrings())
            .build();
    }

    /**
     * Finds a model file by ID.
     */
    public Optional<ModelFile> findById(Long id) {
        List<ModelFile> results = jdbcClient.sql(SELECT_BY_ID)
            .param("id", id)
            .query(this::mapRow)
            .list();

        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    private List<ModelFile> executeQuery(String sql, Map<String, Object> parameters) {
        var spec = jdbcClient.sql(sql);
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }
        return spec.query(this::mapRow).list();
    }

    private long executeCountQuery(String sql, Map<String, Object> parameters) {
        var spec = jdbcClient.sql(sql);
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (!"limit".equals(entry.getKey()) && !"offset".equals(entry.getKey())) {
                spec = spec.param(entry.getKey(), entry.getValue());
            }
        }
        return spec.query(Long.class).single();
    }

    private ModelFile mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdDate = rs.getTimestamp("created_date");
        return ModelFile.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .type(rs.getString("type"))
            .status(rs.getString("status"))
            .dealId(rs.getObject("deal_id") != null ? rs.getLong("deal_id") : null)
            .createdDate(createdDate != null ? createdDate.toInstant() : null)
            .build();
    }
}
