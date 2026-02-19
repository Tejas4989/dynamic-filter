package com.example.user.api;

import com.example.filter.exception.FilterParseException;
import com.example.filter.exception.FilterValidationException;
import com.example.filter.model.PageResponse;
import com.example.user.entity.User;
import com.example.user.service.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * REST Controller for User API.
 * 
 * <p>Implements the endpoints defined in UserAPI.yaml with dynamic
 * filtering and sorting capabilities.</p>
 * 
 * <p>Example requests:
 * <ul>
 *   <li>GET /api/v1/users?filter=firstName:sw:Jo&sort=lastName:asc</li>
 *   <li>GET /api/v1/users?filter=roleIds:in:(1,2,3)&page=0&size=10</li>
 *   <li>GET /api/v1/users/123</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    
    private final UserService userService;
    
    public UserController(UserService userService) {
        this.userService = userService;
    }
    
    /**
     * GET /api/v1/users
     * 
     * Retrieves users with optional filtering, sorting, and pagination.
     *
     * @param filter filter string (e.g., "firstName:sw:Jo,lastName:eq:Doe")
     * @param sort sort string (e.g., "lastName:asc,firstName:desc")
     * @param limit maximum records to return (default 20, max 100)
     * @param offset number of records to skip (default 0)
     * @return paginated list of users
     */
    @GetMapping
    public ResponseEntity<PageResponse<User>> getUsers(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset) {
        
        PageResponse<User> response = userService.findUsers(filter, sort, limit, offset);
        return ResponseEntity.ok(response);
    }
    
    /**
     * GET /api/v1/users/{userId}
     * 
     * Retrieves a single user by ID.
     *
     * @param userId the user ID
     * @return the user if found, 404 otherwise
     */
    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserById(@PathVariable Long userId) {
        return userService.findById(userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * GET /api/v1/users/metadata/fields
     * 
     * Returns the available filterable and sortable fields.
     * Useful for client-side filter builders.
     */
    @GetMapping("/metadata/fields")
    public ResponseEntity<Map<String, Set<String>>> getFieldMetadata() {
        return ResponseEntity.ok(Map.of(
            "filterableFields", userService.getFilterableFields(),
            "sortableFields", userService.getSortableFields()
        ));
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EXCEPTION HANDLERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Handles filter parsing errors.
     */
    @ExceptionHandler(FilterParseException.class)
    public ResponseEntity<ErrorResponse> handleFilterParseException(FilterParseException ex) {
        ErrorResponse error = new ErrorResponse(
            Instant.now().toString(),
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            "/api/v1/users",
            ex.getErrors()
        );
        return ResponseEntity.badRequest().body(error);
    }
    
    /**
     * Handles filter validation errors.
     */
    @ExceptionHandler(FilterValidationException.class)
    public ResponseEntity<ErrorResponse> handleFilterValidationException(FilterValidationException ex) {
        ErrorResponse error = new ErrorResponse(
            Instant.now().toString(),
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            "/api/v1/users",
            ex.getErrors()
        );
        return ResponseEntity.badRequest().body(error);
    }
    
    /**
     * Error response DTO matching OpenAPI spec.
     */
    public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path,
        java.util.List<String> details
    ) {}
}
