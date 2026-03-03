package com.example.modelfile.service;

import com.example.filter.FilterService;
import com.example.filter.model.FilterRequest;
import com.example.filter.model.PageResponse;
import com.example.modelfile.entity.ModelFile;
import com.example.modelfile.repository.ModelFileRepository;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/**
 * Service layer for ModelFile operations.
 *
 * <p>Bridges the Controller and Repository layers, handling
 * filter/sort parsing and validation.</p>
 */
@Service
public class ModelFileService {

    private final ModelFileRepository modelFileRepository;
    private final FilterService filterService;

    public ModelFileService(ModelFileRepository modelFileRepository, FilterService filterService) {
        this.modelFileRepository = modelFileRepository;
        this.filterService = filterService;
    }

    /**
     * Retrieves model files matching the given filter and sort criteria.
     */
    public PageResponse<ModelFile> findModelFiles(String filterString,
                                                   String sortString,
                                                   Integer limit,
                                                   Integer offset) {
        FilterRequest request = filterService.parseAndValidate(
            ModelFile.class,
            filterString,
            sortString,
            limit,
            offset
        );
        return modelFileRepository.findAll(request);
    }

    /**
     * Retrieves a model file by its ID.
     */
    public Optional<ModelFile> findById(Long id) {
        if (id == null || id < 0) {
            return Optional.empty();
        }
        return modelFileRepository.findById(id);
    }

    /**
     * Gets the filterable fields for ModelFile entity.
     */
    public Set<String> getFilterableFields() {
        return filterService.getMetadata(ModelFile.class).getFilterableFields();
    }

    /**
     * Gets the sortable fields for ModelFile entity.
     */
    public Set<String> getSortableFields() {
        return filterService.getMetadata(ModelFile.class).getSortableFields();
    }
}
