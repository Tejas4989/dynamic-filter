package com.example.modelfile.api;

import com.example.filter.model.PageResponse;
import com.example.modelfile.entity.ModelFile;
import com.example.modelfile.service.ModelFileService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * REST Controller for ModelFile API.
 *
 * <p>Provides dynamic filtering and sorting on model files.</p>
 *
 * <p><b>Filterable Fields:</b>
 * <ul>
 *   <li>id, name, type, status, dealId, createdDate</li>
 * </ul>
 *
 * <p><b>Example requests:</b>
 * <ul>
 *   <li>GET /api/v1/model-files - All model files</li>
 *   <li>GET /api/v1/model-files?filter=type:eq:REGRESSION - By type</li>
 *   <li>GET /api/v1/model-files?filter=status:eq:ACTIVE,dealId:eq:1</li>
 *   <li>GET /api/v1/model-files?filter=name:contains:Alpha&sort=createdDate:desc</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/model-files")
public class ModelFileController {

    private final ModelFileService modelFileService;

    public ModelFileController(ModelFileService modelFileService) {
        this.modelFileService = modelFileService;
    }

    /**
     * GET /api/v1/model-files
     *
     * Retrieves model files with optional filtering, sorting, and pagination.
     */
    @GetMapping
    public ResponseEntity<PageResponse<ModelFile>> getModelFiles(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset) {

        PageResponse<ModelFile> response = modelFileService.findModelFiles(filter, sort, limit, offset);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/model-files/{id}
     *
     * Retrieves a single model file by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ModelFile> getModelFileById(@PathVariable Long id) {
        return modelFileService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/model-files/metadata/fields
     *
     * Returns the available filterable and sortable fields.
     */
    @GetMapping("/metadata/fields")
    public ResponseEntity<Map<String, Set<String>>> getFieldMetadata() {
        return ResponseEntity.ok(Map.of(
            "filterableFields", modelFileService.getFilterableFields(),
            "sortableFields", modelFileService.getSortableFields()
        ));
    }
}
