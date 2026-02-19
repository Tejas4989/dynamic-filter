# Dynamic Filter POC

A robust, type-safe dynamic filtering system for Spring Framework (non-Boot) applications using Java 21 and Spring JDBC.

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [API Reference](#api-reference)
- [Filter Syntax](#filter-syntax)
- [Sort Syntax](#sort-syntax)
- [Curl Examples](#curl-examples)
- [Project Structure](#project-structure)
- [How It Works](#how-it-works)
- [Extending the System](#extending-the-system)

---

## Features

- ✅ **Dynamic Filtering** - Filter by any field using various operators
- ✅ **Dynamic Sorting** - Sort by multiple fields with ASC/DESC
- ✅ **Pagination** - Limit/offset based pagination
- ✅ **SQL Injection Protection** - All values bound via parameterized queries
- ✅ **Reflection-based Metadata** - Auto-discovers filterable fields from entity constants
- ✅ **Validation** - Validates filter fields and operators against entity metadata
- ✅ **No ORM Dependencies** - Pure Spring JDBC with JdbcClient
- ✅ **Java 21 Features** - Records, Pattern Matching, modern APIs

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Framework 6.x (non-Boot) |
| Database Access | Spring JDBC (JdbcClient) |
| Database | H2 (in-memory, easily swappable) |
| Server | Embedded Jetty 11 |
| Build | Maven |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        HTTP Request                              │
│         GET /api/v1/users?filter=firstName:sw:J&sort=lastName   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      UserController                              │
│   - Receives filter, sort, limit, offset parameters             │
│   - Delegates to UserService                                     │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                       UserService                                │
│   - Calls FilterService to parse & validate                     │
│   - Passes FilterRequest to Repository                          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      FilterService                               │
│   ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐       │
│   │ FilterParser│  │FilterValidator│  │MetadataRegistry │       │
│   │             │  │              │  │                 │       │
│   │ Parses:     │  │ Validates:   │  │ Extracts:       │       │
│   │ firstName:  │  │ - Field exists│  │ - FIELD_* const│       │
│   │ sw:J        │  │ - Filterable │  │ - COL_* mapping │       │
│   └─────────────┘  └──────────────┘  └─────────────────┘       │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                     UserRepository                               │
│   - Uses SqlQueryBuilder to build parameterized SQL             │
│   - Executes via JdbcClient                                     │
│   - Returns PageResponse<User>                                  │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                     SqlQueryBuilder                              │
│   - Builds WHERE clause with named parameters                   │
│   - Builds ORDER BY clause                                      │
│   - Adds LIMIT/OFFSET pagination                                │
│   - ALL VALUES ARE PARAMETERIZED (SQL Injection Safe)           │
└─────────────────────────────────────────────────────────────────┘
```

---

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+

### Run the Server

```bash
# Clone and navigate to project
cd dynamic-filter-poc

# Compile and run
mvn compile exec:java
```

The server starts on **http://localhost:8080** with sample data.

### Stop the Server

Press `Ctrl+C` or run:
```bash
pkill -f "exec:java"
```

---

## API Reference

### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/users` | List users with filtering, sorting, pagination |
| GET | `/api/v1/users/{id}` | Get a single user by ID |
| GET | `/api/v1/users/metadata/fields` | Get available filterable/sortable fields |

### Query Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `filter` | string | - | Filter criteria (see [Filter Syntax](#filter-syntax)) |
| `sort` | string | - | Sort criteria (see [Sort Syntax](#sort-syntax)) |
| `limit` | integer | 20 | Maximum records to return (max: 100) |
| `offset` | integer | 0 | Number of records to skip |

### Response Format

```json
{
  "content": [...],           // Array of users
  "page": 0,                  // Current page (calculated from offset/limit)
  "size": 20,                 // Page size (limit)
  "totalElements": 100,       // Total matching records
  "appliedFilters": [...],    // Filters that were applied
  "appliedSorts": [...],      // Sorts that were applied
  "first": true,              // Is first page?
  "last": false               // Is last page?
}
```

---

## Filter Syntax

### Format

```
field:operator:value
```

Multiple filters are comma-separated:
```
field1:op1:value1,field2:op2:value2
```

### Supported Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `eq` | Equals | `lastName:eq:Doe` |
| `ne` | Not Equals | `status:ne:inactive` |
| `gt` | Greater Than | `age:gt:18` |
| `gte` | Greater Than or Equal | `age:gte:21` |
| `lt` | Less Than | `price:lt:100` |
| `lte` | Less Than or Equal | `price:lte:50` |
| `sw` | Starts With | `firstName:sw:Jo` |
| `ew` | Ends With | `email:ew:@gmail.com` |
| `contains` | Contains | `description:contains:urgent` |
| `in` | In List | `roleIds:in:(1,2,3)` |
| `nin` | Not In List | `status:nin:(deleted,archived)` |
| `null` | Is Null | `deletedAt:null` |
| `notnull` | Is Not Null | `email:notnull` |

### Operator Compatibility

| Operator | String | Number | Date | Boolean |
|----------|--------|--------|------|---------|
| eq, ne | ✅ | ✅ | ✅ | ✅ |
| gt, gte, lt, lte | ❌ | ✅ | ✅ | ❌ |
| sw, ew, contains | ✅ | ❌ | ❌ | ❌ |
| in, nin | ✅ | ✅ | ✅ | ❌ |
| null, notnull | ✅ | ✅ | ✅ | ✅ |

---

## Sort Syntax

### Format

```
field:direction
```

Multiple sorts are comma-separated:
```
field1:asc,field2:desc
```

### Directions

| Direction | Description |
|-----------|-------------|
| `asc` | Ascending (A-Z, 0-9) |
| `desc` | Descending (Z-A, 9-0) |

If direction is omitted, defaults to `asc`.

---

## Curl Examples

### Sample Data

The database is pre-populated with these users:

| ID | Username | First Name | Last Name | Roles |
|----|----------|------------|-----------|-------|
| 1 | jdoe | John | Doe | ADMIN, USER |
| 2 | jsmith | Jane | Smith | USER |
| 3 | bwilson | Bob | Wilson | USER, MODERATOR |
| 4 | ajohnson | Alice | Johnson | ADMIN, USER, MODERATOR |
| 5 | mgarcia | Maria | Garcia | USER |

---

### Basic Queries

#### Get All Users
```bash
curl http://localhost:8080/api/v1/users
```

#### Get User by ID
```bash
curl http://localhost:8080/api/v1/users/1
```

#### Get Available Fields
```bash
curl http://localhost:8080/api/v1/users/metadata/fields
```

---

### Filtering Examples

#### Equals (eq)
```bash
# Find user with lastName = "Doe"
curl "http://localhost:8080/api/v1/users?filter=lastName:eq:Doe"

# Find user with username = "jsmith"
curl "http://localhost:8080/api/v1/users?filter=username:eq:jsmith"
```

#### Not Equals (ne)
```bash
# Find users where lastName is NOT "Doe"
curl "http://localhost:8080/api/v1/users?filter=lastName:ne:Doe"
```

#### Starts With (sw)
```bash
# Find users whose firstName starts with "J"
curl "http://localhost:8080/api/v1/users?filter=firstName:sw:J"

# Find users whose lastName starts with "Do"
curl "http://localhost:8080/api/v1/users?filter=lastName:sw:Do"
```

#### Ends With (ew)
```bash
# Find users whose lastName ends with "son"
curl "http://localhost:8080/api/v1/users?filter=lastName:ew:son"
```

#### Contains
```bash
# Find users whose firstName contains "oh"
curl "http://localhost:8080/api/v1/users?filter=firstName:contains:oh"

# Find users whose username contains "smith"
curl "http://localhost:8080/api/v1/users?filter=username:contains:smith"
```

#### Greater Than / Less Than (gt, gte, lt, lte)
```bash
# Find users with userId greater than 2
curl "http://localhost:8080/api/v1/users?filter=userId:gt:2"

# Find users with userId less than or equal to 3
curl "http://localhost:8080/api/v1/users?filter=userId:lte:3"
```

#### In List (in)
```bash
# Find users with userId in (1, 3, 5)
curl "http://localhost:8080/api/v1/users?filter=userId:in:(1,3,5)"

# Find users with specific usernames
curl "http://localhost:8080/api/v1/users?filter=username:in:(jdoe,jsmith,mgarcia)"
```

#### Not In List (nin)
```bash
# Find users with userId NOT in (1, 2)
curl "http://localhost:8080/api/v1/users?filter=userId:nin:(1,2)"
```

#### Is Null / Is Not Null
```bash
# Find users where lastName is NOT null
curl "http://localhost:8080/api/v1/users?filter=lastName:notnull"

# Find users where firstName is null (none in sample data)
curl "http://localhost:8080/api/v1/users?filter=firstName:null"
```

---

### Multiple Filters

Filters are combined with AND logic.

```bash
# firstName starts with "J" AND lastName is not null
curl "http://localhost:8080/api/v1/users?filter=firstName:sw:J,lastName:notnull"

# userId > 1 AND userId < 5
curl "http://localhost:8080/api/v1/users?filter=userId:gt:1,userId:lt:5"

# firstName starts with "J" AND lastName equals "Smith"
curl "http://localhost:8080/api/v1/users?filter=firstName:sw:J,lastName:eq:Smith"
```

---

### Sorting Examples

#### Single Field Sort
```bash
# Sort by lastName ascending
curl "http://localhost:8080/api/v1/users?sort=lastName:asc"

# Sort by firstName descending
curl "http://localhost:8080/api/v1/users?sort=firstName:desc"

# Sort by userId descending
curl "http://localhost:8080/api/v1/users?sort=userId:desc"
```

#### Multiple Field Sort
```bash
# Sort by lastName ascending, then firstName descending
curl "http://localhost:8080/api/v1/users?sort=lastName:asc,firstName:desc"

# Sort by lastName, then userId
curl "http://localhost:8080/api/v1/users?sort=lastName:asc,userId:desc"
```

---

### Pagination Examples

#### Basic Pagination
```bash
# First 2 users
curl "http://localhost:8080/api/v1/users?limit=2&offset=0"

# Next 2 users (page 2)
curl "http://localhost:8080/api/v1/users?limit=2&offset=2"

# Last user (page 3)
curl "http://localhost:8080/api/v1/users?limit=2&offset=4"
```

#### Pagination with Sorting
```bash
# First 3 users sorted by lastName
curl "http://localhost:8080/api/v1/users?limit=3&offset=0&sort=lastName:asc"
```

---

### Combined Examples

#### Filter + Sort
```bash
# Users starting with "J", sorted by lastName
curl "http://localhost:8080/api/v1/users?filter=firstName:sw:J&sort=lastName:asc"
```

#### Filter + Sort + Pagination
```bash
# First 2 users with userId > 1, sorted by firstName
curl "http://localhost:8080/api/v1/users?filter=userId:gt:1&sort=firstName:asc&limit=2&offset=0"
```

#### Complex Query
```bash
# Users where:
#   - firstName starts with "J" OR lastName ends with "son"
#   - (Note: current implementation uses AND; OR would require extension)
# Sorted by lastName ascending, then firstName descending
# Limited to 10 results
curl "http://localhost:8080/api/v1/users?filter=firstName:sw:J,lastName:notnull&sort=lastName:asc,firstName:desc&limit=10&offset=0"
```

---

### Error Handling Examples

#### Invalid Filter Field
```bash
curl "http://localhost:8080/api/v1/users?filter=invalidField:eq:test"
# Returns 400 with error message listing valid fields
```

#### Invalid Operator
```bash
curl "http://localhost:8080/api/v1/users?filter=firstName:invalid:test"
# Returns 400 with error message about unknown operator
```

#### Invalid Sort Field
```bash
curl "http://localhost:8080/api/v1/users?sort=invalidField:asc"
# Returns 400 with error message listing valid fields
```

#### User Not Found
```bash
curl http://localhost:8080/api/v1/users/999
# Returns 404
```

---

### Pretty Print JSON

Add `| python3 -m json.tool` for formatted output:

```bash
curl -s "http://localhost:8080/api/v1/users?filter=firstName:sw:J" | python3 -m json.tool
```

Or use `jq` if installed:

```bash
curl -s "http://localhost:8080/api/v1/users?filter=firstName:sw:J" | jq
```

---

## Project Structure

```
src/main/java/com/example/
├── LocalServer.java                 # Embedded Jetty server
├── config/
│   ├── AppConfig.java              # Spring configuration
│   └── DataSourceConfig.java       # DataSource setup
├── filter/
│   ├── FilterService.java          # Main filter facade
│   ├── exception/
│   │   ├── FilterParseException.java
│   │   └── FilterValidationException.java
│   ├── jdbc/
│   │   └── SqlQueryBuilder.java    # Builds parameterized SQL
│   ├── metadata/
│   │   ├── EntityMetadata.java     # Entity metadata container
│   │   ├── EntityMetadataRegistry.java  # Reflection-based extraction
│   │   └── FieldMetadata.java      # Field metadata
│   ├── model/
│   │   ├── FilterCriteria.java     # Filter criterion record
│   │   ├── FilterOperator.java     # Supported operators enum
│   │   ├── FilterRequest.java      # Combined filter/sort/pagination
│   │   ├── PageResponse.java       # Paginated response
│   │   ├── SortCriteria.java       # Sort criterion record
│   │   └── SortDirection.java      # ASC/DESC enum
│   ├── parser/
│   │   └── FilterParser.java       # Parses filter/sort strings
│   └── validation/
│       └── FilterValidator.java    # Validates against metadata
└── user/
    ├── api/
    │   └── UserController.java     # REST controller
    ├── entity/
    │   └── User.java               # User record with FIELD_* constants
    ├── repository/
    │   └── UserRepository.java     # JdbcClient repository
    └── service/
        └── UserService.java        # Service layer

src/main/resources/
├── application.properties          # Database configuration
├── schema.sql                      # Database schema + sample data
└── UserAPI.yaml                    # OpenAPI specification
```

---

## How It Works

### 1. Entity Definition with Constants

```java
public record User(Long userId, String username, String firstName, ...) {
    // These constants are discovered via reflection
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_USERNAME = "username";
    public static final String FIELD_FIRST_NAME = "firstName";
    
    // Column mappings
    public static final String COL_USER_ID = "user_id";
    public static final String COL_FIRST_NAME = "first_name";
}
```

### 2. Metadata Extraction

`EntityMetadataRegistry` uses reflection to:
- Find all `FIELD_*` constants → filterable/sortable fields
- Find all `COL_*` constants → database column mappings
- Cache the metadata for performance

### 3. Filter Parsing

`FilterParser` converts strings like `firstName:sw:Jo` into structured `FilterCriteria` objects.

### 4. Validation

`FilterValidator` checks:
- Field exists in entity metadata
- Field is filterable/sortable
- Operator is compatible with field type

### 5. SQL Generation

`SqlQueryBuilder` generates parameterized SQL:

```sql
SELECT u.user_id, u.username, u.first_name, u.last_name
FROM users u
WHERE first_name LIKE :p1
ORDER BY last_name ASC
LIMIT :limit OFFSET :offset
```

With parameters: `{p1: "Jo%", limit: 20, offset: 0}`

**All values are bound via parameters - never concatenated into SQL!**

---

## Extending the System

### Adding a New Entity

1. **Create the Entity Record**

```java
public record Product(Long productId, String name, BigDecimal price) {
    public static final String FIELD_PRODUCT_ID = "productId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_PRICE = "price";
    
    public static final String TABLE_NAME = "products";
    public static final String COL_PRODUCT_ID = "product_id";
}
```

2. **Create Repository**

```java
@Repository
public class ProductRepository {
    private final JdbcClient jdbcClient;
    private final SqlQueryBuilder queryBuilder;
    private final EntityMetadata metadata;
    
    public ProductRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.queryBuilder = SqlQueryBuilder.getInstance();
        this.metadata = EntityMetadataRegistry.getInstance().register(Product.class);
    }
    
    public PageResponse<Product> findAll(FilterRequest request) {
        // Use SqlQueryBuilder to build query
        // Execute with JdbcClient
    }
}
```

3. **Create Service and Controller**

Follow the same pattern as `UserService` and `UserController`.

### Adding Custom Operators

1. Add to `FilterOperator` enum
2. Update `SqlQueryBuilder.buildCondition()` if needed
3. Update `FilterValidator` if type restrictions apply

---

## License

MIT License
