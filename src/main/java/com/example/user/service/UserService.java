package com.example.user.service;

import com.example.filter.FilterService;
import com.example.filter.model.FilterRequest;
import com.example.filter.model.PageResponse;
import com.example.user.entity.User;
import com.example.user.repository.UserRepository;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service layer for User operations.
 * 
 * <p>Bridges the Controller and Repository layers, handling:
 * <ul>
 *   <li>Filter/sort parsing and validation</li>
 *   <li>Business logic (if any)</li>
 *   <li>Transaction management (if needed)</li>
 * </ul>
 */
@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final FilterService filterService;
    
    public UserService(UserRepository userRepository, FilterService filterService) {
        this.userRepository = userRepository;
        this.filterService = filterService;
    }
    
    /**
     * Retrieves users matching the given filter and sort criteria.
     *
     * @param filterString the filter query string (e.g., "firstName:sw:Jo")
     * @param sortString the sort query string (e.g., "lastName:asc")
     * @param limit maximum records to return
     * @param offset number of records to skip
     * @return a page of users
     */
    public PageResponse<User> findUsers(String filterString, 
                                        String sortString,
                                        Integer limit,
                                        Integer offset) {
        // Create and parse/validate the filter request
        FilterRequest request = filterService.parseAndValidate(
            User.class,
            filterString,
            sortString,
            limit,
            offset
        );
        
        // Delegate to repository
        return userRepository.findAll(request);
    }
    
    /**
     * Retrieves a user by their ID.
     *
     * @param userId the user ID
     * @return the user if found
     */
    public Optional<User> findById(Long userId) {
        if (userId == null || userId < 0) {
            return Optional.empty();
        }
        return userRepository.findById(userId);
    }
    
    /**
     * Gets the filterable fields for User entity.
     * Useful for API documentation or discovery endpoints.
     */
    public java.util.Set<String> getFilterableFields() {
        return filterService.getMetadata(User.class).getFilterableFields();
    }
    
    /**
     * Gets the sortable fields for User entity.
     */
    public java.util.Set<String> getSortableFields() {
        return filterService.getMetadata(User.class).getSortableFields();
    }
}
