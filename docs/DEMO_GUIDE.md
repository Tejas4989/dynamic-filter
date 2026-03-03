# Dynamic Filter POC — Demo Guide

**Purpose:** Technical walkthrough for demonstrating the Dynamic Filter POC. Use this document as your demo script and reference to ensure you cover all key points.

---

## 1. Executive Summary (30 seconds)

**What we built:** A reusable, type-safe dynamic filtering system for REST APIs. Clients pass filter/sort strings as query params; the system parses, validates, and builds parameterized SQL—**with zero changes to core filter logic when adding new APIs**.

**Key numbers:**
- **3 APIs** with dynamic filtering: Users, Deals, ModelFiles
- **~15 shared classes** in the filter package—**never modified** when adding new endpoints
- **5 files** to add for a new single-table API (or **6** for multi-table with joins)
- **100% parameterized queries** — SQL injection safe

---

## 2. Core Value: Reusability (Key Talking Point)

### What You Do NOT Touch

When adding a new filterable API, you **never modify** these components:

| Component | Location | Responsibility |
|-----------|----------|----------------|
| **FilterService** | `filter/FilterService.java` | Parse + validate filter strings; get entity metadata |
| **FilterParser** | `filter/parser/FilterParser.java` | Parse `field:op:value` syntax into `FilterCriteria` |
| **FilterValidator** | `filter/validation/FilterValidator.java` | Validate fields exist, operators match field types |
| **SqlQueryBuilder** | `filter/jdbc/SqlQueryBuilder.java` | Build WHERE, ORDER BY, LIMIT/OFFSET with named params |
| **EntityMetadataRegistry** | `filter/metadata/EntityMetadataRegistry.java` | Extract FIELD_* / COL_* via reflection |
| **FilterExceptionHandler** | `api/FilterExceptionHandler.java` | Global exception handling for filter errors |
| **FilterRequest, PageResponse** | `filter/model/*` | Request/response DTOs |
| **FilterOperator, SortDirection** | `filter/model/*` | Enums for operators and sort direction |

**Total: ~15 classes in the filter package — all reusable, zero changes for new APIs.**

### What You Add (Per New API)

Only **entity-specific** code. See [Section 6](#6-files-to-add-for-a-new-api) for exact counts.

---

## 3. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  HTTP: GET /api/v1/users?filter=firstName:sw:J&sort=lastName:asc&limit=10   │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Controller (UserController / DealController / ModelFileController)          │
│  • Receives filter, sort, limit, offset                                     │
│  • Delegates to Service                                                      │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Service (UserService / DealService / ModelFileService)                     │
│  • Calls FilterService.parseAndValidate(entityClass, filter, sort, ...)     │
│  • Passes FilterRequest to Repository                                        │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  FilterService (SHARED — never modified)                                    │
│  • FilterParser.parseFilters() → List<FilterCriteria>                       │
│  • FilterValidator.validateFilters() → throws if invalid                    │
│  • EntityMetadataRegistry.getOrRegister(entityClass) → metadata             │
│  • Returns FilterRequest with parsed criteria                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Repository (UserRepository / DealRepository / ModelFileRepository)         │
│  • SqlQueryBuilder.buildQuery(BASE_SELECT, request, metadata)               │
│  • Executes parameterized SQL via JdbcClient                                │
│  • Returns PageResponse<T>                                                 │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  SqlQueryBuilder (SHARED — never modified)                                  │
│  • buildWhereClause(filters, metadata) → "col1 = :p1 AND col2 LIKE :p2"     │
│  • buildOrderByClause(sorts, metadata) → "col1 ASC, col2 DESC"              │
│  • All values bound as named parameters (:p1, :p2, ...)                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. How Metadata Discovery Works (Technical Deep Dive)

**Entity metadata is the bridge.** No configuration files—just Java constants.

### Convention: FIELD_* and COL_*

```java
public record ModelFile(Long id, String name, String type, String status, Long dealId, Instant createdDate) {
    // FIELD_* — value must match record component name exactly
    public static final String FIELD_ID = "id";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_DEAL_ID = "dealId";
    public static final String FIELD_CREATED_DATE = "createdDate";

    // COL_* — maps to SQL column (table.column or expression)
    public static final String COL_ID = "model_files.id";
    public static final String COL_NAME = "model_files.name";
    public static final String COL_TYPE = "model_files.type";
    public static final String COL_STATUS = "model_files.status";
    public static final String COL_DEAL_ID = "model_files.deal_id";
    public static final String COL_CREATED_DATE = "model_files.created_date";
}
```

### EntityMetadataRegistry (Reflection)

1. Scans `FIELD_*` constants → field names (e.g., `id`, `name`)
2. Scans `COL_*` constants → field → column mapping (e.g., `id` → `model_files.id`)
3. Uses record components for type info (Long, String, Instant, etc.)
4. Caches per class (thread-safe)

**Result:** Filter string `name:contains:Alpha` → validated against `name` → resolved to `model_files.name` SQL column → `model_files.name LIKE :p1` with `%Alpha%` bound.

---

## 5. Three API Patterns Demonstrated

### Pattern A: Single-Table (User, ModelFile)

- **Entity** = domain object with FIELD_* / COL_*
- **Repository** uses `SqlQueryBuilder.buildQuery()` directly
- **No aggregation** — 1 row = 1 entity

### Pattern B: Multi-Table with Joins (Deal)

- **DealFilterView** = flat record with fields from joined tables (deals, users, programs, contracts)
- **Repository** builds query from DealFilterView; produces multiple rows per deal
- **Aggregation** — groups flat rows into hierarchical Deal → Program → Contract

### Pattern C: Junction Table (User roleIds)

- **roleIds** stored in `user_roles` table
- **Repository** splits filters: standard → SqlQueryBuilder; roleIds → custom subquery
- **Result:** `u.user_id IN (SELECT user_id FROM user_roles WHERE role_id IN (:p1))`

---

## 6. Files to Add for a New API

### Single-Table (e.g., ModelFile)

| # | File | Purpose |
|---|------|---------|
| 1 | `schema.sql` (additions) | Table definition + sample data |
| 2 | `entity/<Entity>.java` | Domain record + FIELD_* / COL_* constants |
| 3 | `repository/<Entity>Repository.java` | JDBC + SqlQueryBuilder integration |
| 4 | `service/<Entity>Service.java` | Parse/validate + delegate to repository |
| 5 | `api/<Entity>Controller.java` | REST endpoints |

**Total: 5 files** (1 schema edit + 4 new classes)

### Multi-Table with Joins (e.g., Deal)

| # | File | Purpose |
|---|------|---------|
| 1 | `schema.sql` (additions) | Tables + sample data |
| 2 | `entity/<Entity>FilterView.java` | Flat query record for filtering |
| 3 | `entity/<Entity>.java` | Domain entity (with nested objects) |
| 4 | `entity/<Nested>.java` | Nested entities (e.g., Program, Contract) |
| 5 | `repository/<Entity>Repository.java` | JDBC + SqlQueryBuilder + aggregation |
| 6 | `service/<Entity>Service.java` | Parse/validate + delegate |
| 7 | `api/<Entity>Controller.java` | REST endpoints |

**Total: 7 files** (1 schema edit + 6+ new classes; nested entities add more)

### Summary

| Scenario | New Files | Schema Edits |
|----------|-----------|--------------|
| Single-table API | 4 classes | 1 (schema.sql) |
| Multi-table API | 6+ classes | 1 (schema.sql) |

**No changes to filter package. No changes to FilterExceptionHandler.**

---

## 7. Filter Syntax Reference

### Format

```
field:operator:value
```

### Operators

| Operator | Code | Example | SQL |
|----------|------|---------|-----|
| Equals | `eq` | `status:eq:ACTIVE` | `= :p1` |
| Not equals | `ne` | `type:ne:DRAFT` | `!= :p1` |
| Greater than | `gt` | `dealAmount:gt:1000000` | `> :p1` |
| Greater or equal | `gte` | `id:gte:5` | `>= :p1` |
| Less than | `lt` | `budget:lt:500000` | `< :p1` |
| Less or equal | `lte` | `userId:lte:10` | `<= :p1` |
| Starts with | `sw` | `name:sw:Alpha` | `LIKE 'Alpha%'` |
| Ends with | `ew` | `name:ew:Model` | `LIKE '%Model'` |
| Contains | `contains` | `name:contains:Phase` | `LIKE '%Phase%'` |
| In list | `in` | `status:in:(ACTIVE,PENDING)` | `IN (:p1)` |
| Not in list | `nin` | `id:nin:(1,2,3)` | `NOT IN (:p1)` |
| Is null | `null` | `dealId:null` | `IS NULL` |
| Is not null | `notnull` | `name:notnull` | `IS NOT NULL` |

### Sort

```
field:asc   or   field:desc
```

### Multiple

Comma-separated (parentheses preserved for IN values):

```
filter=status:eq:ACTIVE,type:eq:REGRESSION,dealId:in:(1,2,3)
sort=createdDate:desc,name:asc
```

---

## 8. Demo Flow (Suggested Order)

### Part 1: Show It Works (2 min)

```bash
# Start server
mvn compile exec:java

# Single-table: ModelFile
curl "http://localhost:8080/api/v1/model-files?filter=type:eq:REGRESSION"
curl "http://localhost:8080/api/v1/model-files?filter=status:eq:ACTIVE&sort=name:asc"

# Multi-table: Deal (filter by analyst, program, contract)
curl "http://localhost:8080/api/v1/deals?filter=analystName:sw:John"
curl "http://localhost:8080/api/v1/deals?filter=programType:eq:DEVELOPMENT"
curl "http://localhost:8080/api/v1/deals?filter=contractName:contains:Dev"

# Junction table: User roleIds
curl "http://localhost:8080/api/v1/users?filter=roleIds:in:(1,2)"

# Metadata discovery
curl http://localhost:8080/api/v1/model-files/metadata/fields
```

### Part 2: Reusability (3 min)

1. Open `filter/` package — show ~15 classes
2. Emphasize: **"These never change when we add User, Deal, ModelFile."**
3. Open `ModelFileController` → `ModelFileService` → `ModelFileRepository`
4. Show the flow: Controller passes strings → Service calls `FilterService.parseAndValidate(ModelFile.class, ...)` → Repository calls `SqlQueryBuilder.buildQuery(BASE_SELECT, request, metadata)`
5. **"The only entity-specific code is: entity constants, BASE_SELECT, and mapRow()."**

### Part 3: Metadata Discovery (2 min)

1. Open `ModelFile.java` — show FIELD_* and COL_*
2. Explain: EntityMetadataRegistry uses reflection to discover these
3. Open `EntityMetadataRegistry.extractMetadata()` — show the scan logic
4. **"No config files. Just Java constants. Add a field = add a constant = it's filterable."**

### Part 4: SQL Safety (1 min)

1. Open `SqlQueryBuilder.buildCondition()` — show `parameters.put(paramName, value)`
2. Emphasize: **"All values are bound. No string concatenation. SQL injection impossible."**

### Part 5: File Count (1 min)

1. Refer to [Section 6](#6-files-to-add-for-a-new-api)
2. **"For a new single-table API: 5 files. Schema + Entity + Repository + Service + Controller. That's it."**

---

## 9. Project Structure (Quick Reference)

```
src/main/java/com/example/
├── api/
│   ├── ErrorResponse.java           # Shared error DTO
│   └── FilterExceptionHandler.java # Global filter exception handler (SHARED)
├── config/
│   ├── AppConfig.java
│   └── DataSourceConfig.java
├── filter/                           # ⭐ SHARED — never modified for new APIs
│   ├── FilterService.java
│   ├── exception/
│   │   ├── FilterParseException.java
│   │   └── FilterValidationException.java
│   ├── jdbc/
│   │   └── SqlQueryBuilder.java
│   ├── metadata/
│   │   ├── EntityMetadata.java
│   │   ├── EntityMetadataRegistry.java
│   │   └── FieldMetadata.java
│   ├── model/
│   │   ├── FilterCriteria.java
│   │   ├── FilterOperator.java
│   │   ├── FilterRequest.java
│   │   ├── PageResponse.java
│   │   ├── SortCriteria.java
│   │   └── SortDirection.java
│   ├── parser/
│   │   └── FilterParser.java
│   ├── util/
│   │   └── StringUtils.java
│   └── validation/
│       └── FilterValidator.java
├── user/                             # API 1: Single-table + junction
│   ├── api/UserController.java
│   ├── entity/User.java
│   ├── repository/UserRepository.java
│   └── service/UserService.java
├── deal/                             # API 2: Multi-table with joins
│   ├── api/DealController.java
│   ├── entity/
│   │   ├── Deal.java
│   │   ├── DealFilterView.java
│   │   ├── Program.java
│   │   ├── Contract.java
│   │   └── UserOption.java
│   ├── repository/DealRepository.java
│   └── service/DealService.java
└── modelfile/                        # API 3: Single-table
    ├── api/ModelFileController.java
    ├── entity/ModelFile.java
    ├── repository/ModelFileRepository.java
    └── service/ModelFileService.java
```

---

## 10. Key Talking Points Checklist

- [ ] **Reusability:** Core filter logic (~15 classes) never changes when adding new APIs
- [ ] **File count:** 5 files for single-table API (schema + 4 classes)
- [ ] **Metadata discovery:** FIELD_* and COL_* constants; reflection-based; no config files
- [ ] **SQL safety:** 100% parameterized; no string concatenation
- [ ] **Three patterns:** Single-table, multi-table (joined), junction table
- [ ] **Query Entity pattern:** Flat view for filtering; aggregate to hierarchical response
- [ ] **Full table names:** `model_files.id`, `deals.deal_id` — no cryptic aliases
- [ ] **Centralized exceptions:** FilterExceptionHandler; no per-controller error handling

---

## 11. Related Docs

- **README.md** — Full project documentation, API reference, curl examples
- **docs/DEVELOPER_WALKTHROUGH.md** — Developer guide for self-paced learning or follow-along during demos
- **docs/ADDING_NEW_FILTER_TABLE.md** — Step-by-step guide for adding a new filterable API
