# Dynamic Filter POC

A robust, type-safe dynamic filtering system for Spring Framework (non-Boot) applications using Java 21 and Spring JDBC.

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [API Reference](#api-reference)
  - [User API](#user-api)
  - [Deal API (Query Entity Pattern)](#deal-api-query-entity-pattern)
- [Filter Syntax](#filter-syntax)
- [Sort Syntax](#sort-syntax)
- [Curl Examples](#curl-examples)
  - [User API Examples](#user-api-examples)
  - [Deal API Examples](#deal-api-examples)
- [Junction Table Queries](#junction-table-queries)
- [Query Entity Pattern](#query-entity-pattern)
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
- ✅ **Junction Table Support** - Filter on many-to-many relationships (e.g., roleIds)
- ✅ **Query Entity Pattern** - Filter across joined tables (FK relationships, one-to-many)
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

### User API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/users` | List users with filtering, sorting, pagination |
| GET | `/api/v1/users/{id}` | Get a single user by ID |
| GET | `/api/v1/users/metadata/fields` | Get available filterable/sortable fields |

**Filterable Fields:** `userId`, `username`, `firstName`, `lastName`, `roleIds`

> **Note:** `roleIds` is stored in a junction table (`user_roles`). Filtering by roleIds uses a subquery automatically.

### Deal API (Query Entity Pattern)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/deals` | List deals with filtering, sorting, pagination |
| GET | `/api/v1/deals/{id}` | Get a single deal by ID (with all programs) |
| GET | `/api/v1/deals/metadata/fields` | Get available filterable/sortable fields |

**Filterable Fields:**
- Deal fields: `dealId`, `dealName`, `analystId`, `dealStatus`, `dealAmount`
- Analyst fields: `analystName` (from users table via FK)
- Program fields: `programId`, `programName`, `programType`, `programBudget` (from programs table, one-to-many)

> **Note:** The Deal API uses the **Query Entity Pattern** - filtering is done on a flattened view joining deals, users, and programs tables.

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

#### Users Table
| ID | Username | First Name | Last Name | Roles |
|----|----------|------------|-----------|-------|
| 1 | jdoe | John | Doe | ADMIN (1), USER (2) |
| 2 | jsmith | Jane | Smith | USER (2) |
| 3 | bwilson | Bob | Wilson | USER (2), MODERATOR (3) |
| 4 | ajohnson | Alice | Johnson | ADMIN (1), USER (2), MODERATOR (3) |
| 5 | mgarcia | Maria | Garcia | USER (2) |

#### Deals Table
| ID | Deal Name | Analyst | Status | Amount |
|----|-----------|---------|--------|--------|
| 1 | Project Alpha | John Doe | ACTIVE | 1,500,000 |
| 2 | Project Beta | John Doe | ACTIVE | 2,500,000 |
| 3 | Project Gamma | Jane Smith | PENDING | 800,000 |
| 4 | Project Delta | Bob Wilson | ACTIVE | 3,200,000 |
| 5 | Project Epsilon | Alice Johnson | CLOSED | 500,000 |
| 6 | Project Zeta | Jane Smith | ACTIVE | 1,800,000 |
| 7 | Project Eta | (none) | DRAFT | 0 |

#### Programs Table (One-to-Many with Deals)
| Deal | Programs |
|------|----------|
| Project Alpha | Alpha Phase 1 (DEV), Alpha Phase 2 (TEST), Alpha Deployment (DEPLOY) |
| Project Beta | Beta Research (RESEARCH), Beta Development (DEV) |
| Project Gamma | Gamma Pilot (PILOT) |
| Project Delta | Delta Phase 1-2 (DEV), Delta Testing (TEST), Delta Launch (DEPLOY) |
| Project Epsilon | Epsilon Maintenance (MAINTENANCE) |
| Project Zeta | Zeta Analysis (RESEARCH), Zeta Prototype (DEV) |
| Project Eta | (no programs) |

---

## User API Examples

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

### Junction Table Filtering (roleIds)

The `roleIds` field is stored in a junction table (`user_roles`), not directly in the users table.
The system automatically generates subqueries to handle this:

```bash
# Find users with ADMIN role (roleId = 1)
curl "http://localhost:8080/api/v1/users?filter=roleIds:in:(1)"
# Returns: John Doe, Alice Johnson

# Find users with MODERATOR role (roleId = 3)
curl "http://localhost:8080/api/v1/users?filter=roleIds:in:(3)"
# Returns: Bob Wilson, Alice Johnson

# Find users with ADMIN OR MODERATOR role
curl "http://localhost:8080/api/v1/users?filter=roleIds:in:(1,3)"
# Returns: John Doe, Bob Wilson, Alice Johnson

# Find users WITHOUT ADMIN role (NOT IN)
curl "http://localhost:8080/api/v1/users?filter=roleIds:nin:(1)"
# Returns: Jane Smith, Bob Wilson, Maria Garcia

# Combined: ADMIN users whose firstName starts with "J"
curl "http://localhost:8080/api/v1/users?filter=roleIds:in:(1),firstName:sw:J"
# Returns: John Doe
```

**Generated SQL for `roleIds:in:(1,3)`:**
```sql
SELECT u.user_id, u.username, u.first_name, u.last_name
FROM users u
WHERE u.user_id IN (
    SELECT ur.user_id FROM user_roles ur WHERE ur.role_id IN (:roleIds)
)
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

## Deal API Examples

The Deal API demonstrates the **Query Entity Pattern** - filtering across multiple joined tables.

### Basic Deal Queries

```bash
# Get all deals
curl "http://localhost:8080/api/v1/deals"

# Get deal by ID (with all programs)
curl "http://localhost:8080/api/v1/deals/4"

# Get filterable fields
curl "http://localhost:8080/api/v1/deals/metadata/fields"
```

### Filter by Deal Fields

```bash
# Filter by deal status
curl "http://localhost:8080/api/v1/deals?filter=dealStatus:eq:ACTIVE"

# Filter by deal amount (high-value deals)
curl "http://localhost:8080/api/v1/deals?filter=dealAmount:gte:2000000"

# Filter by deal name (starts with)
curl "http://localhost:8080/api/v1/deals?filter=dealName:sw:Project%20A"
```

### Filter by Analyst (FK Relationship)

```bash
# Find deals where analyst name starts with "John"
curl "http://localhost:8080/api/v1/deals?filter=analystName:sw:John"
# Returns: Project Alpha, Project Beta (both assigned to John Doe)

# Find deals where analyst name contains "Smith"
curl "http://localhost:8080/api/v1/deals?filter=analystName:contains:Smith"
# Returns: Project Gamma, Project Zeta (both assigned to Jane Smith)
```

### Filter by Program (One-to-Many Relationship)

```bash
# Find deals that have DEVELOPMENT programs
curl "http://localhost:8080/api/v1/deals?filter=programType:eq:DEVELOPMENT"
# Returns: Alpha, Beta, Delta, Zeta (all have at least one DEVELOPMENT program)

# Find deals that have RESEARCH programs
curl "http://localhost:8080/api/v1/deals?filter=programType:eq:RESEARCH"
# Returns: Beta, Zeta

# Find deals with program name containing "Phase"
curl "http://localhost:8080/api/v1/deals?filter=programName:contains:Phase"
# Returns: Alpha, Delta
```

### Combined Filters (Cross-Table)

```bash
# Deals by Jane Smith with RESEARCH programs
curl "http://localhost:8080/api/v1/deals?filter=analystName:sw:Jane,programType:eq:RESEARCH"
# Returns: Project Zeta

# Active deals with amount >= 2M, sorted by amount desc
curl "http://localhost:8080/api/v1/deals?filter=dealStatus:eq:ACTIVE,dealAmount:gte:2000000&sort=dealAmount:desc"
# Returns: Delta (3.2M), Beta (2.5M)

# DEVELOPMENT deals by John, sorted by deal name
curl "http://localhost:8080/api/v1/deals?filter=analystName:sw:John,programType:eq:DEVELOPMENT&sort=dealName:asc"
```

### Sorting Deals

```bash
# Sort by deal amount descending
curl "http://localhost:8080/api/v1/deals?sort=dealAmount:desc"

# Sort by analyst name, then deal name
curl "http://localhost:8080/api/v1/deals?sort=analystName:asc,dealName:asc"
```

### Pagination

```bash
# First 3 deals
curl "http://localhost:8080/api/v1/deals?limit=3&offset=0"

# Next 3 deals
curl "http://localhost:8080/api/v1/deals?limit=3&offset=3"
```

---

## Junction Table Queries

When filtering on fields stored in junction/mapping tables, the system generates appropriate subqueries.

### User roleIds (Many-to-Many)

**Table Structure:**
```
users (user_id) ←──── user_roles (user_id, role_id) ────► roles (role_id)
```

**Filter:** `roleIds:in:(1,3)`

**Generated SQL:**
```sql
SELECT u.user_id, u.username, u.first_name, u.last_name
FROM users u
WHERE u.user_id IN (
    SELECT ur.user_id 
    FROM user_roles ur 
    WHERE ur.role_id IN (:roleIds)
)
LIMIT :limit OFFSET :offset
```

**Supported Operators for Junction Fields:**
| Operator | SQL Generated |
|----------|---------------|
| `in` | `user_id IN (SELECT... WHERE role_id IN (...))` |
| `nin` | `user_id NOT IN (SELECT... WHERE role_id IN (...))` |
| `eq` | `user_id IN (SELECT... WHERE role_id = ...)` |
| `ne` | `user_id NOT IN (SELECT... WHERE role_id = ...)` |

---

## Query Entity Pattern

For complex queries across multiple tables with FK and one-to-many relationships, we use the **Query Entity Pattern**.

### The Problem

When you have:
- `deals` table with `analyst_id` (FK to users)
- `programs` table with `deal_id` (FK to deals)

You want to filter deals by:
- Analyst name (from users table)
- Program type (from programs table)

### The Solution: Query Entity

**1. Domain Entity** (returned to API):
```java
public record Deal(
    Long dealId,
    String dealName,
    String analystName,      // Denormalized from users
    List<Program> programs   // Nested collection
) {}
```

**2. Query Entity** (flat view for filtering):
```java
public record DealFilterView(
    Long dealId,
    String dealName,
    String analystName,      // From users via JOIN
    Long programId,          // From programs via JOIN
    String programName,
    String programType
) {
    public static final String BASE_SELECT = """
        SELECT d.deal_id, d.deal_name,
               CONCAT(u.first_name, ' ', u.last_name) AS analyst_name,
               p.program_id, p.program_name, p.program_type
        FROM deals d
        LEFT JOIN users u ON d.analyst_id = u.user_id
        LEFT JOIN programs p ON d.deal_id = p.deal_id
        """;
}
```

### How It Works

```
HTTP Request: GET /deals?filter=analystName:sw:John,programType:eq:DEVELOPMENT
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ DealRepository                                                               │
│                                                                              │
│ 1. Build query using DealFilterView (flat)                                   │
│    SELECT ... FROM deals d                                                   │
│    LEFT JOIN users u ON d.analyst_id = u.user_id                            │
│    LEFT JOIN programs p ON d.deal_id = p.deal_id                            │
│    WHERE CONCAT(u.first_name, ' ', u.last_name) LIKE 'John%'                │
│      AND p.program_type = 'DEVELOPMENT'                                      │
│                                                                              │
│ 2. Execute query → Get FLAT rows (duplicates per program)                   │
│    | deal_id | deal_name | analyst_name | program_id | program_name |        │
│    |---------|-----------|--------------|------------|--------------|        │
│    | 1       | Alpha     | John Doe     | 1          | Phase 1      |        │
│    | 1       | Alpha     | John Doe     | 2          | Phase 2      |        │
│    | 2       | Beta      | John Doe     | 4          | Research     |        │
│                                                                              │
│ 3. AGGREGATE: Group flat rows into Deal objects with nested Programs        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
                            │
                            ▼
Response: [
  {
    "dealId": 1,
    "dealName": "Alpha",
    "analystName": "John Doe",
    "programs": [
      {"programId": 1, "programName": "Phase 1"},
      {"programId": 2, "programName": "Phase 2"}
    ]
  },
  ...
]
```

### Key Benefits

1. **Filter on ANY joined field** - analyst name, program type, etc.
2. **No manual subqueries** - JOINs are built into the query entity
3. **Reusable** - SqlQueryBuilder works generically on the flat structure
4. **Clean separation** - Query entity (filtering) vs Domain entity (API response)
5. **Proper pagination** - Paginates on distinct deals, not rows

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
├── user/
│   ├── api/
│   │   └── UserController.java     # REST controller
│   ├── entity/
│   │   └── User.java               # User record with FIELD_* constants
│   ├── repository/
│   │   └── UserRepository.java     # JdbcClient repository (with junction table support)
│   └── service/
│       └── UserService.java        # Service layer
└── deal/                            # Query Entity Pattern example
    ├── api/
    │   └── DealController.java     # REST controller
    ├── entity/
    │   ├── Deal.java               # Domain entity (with nested Programs)
    │   ├── DealFilterView.java     # Query entity (flat view for filtering)
    │   └── Program.java            # Program domain entity
    ├── repository/
    │   └── DealRepository.java     # Repository with JOIN query + aggregation
    └── service/
        └── DealService.java        # Service layer

src/main/resources/
├── application.properties          # Database configuration
├── schema.sql                      # Database schema + sample data (users, deals, programs)
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

### 6. Junction Table Handling

For fields stored in junction tables (like `roleIds`), the repository generates subqueries:

```java
// In UserRepository.buildRoleIdConditions():
switch (operator) {
    case IN -> {
        conditions.add("u.user_id IN (SELECT ur.user_id FROM user_roles ur WHERE ur.role_id IN (:" + paramName + "))");
    }
    case NOT_IN -> {
        conditions.add("u.user_id NOT IN (SELECT ur.user_id FROM user_roles ur WHERE ur.role_id IN (:" + paramName + "))");
    }
}
```

### 7. Query Entity Pattern (for JOINs)

For complex multi-table queries, use a flat **Query Entity** for filtering:

1. **DealFilterView** - Flat record with all filterable fields from joined tables
2. Filter/sort using the flat structure
3. **Aggregate** flat rows back into hierarchical domain objects (Deal with nested Programs)

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

### Adding Junction Table Support

For many-to-many relationships (like User-Roles):

1. **Identify the junction field** in your entity (e.g., `roleIds`)
2. **Add handling in Repository** to generate subqueries:

```java
private String buildJunctionConditions(List<FilterCriteria> junctionFilters, Map<String, Object> params) {
    List<String> conditions = new ArrayList<>();
    
    for (FilterCriteria filter : junctionFilters) {
        if (filter.operator() == FilterOperator.IN) {
            conditions.add("main.id IN (SELECT junction.main_id FROM junction_table junction WHERE junction.related_id IN (:" + paramName + "))");
        }
    }
    
    return String.join(" AND ", conditions);
}
```

3. **Separate junction filters** from standard filters in `findAll()`
4. **Combine conditions** into the final query

### Using the Query Entity Pattern

For filtering across multiple joined tables:

1. **Create Domain Entity** (rich object returned to API):

```java
public record Order(Long orderId, String customerName, List<OrderItem> items) {}
```

2. **Create Query Entity** (flat view for filtering):

```java
public record OrderFilterView(
    Long orderId,
    String customerName,    // From customers table
    Long itemId,            // From order_items table
    String productName      // From products table
) {
    public static final String BASE_SELECT = """
        SELECT o.order_id, c.customer_name, i.item_id, p.product_name
        FROM orders o
        LEFT JOIN customers c ON o.customer_id = c.customer_id
        LEFT JOIN order_items i ON o.order_id = i.order_id
        LEFT JOIN products p ON i.product_id = p.product_id
        """;
}
```

3. **Implement Repository** with aggregation:

```java
public PageResponse<Order> findAll(FilterRequest request) {
    // 1. Query using flat OrderFilterView
    List<OrderFilterView> flatRows = executeFlatQuery(request);
    
    // 2. Aggregate into hierarchical Orders
    List<Order> orders = aggregateToOrders(flatRows);
    
    return PageResponse.of(orders, totalCount, request);
}
```

---

## License

MIT License
