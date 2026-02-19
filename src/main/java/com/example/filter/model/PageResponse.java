package com.example.filter.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Generic page response for paginated queries.
 *
 * @param <T> the element type
 * @param content the page content
 * @param page current page number
 * @param size page size
 * @param totalElements total number of elements
 * @param appliedFilters list of applied filter descriptions
 * @param appliedSorts list of applied sort descriptions
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    List<String> appliedFilters,
    List<String> appliedSorts
) {
    public PageResponse {
        content = content != null ? List.copyOf(content) : List.of();
        appliedFilters = appliedFilters != null ? List.copyOf(appliedFilters) : List.of();
        appliedSorts = appliedSorts != null ? List.copyOf(appliedSorts) : List.of();
    }
    
    /**
     * @return total number of pages
     */
    public int totalPages() {
        return size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
    }
    
    /**
     * @return true if this is the first page
     */
    public boolean isFirst() {
        return page == 0;
    }
    
    /**
     * @return true if this is the last page
     */
    public boolean isLast() {
        return page >= totalPages() - 1;
    }
    
    /**
     * @return true if there's a next page
     */
    public boolean hasNext() {
        return page < totalPages() - 1;
    }
    
    /**
     * @return true if there's a previous page
     */
    public boolean hasPrevious() {
        return page > 0;
    }
    
    /**
     * @return number of elements in this page
     */
    public int numberOfElements() {
        return content.size();
    }
    
    /**
     * Maps the content to a different type.
     */
    public <U> PageResponse<U> map(Function<T, U> mapper) {
        Objects.requireNonNull(mapper);
        List<U> mappedContent = content.stream().map(mapper).toList();
        return new PageResponse<>(mappedContent, page, size, totalElements, appliedFilters, appliedSorts);
    }
    
    /**
     * Creates an empty page response.
     */
    public static <T> PageResponse<T> empty() {
        return new PageResponse<>(List.of(), 0, 20, 0, List.of(), List.of());
    }
    
    /**
     * Builder for convenient construction.
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
    
    public static final class Builder<T> {
        private List<T> content = List.of();
        private int page = 0;
        private int size = 20;
        private long totalElements = 0;
        private List<String> appliedFilters = List.of();
        private List<String> appliedSorts = List.of();
        
        private Builder() {}
        
        public Builder<T> content(List<T> content) {
            this.content = content;
            return this;
        }
        
        public Builder<T> page(int page) {
            this.page = page;
            return this;
        }
        
        public Builder<T> size(int size) {
            this.size = size;
            return this;
        }
        
        public Builder<T> totalElements(long totalElements) {
            this.totalElements = totalElements;
            return this;
        }
        
        public Builder<T> appliedFilters(List<String> appliedFilters) {
            this.appliedFilters = appliedFilters;
            return this;
        }
        
        public Builder<T> appliedSorts(List<String> appliedSorts) {
            this.appliedSorts = appliedSorts;
            return this;
        }
        
        public PageResponse<T> build() {
            return new PageResponse<>(content, page, size, totalElements, appliedFilters, appliedSorts);
        }
    }
}
