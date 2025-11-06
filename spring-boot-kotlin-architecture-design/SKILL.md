---
name: spring-boot-kotlin-architecture-design
description: Transform user requirements into detailed Clean Architecture design specifications for Spring Boot Kotlin projects. Use when you need to design domain models, database schemas, API endpoints, repository/service/controller layers, exception handling, and business logic. Reads PROJECT_CONVENTIONS.md to ensure design follows team standards. Outputs comprehensive design documents with entities, relationships, API specs, database DDL, and implementation guidance.
---

# Architecture Design

Transform feature requirements into detailed, production-ready architectural designs following Clean Architecture and DDD principles.

## Pre-Design Questions

Before starting, gather requirements to understand the feature properly:

```
Claude: Let me ask a few questions to design this properly:

1. Feature & Scope
   - What's the core business capability?
   - What operations must this support? (Create, Read, Update, Delete, Search, etc.)
   - Are there dependent features?

2. Domain Entities
   - What real-world concepts/entities are involved?
   - What's the main aggregate root?
   - What are the relationships? (1:1, 1:N, N:M?)

3. Business Rules
   - What validations are critical? (Uniqueness, constraints, etc.)
   - What state transitions exist? (e.g., Order: Pending → Shipped → Delivered)
   - What cross-entity validations? (e.g., Stock check during order creation)

4. Data & Performance
   - Typical query patterns?
   - Expected data volume?
   - Any specific performance requirements?

5. Edge Cases
   - What happens on failures? (Rollback, partial success?)
   - Concurrent operations? (Optimistic/pessimistic locking?)
   - Historical tracking needed? (Audit log, soft delete?)
```

Keep questions concise—ask 2-3 at a time, not all at once.

## Quick Reference

**처음 사용자를 위한 한눈에 보기:**

```
1. PROJECT_CONVENTIONS.md 읽기
2. 요구사항 명확히 하기 (대화형 질문)
3. 도메인 모델링 (entity_patterns.md 참고)
4. 관계 및 Fetch 전략 설계
5. 예외 및 검증 설계
6. 데이터베이스 스키마
7. API 엔드포인트 설계
8. Repository & Service 설계

설계 완료 후:
→ implementation_checklist.md로 검증
→ 구현 시작
```

---

## Design Workflow

### Phase 1: Read PROJECT_CONVENTIONS.md

**Action:** Always read `PROJECT_CONVENTIONS.md` first!

```bash
# Claude reads and internalizes:
# - Naming conventions (Entity, DTO, Service, Controller, Exception)
# - Architecture patterns (DDD, Repository, Service)
# - Exception design (BusinessException with context)
# - Database patterns (BaseEntity, auditing, Enum usage)
# - API design (REST conventions, response format)
# - Testing patterns (Unit with Fake, Integration with Testcontainers)
```

**Why:** The conventions document is the source of truth. Design must follow it.

### Phase 2: Understand & Clarify Requirements

**Process:**
1. Extract all business concepts (nouns) from user request
2. Identify operations and constraints
3. Ask clarifying questions if needed
4. Build a shared mental model

**Example:**
```
User: "I need inventory management for warehouses"

Claude:
- Concepts: Warehouse, Product, Stock, StockMovement
- Operations: Add Stock, Reduce Stock, Transfer, View Balance
- Questions:
  Q1: "Is it per-warehouse inventory or company-wide?"
  Q2: "Do you track stock movements (history)?"
  Q3: "Any reorder points or alerts?"
```

### Phase 3: Domain Modeling

**Use the Entity Decision Tree:**

```
┌─ Is it a core concept with lifecycle?
│  ├─ YES → Entity (with @Id, state changes)
│  └─ NO  → Value Object (@Embeddable, immutable)
│
├─ Can this exist independently?
│  ├─ YES → Separate Entity, has own repository
│  └─ NO  → Part of parent (Embedded or @OneToMany)
│
└─ What's the relationship cardinality?
   ├─ Parent : Child = 1:1 → Embedded or separate with unique FK
   ├─ Parent : Child = 1:N → @OneToMany / @ManyToOne
   └─ Parent : Child = N:M → @ManyToMany (decide on join table strategy)
```

**Output:**
- Entity list with attributes
- Relationship diagram (text format: `Parent (1) ─< (N) Child`)
- Value Objects (Embedded)
- Enums for fixed choices
- Base class inheritance (@MappedSuperclass)

**From dtxiotv3 Pattern:**
```kotlin
// Hierarchy pattern (from Site-Pipe-Channel structure):
@Entity class Site : BaseEntity()        // Root aggregate
@Entity class Pipe : BaseEntity()        // Child aggregate
@Entity class Channel : BaseEntity()     // Grandchild

// Value Object pattern:
@Embeddable data class Location(
    val latitude: Double,
    val longitude: Double
)
```

### Phase 4: Relationship & Fetch Strategy Design

**Quick Decision Framework:**

| Decision | Criteria | Choice |
|----------|----------|--------|
| **Cascade** | Should deleting parent delete children? | REMOVE or DETACH |
| **FetchType** | Load lazily or eagerly? | LAZY (default) unless N+1 risk |
| **Embedded** | Separate table or column? | Embedded = no FK, fewer queries |
| **Soft Delete** | Archive or physical delete? | Soft (add deleted_at) for audit trail |

**Quick Decision Tree:**

```
Q1: Does child exist independently?
├─ YES → @ManyToOne with cascade=[PERSIST]
└─ NO  → Continue to Q2

Q2: Is data immutable and always needed?
├─ YES → @Embeddable
└─ NO  → Continue to Q3

Q3: Should deleting parent delete children?
├─ YES → @OneToMany with cascade=[ALL], orphanRemoval=true
└─ NO  → @OneToMany with cascade=[PERSIST]

Q4: Should we load children eagerly or lazily?
├─ Eagerly (small amount) → fetch=EAGER
└─ Lazily (on demand) → fetch=LAZY (default)
```

**Key Guidelines:**
- Default to LAZY fetch (avoid N+1)
- Use @Embeddable for value objects (no extra table)
- Cascade REMOVE only when child cannot exist independently
- Index all foreign keys
- Use fetch joins for specific queries to avoid N+1

See `references/entity_patterns.md` for detailed scenarios and fetch strategy examples.

### Phase 5: Exception & Validation Design

**Exception Hierarchy (from PROJECT_CONVENTIONS):**

```kotlin
sealed class BusinessException(...): RuntimeException(...)

// Domain exceptions (follow naming: {Condition}Exception)
class OrderNotFoundException(orderId: String) : BusinessException(...)
class InsufficientStockException(productId: String, needed: Int, available: Int) : BusinessException(...)
class OrderCannotBeCancelledException(status: OrderStatus) : BusinessException(...)
```

**When to validate:**
- **Entity constructor**: Invariants (id != null, email format)
- **Service method**: Business rules (uniqueness, state transitions)
- **API endpoint**: Input constraints (@NotNull, @Size, @Email)

**Pattern:**
```kotlin
// Value Object validation (at creation)
@Embeddable
data class Email(val value: String) {
    init {
        require(value.matches(EMAIL_REGEX)) { "Invalid email" }
    }
}

// Entity business logic
class Order {
    fun cancel() {
        if (status == SHIPPED) {
            throw OrderCannotBeCancelledException(status)
        }
        status = CANCELLED
    }
}

// Service validation
fun createOrder(request: CreateOrderRequest) {
    if (!stockService.hasAvailable(productId, quantity)) {
        throw InsufficientStockException(productId, quantity, available)
    }
    return orderRepository.save(Order.of(request))
}
```

### Phase 6: Database Schema Design

**Considerations:**
1. **Column types & constraints**: NOT NULL, UNIQUE, default values
2. **Indexes**: On FK, search columns, covering indexes
3. **Partitioning**: For large tables (historical data)
4. **Soft delete**: Add deleted_at timestamp
5. **Temporal tables**: If audit trail needed (PostgreSQL TEMPORAL)

**Pattern:**
```sql
CREATE TABLE "order" (
    order_id VARCHAR(13) PRIMARY KEY,
    customer_id VARCHAR(13) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SHIPPED', ...)),
    total_amount DECIMAL(10,2) NOT NULL,
    shipping_address_latitude DOUBLE PRECISION,
    shipping_address_longitude DOUBLE PRECISION,
    deleted_at TIMESTAMP,  -- Soft delete
    created_date TIMESTAMP NOT NULL,
    created_by VARCHAR(50),
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);

-- Indexes (critical for performance)
CREATE INDEX idx_order_customer ON "order"(customer_id);
CREATE INDEX idx_order_status ON "order"(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_order_created ON "order"(created_date DESC);

-- Unique constraint
ALTER TABLE "order" ADD CONSTRAINT uq_order_external_ref UNIQUE (external_reference_id);
```

### Phase 7: API Endpoint Design

**REST Conventions (from PROJECT_CONVENTIONS):**

```
GET    /orders              # List orders (with pagination, filters)
GET    /orders/{orderId}    # Get order detail
POST   /orders              # Create order
PUT    /orders/{orderId}    # Replace order
PATCH  /orders/{orderId}    # Partial update
DELETE /orders/{orderId}    # Soft delete
```

**Request/Response DTOs:**

```kotlin
// Request DTO (with validation)
data class CreateOrderRequest(
    @field:NotBlank val customerId: String,
    @field:Size(min=1) val items: List<OrderItemRequest>
)

// Response DTO
data class OrderResponse(
    val orderId: String,
    val customerId: String,
    val status: String,
    val items: List<OrderItemResponse>,
    val totalAmount: BigDecimal
)

// Controller (minimal logic, delegate to service)
@Tag(name = "Orders")
@RestController
@RequestMapping("/orders")
class OrderController(private val orderService: OrderService) {
    
    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: String): Payload<OrderResponse> {
        val order = orderService.getById(orderId)
        return Payload(data = order.toResponse())
    }
    
    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): Payload<OrderResponse> {
        val order = orderService.create(request)
        return Payload(data = order.toResponse(), status = CREATED)
    }
}
```

### Phase 8: Repository & Service Design

**Repository Pattern (from PROJECT_CONVENTIONS):**

```kotlin
// Define custom queries, not implementation details
interface OrderRepository : JpaRepository<Order, String> {
    fun findByCustomerId(customerId: String): List<Order>
    fun findByStatusAndCreatedDateAfter(status: OrderStatus, from: LocalDateTime): List<Order>
    fun existsByExternalReferenceId(refId: String): Boolean
}

// Custom implementation (if QueryDSL needed)
@Repository
class OrderRepositoryImpl(private val queryFactory: JPAQueryFactory) {
    fun findOrdersNearLocation(latitude: Double, longitude: Double, radiusKm: Double): List<Order> {
        // Complex spatial query
    }
}
```

**Service Pattern:**

```kotlin
@Service
@Transactional
class OrderService(
    private val orderRepository: OrderRepository,
    private val stockService: StockService
) {
    // Public methods for use cases
    fun create(request: CreateOrderRequest): Order {
        // 1. Validate
        require(!orderRepository.existsByExternalReferenceId(request.externalRef))
        
        // 2. Business logic
        for (item in request.items) {
            if (!stockService.hasAvailable(item.productId, item.quantity)) {
                throw InsufficientStockException(item.productId, ...)
            }
        }
        
        // 3. Persist
        val order = Order.of(request)
        return orderRepository.save(order)
    }
    
    fun cancel(orderId: String): Order {
        val order = orderRepository.getById(orderId) ?: throw OrderNotFoundException(orderId)
        order.cancel()  // Calls domain logic
        return orderRepository.save(order)
    }
}
```

**Key Principles:**
- One service per aggregate root
- Stateless (no instance variables)
- Use `@Transactional` for write ops, `readOnly=true` for reads
- Let domain entities encapsulate business logic

## Design Specification Output

After completing all phases, generate a comprehensive design document:

```markdown
# {Feature} - Architecture Design

## 1. Overview
- Business capability & scope
- Key stakeholders & use cases
- Success criteria

## 2. Domain Model
- Entity list with attributes
- Relationships (text diagram)
- Value Objects (@Embeddable)
- Enums

## 3. Database Schema
- DDL (CREATE TABLE, indexes)
- Constraints & defaults
- Performance notes

## 4. Repository Layer
- Custom query methods
- Pagination strategy
- Complex query patterns (QueryDSL if needed)

## 5. Service Layer
- Public methods & signatures
- Business logic & validations
- Transaction boundaries
- External dependencies

## 6. API Layer
- Endpoint summary table
- Request/Response DTOs
- Error codes & messages
- Example requests/responses

## 7. Exception Handling
- Exception hierarchy
- When each is thrown
- Error code mapping

## 8. Design Decisions & Rationales
- Why this entity structure?
- Why LAZY/EAGER on relationships?
- Why soft delete vs hard delete?
- Why QueryDSL vs Query methods?

## 9. Design Checklist (Pre-Implementation)
- [ ] All entities extend BaseEntity?
- [ ] Foreign key constraints defined?
- [ ] Indexes on all FKs & search columns?
- [ ] N+1 queries reviewed?
- [ ] Soft delete strategy clear?
- [ ] Transaction scopes (@Transactional) defined?
- [ ] All business rules validated?
- [ ] Exception coverage complete?
- [ ] API endpoints follow REST?
- [ ] DTOs have @NotNull/@Size validation?
- [ ] Response format standardized?

## 10. Testing Strategy
- Unit tests: Service with Fake repository
- Integration tests: Testcontainers + full stack
- Edge cases covered (concurrency, failures, etc.)

## 11. Future Enhancements
- Caching (Redis)
- Event publishing (if async needed)
- Read model (CQRS if complex queries)
```

## Best Practices from Production Systems

Real-world architectural patterns battle-tested in complex, data-intensive systems:

**1. Aggregate Structure (Clear Ownership Hierarchy)**
- Example: Site → Pipe → Channel hierarchy with cascading operations
- Benefit: Clear data ownership, simple transactions

**2. Spatial/Geospatial Data Handling**
- Example: Location value object with distance calculations
- Benefit: Type-safety, reusability across entities

**3. Bulk Import with Rich Context**
- Example: CSV parsing with file/line/column/value context in exceptions
- Benefit: Users can immediately fix exact issue without guessing

**4. Complex Query Strategy**
- Example: QueryDSL for dynamic queries vs @Query for stable ones
- Benefit: More readable, easier to modify, better performance

**5. Time-Series Data & Archival**
- Example: ElementCollection for small datasets, separate Entity for large
- Benefit: Balance between simplicity and performance

See `references/production_patterns.md` for detailed analysis including pitfalls and fixes.

### Common Pitfalls from dtxiotv3

## Common Pitfalls to Avoid

❌ **Pitfall 1**: Entity with circular dependencies  
✅ **Fix**: Identify clear aggregate root, unidirectional relationships

❌ **Pitfall 2**: All relationships EAGER → N+1 queries  
✅ **Fix**: Default LAZY, use fetch joins in queries

❌ **Pitfall 3**: Business logic in Service, not Entity  
✅ **Fix**: Entities should encapsulate domain logic (cancel, cancel, transition)

❌ **Pitfall 4**: No validation in Value Objects  
✅ **Fix**: Validate immutable data at construction (Email, Phone)

❌ **Pitfall 5**: Missing indexes on foreign keys  
✅ **Fix**: Every FK and search column needs index

❌ **Pitfall 6**: Ignoring soft delete → data loss  
✅ **Fix**: Add deleted_at, use WHERE deleted_at IS NULL in queries

## When to Iterate

After implementation, revisit design if:
- N+1 query problems discovered
- Complex queries don't perform
- New business rules emerge
- Relationship cardinality changes
- Test coverage below 80%

Iteration is normal—capture learnings in updated design document.

## Integration with Other Skills

**Workflow:**
```
spring-boot-init
    ↓ (generates PROJECT_CONVENTIONS.md)
architecture-design  ← You are here
    ↓ (design specification)
tdd-development      (implement with tests)
    ↓ (working code)
code-review          (validate against conventions)
    ↓ (refactoring)
documentation        (API docs, ERD)
```

**In the future: PRD-to-Design skill**
```
(new skill: prd-writer)
    ↓ (converts requirements to structured PRD)
architecture-design
    ↓ (converts PRD to architecture)
(rest of workflow)
```

Always read PROJECT_CONVENTIONS.md first. It's the source of truth for naming, patterns, and standards.

---

## References in This Skill

- **SKILL.md** (this file): 8-Phase design process
- **references/entity_patterns.md**: 8 entity patterns + decision trees
- **references/implementation_checklist.md**: 75 checklist items + 8 common mistakes
- **references/production_patterns.md**: Real-world patterns, pitfalls & fixes
