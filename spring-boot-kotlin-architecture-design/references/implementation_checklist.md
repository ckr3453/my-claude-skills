# Design Validation Checklist & Implementation Readiness

## Pre-Implementation Design Review

Complete this checklist before writing code. Each item prevents common production issues.

### Domain Model

- [ ] **Aggregate Root Identified**: Is there a clear parent entity that owns children?
  - Good: Order is root, OrderItems are children
  - Bad: OrderItem can exist without Order

- [ ] **All Entities Extend BaseEntity**: (id + auditing + created_by/date)
  ```kotlin
  @Entity
  class Product : BaseEntity()  // ✅ Inherits id, createdDate, etc.
  ```

- [ ] **Lifecycle Clear**: Does each entity know when it's created, updated, deleted?
  - Can you answer: "When is this entity soft-deleted?"
  - Can you answer: "Who creates this entity?"

- [ ] **Invariants Protected**: Critical rules enforced in entity constructor
  ```kotlin
  init {
      require(quantity > 0) { "Quantity must be positive" }
      require(email.isNotEmpty()) { "Email required" }
  }
  ```

- [ ] **State Transitions Valid**: State changes documented and validated
  ```
  Order: PENDING → SHIPPED → DELIVERED (or → CANCELLED from PENDING)
  ```

### Relationships

- [ ] **Cardinality Correct**: 1:1, 1:N, N:M properly identified
  - [ ] 1:N: Parent has @OneToMany, Child has @ManyToOne
  - [ ] N:M: Both sides have @ManyToMany (if needed)

- [ ] **Cascade Policies Defined**:
  - [ ] Parent delete = child delete? → `cascade = [ALL], orphanRemoval = true`
  - [ ] Parent delete = child orphaned? → `cascade = [PERSIST]`
  - [ ] What happens on FK violation? → Clear error handling

- [ ] **Fetch Strategy Optimized**:
  - [ ] Default LAZY (prevent N+1)?
  ```kotlin
  @ManyToOne(fetch = LAZY)  // ✅ Don't eagerly load
  var customer: Customer
  ```
  - [ ] Identified queries that need fetch joins?
  ```kotlin
  @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.items WHERE o.status = :status")
  fun findOrdersWithItems(@Param("status") status: OrderStatus): List<Order>
  ```

- [ ] **Bidirectional Relationships**: If used, specify mappedBy
  ```kotlin
  // Parent side
  @OneToMany(mappedBy = "order")
  var items: List<OrderItem>
  
  // Child side
  @ManyToOne
  var order: Order
  ```

### Value Objects & Embeddings

- [ ] **Value Objects Immutable**: All fields are val, not var
  ```kotlin
  @Embeddable
  data class Email(val value: String)  // ✅ val, not var
  ```

- [ ] **Value Objects Validated on Creation**:
  ```kotlin
  @Embeddable
  data class Email(val value: String) {
      init {
          require(value.matches(EMAIL_REGEX)) { "Invalid email" }
      }
  }
  ```

- [ ] **Embedded Fields Don't Cause N+1**:
  - [ ] Embedded value objects are always loaded with parent
  - [ ] No @ManyToOne inside @Embeddable

### Repository Layer

- [ ] **All Queries Have Purpose**: No generic "findAll()" bloat
  - [ ] Each custom query answers a business question
  - [ ] Documented with comment why it exists

- [ ] **N+1 Queries Prevented**:
  ```kotlin
  // ❌ Bad: N+1 queries
  val orders = orderRepository.findAll()  // Query 1
  orders.forEach { order ->
      order.items.size  // Query N: loads items for each order
  }
  
  // ✅ Good: Single query with fetch join
  @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.items")
  fun findAllWithItems(): List<Order>
  ```

- [ ] **Foreign Key Indexes Defined**: Every @ManyToOne relationship
  ```sql
  CREATE INDEX idx_order_customer ON "order"(customer_id);
  CREATE INDEX idx_order_item_order ON order_item(order_id);
  ```

- [ ] **Search Columns Indexed**: Fields used in WHERE clauses
  ```sql
  CREATE INDEX idx_order_status ON "order"(status) WHERE deleted_at IS NULL;
  CREATE INDEX idx_product_code ON product(code) WHERE deleted_at IS NULL;
  ```

- [ ] **Soft Delete Always Filtered**:
  ```kotlin
  @Query("SELECT p FROM Product p WHERE p.deletedAt IS NULL")
  fun findAllActive(): List<Product>
  ```

### Service Layer

- [ ] **Single Responsibility**: One service per aggregate root
  - Order service handles orders (create, cancel, track)
  - Customer service handles customers (register, profile, etc.)

- [ ] **Transaction Boundaries Clear**: @Transactional on write methods
  ```kotlin
  @Transactional
  fun createOrder(request: CreateOrderRequest): Order {
      // Will rollback on exception
  }
  
  @Transactional(readOnly = true)
  fun getOrder(orderId: String): Order {
      // Optimized for read-only
  }
  ```

- [ ] **Business Logic Encapsulated**:
  ```kotlin
  // ❌ Bad: Logic in service
  if (order.status == PENDING) order.status = SHIPPED
  
  // ✅ Good: Logic in entity
  order.ship()  // Entity handles validation
  ```

- [ ] **External Dependencies Injected**: No new SomeService() in methods
  ```kotlin
  @Service
  class OrderService(
      private val orderRepository: OrderRepository,
      private val stockService: StockService  // Injected
  )
  ```

- [ ] **Exception Handling Strategy**: Which exceptions throw, which catch?
  ```kotlin
  fun createOrder(...): Order {
      // Let business exceptions bubble (handled by GlobalExceptionHandler)
      stockService.checkAvailability(...)  // May throw InsufficientStockException
      
      // Catch framework exceptions, wrap in business exception
      try {
          orderRepository.save(order)
      } catch (e: DataIntegrityViolationException) {
          throw DuplicateOrderException(...)
      }
  }
  ```

### Exception Design

- [ ] **Exception Hierarchy Defined**: All custom exceptions extend BusinessException
  ```kotlin
  sealed class BusinessException(message: String, val errorCode: ErrorCode)
  
  class OrderNotFoundException(orderId: String) : BusinessException(...)
  class InsufficientStockException(...) : BusinessException(...)
  ```

- [ ] **Error Codes Mapped**: Each exception has unique ErrorCode
  ```kotlin
  enum class ErrorCode(val code: String, val httpStatus: HttpStatus) {
      ORDER_NOT_FOUND("ORD-001", NOT_FOUND),
      INSUFFICIENT_STOCK("ORD-002", BAD_REQUEST),
      ORDER_CANNOT_BE_CANCELLED("ORD-003", BAD_REQUEST)
  }
  ```

- [ ] **Exceptions Include Context**: What info helps debug?
  ```kotlin
  throw InsufficientStockException(
      productId = productId,
      requiredQuantity = quantity,
      availableQuantity = available,
      stockMovementId = "SKM-123"  // Link to audit trail
  )
  ```

- [ ] **No Generic Exception Messages**: "Error occurred" → specific, actionable
  - Good: "Order #ORD-123 cannot be cancelled (status: SHIPPED)"
  - Bad: "An error occurred"

### API Layer

- [ ] **REST Conventions Followed**:
  ```
  GET    /orders              # List
  GET    /orders/{id}         # Get one
  POST   /orders              # Create
  PUT    /orders/{id}         # Replace
  PATCH  /orders/{id}         # Partial update
  DELETE /orders/{id}         # Delete (or soft-delete)
  ```

- [ ] **DTOs Have Validation**:
  ```kotlin
  data class CreateOrderRequest(
      @field:NotBlank val customerId: String,
      @field:Size(min = 1) val items: List<OrderItemRequest>,
      @field:DecimalMin("0.01") val totalAmount: BigDecimal
  )
  ```

- [ ] **Response Format Consistent**: All endpoints return Payload<T>
  ```kotlin
  data class Payload<T>(
      val status: HttpStatus,
      val message: String,
      val path: String,
      val data: T? = null,
      val errorCode: ErrorCode? = null
  )
  ```

- [ ] **Pagination Implemented** (if large datasets):
  ```kotlin
  fun listOrders(
      @RequestParam(defaultValue = "0") page: Int,
      @RequestParam(defaultValue = "20") size: Int
  ): Payload<Page<OrderResponse>>
  ```

- [ ] **Swagger Documentation**: @Tag, @Operation, @ApiResponse
  ```kotlin
  @Tag(name = "Orders")
  @GetMapping("/{orderId}")
  @Operation(summary = "Get order by ID")
  @ApiResponse(responseCode = "404", description = "Order not found")
  fun getOrder(@PathVariable orderId: String): Payload<OrderResponse>
  ```

### Database Schema

- [ ] **Column Constraints Defined**:
  - [ ] NOT NULL on required fields
  - [ ] Unique constraints on business keys (email, username, code)
  - [ ] Default values where appropriate
  ```sql
  CREATE TABLE product (
      product_id VARCHAR(13) PRIMARY KEY,
      name VARCHAR(100) NOT NULL,
      code VARCHAR(20) NOT NULL UNIQUE,
      status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
      ...
  );
  ```

- [ ] **Foreign Key Constraints with Action**:
  ```sql
  FOREIGN KEY (order_id) REFERENCES "order"(order_id) ON DELETE CASCADE,
  FOREIGN KEY (product_id) REFERENCES product(product_id) ON DELETE RESTRICT
  ```

- [ ] **Check Constraints for Enums**:
  ```sql
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SHIPPED', 'DELIVERED')),
  quantity INT NOT NULL CHECK (quantity > 0)
  ```

- [ ] **Indexes Planned**:
  - [ ] All FK columns indexed
  - [ ] All search/filter columns indexed
  - [ ] Composite indexes for common WHERE + ORDER BY
  ```sql
  CREATE INDEX idx_order_customer_status ON "order"(customer_id, status);
  ```

- [ ] **Audit Columns Present**: created_date, created_by, modified_date, modified_by
  - Inherited from BaseEntity

- [ ] **Soft Delete Column** (if needed): deleted_at TIMESTAMP NULL

### Testing

- [ ] **Test Plan Documented**:
  - [ ] Unit tests (Entity + Service with Fake)
  - [ ] Integration tests (Testcontainers + actual DB)
  - [ ] Edge cases (concurrency, failures, boundaries)

- [ ] **Coverage Target**: 80% minimum on domain/service layer
  ```bash
  jacocoTestCoverageVerification {
      minimumInstructionCoverage = 0.80
  }
  ```

- [ ] **Fake Repositories Created** (for unit tests):
  ```kotlin
  class FakeOrderRepository : OrderRepository {
      private val orders = mutableListOf<Order>()
      
      override fun save(order: Order): Order {
          orders.add(order)
          return order
      }
      
      override fun findById(id: String): Order? = orders.find { it.id == id }
  }
  ```

---

## Implementation Readiness Checklist

Before starting code:

- [ ] **Design Document Complete** (all 11 sections from SKILL.md)
- [ ] **PROJECT_CONVENTIONS.md Reviewed** (naming, patterns, testing)
- [ ] **Stakeholders Aligned** (entity structure, API contracts)
- [ ] **Edge Cases Documented** (what if stock is zero? concurrent updates?)
- [ ] **Performance Targets Set** (max query time, expected volume)
- [ ] **Security Requirements Clear** (who can create/read/update/delete?)

## Common Pitfalls & Preventions

| Pitfall | Prevention | Check |
|---------|-----------|-------|
| N+1 queries discovered late | Review all repository queries, plan fetch joins | [ ] |
| Soft-deleted records leak into results | Filter WHERE deleted_at IS NULL everywhere | [ ] |
| Transaction rollback loses data | Mark write methods with @Transactional | [ ] |
| Concurrent updates corrupt data | Use optimistic locking (@Version) for high-concurrency entities | [ ] |
| Missing FK indexes crash DB | Index every FK column | [ ] |
| Invalid state transitions allowed | Validate in entity methods | [ ] |
| API breaks due to DTO changes | Version DTOs early (CreateOrderRequest v2) | [ ] |
| Exception messages expose internals | Sanitize exception messages in GlobalExceptionHandler | [ ] |

---

## Common Mistakes & Real-World Prevention

**Experienced developers' pitfalls you should avoid:**

### ❌ Mistake 1: Domain Logic in Service (Should be in Entity)

**Bad:**
```kotlin
@Service
class OrderService(...) {
    fun createOrder(request: CreateOrderRequest): Order {
        if (request.quantity <= 0) throw InvalidQuantityException()
        if (!stockService.hasAvailable(...)) throw InsufficientStockException()
        val order = Order(...); return orderRepository.save(order)
    }
}
```

**Good:**
```kotlin
@Entity
class Order : BaseEntity() {
    companion object {
        fun of(request: CreateOrderRequest, customer: Customer): Order {
            require(request.quantity > 0)
            return Order(customer = customer, quantity = request.quantity)
        }
    }
}

@Service
class OrderService(...) {
    fun createOrder(request: CreateOrderRequest): Order {
        if (!stockService.hasAvailable(...)) throw InsufficientStockException()
        val order = Order.of(request, customer)
        return orderRepository.save(order)
    }
}
```

---

### ❌ Mistake 2: Soft Delete Filter Missing

**Bad:**
```kotlin
fun findAll(): List<Order> = orderRepository.findAll()  // Returns deleted!
```

**Good:**
```kotlin
@Query("SELECT o FROM Order o WHERE o.deletedAt IS NULL")
fun findAll(): List<Order>
```

---

### ❌ Mistake 3: Unnecessary Broad Transactions

**Bad:**
```kotlin
@Service
@Transactional  // ⚠️ ALL methods in transaction
class OrderService {
    fun getOrder(id: String): Order { ... }  // Read-only, unnecessary!
}
```

**Good:**
```kotlin
@Service
class OrderService {
    @Transactional(readOnly = true)
    fun getOrder(id: String): Order { ... }
    
    @Transactional
    fun createOrder(request: CreateOrderRequest): Order { ... }
}
```

---

### ❌ Mistake 4: String Instead of Enum (Type Safety Lost)

**Bad:**
```kotlin
var status: String  // ⚠️ "INVALID" can be saved!
```

**Good:**
```kotlin
@Enumerated(EnumType.STRING)
var status: OrderStatus  // ✅ Only valid enum values
```

---

### ❌ Mistake 5: N+1 Queries Discovered Late

**Bad (Found in production after 3 months):**
```kotlin
val orders = findAll()  // 1 query
orders.forEach { it.items.size }  // N queries! = N+1 total
```

**Good (Caught in design phase):**
```
implementation_checklist.md item:
- [ ] N+1 Queries Prevented
  → All finders reviewed
  → Fetch strategies decided
```

---

### ❌ Mistake 6: Ignoring Concurrent Modifications

**Bad:**
```kotlin
val order = findById(id)
order.status = CANCELLED
save(order)  // If another thread modifies between find/save → lost update!
```

**Good:**
```kotlin
@Entity
class Order {
    @Version
    var version: Long = 0  // ✅ Detects concurrent changes
}

try {
    order.status = CANCELLED
    save(order)
} catch (e: OptimisticLockingFailureException) {
    throw OrderStateConflictException("Modified concurrently")
}
```

---

### ❌ Mistake 7: Generic Exception Messages

**Bad:**
```kotlin
throw Exception("Error")
throw OrderException("Failed to create order")  // No context!
```

**Good:**
```kotlin
throw OrderNotFoundException(
    orderId = "ORD-123",
    userId = currentUser.id,
    timestamp = LocalDateTime.now()
)
// Logs: ErrorCode: ORD-001, Context: {orderId, userId, timestamp}
```

---

### ❌ Mistake 8: Missing Database Indexes

**Bad:**
```sql
CREATE TABLE "order" (customer_id VARCHAR(13))
-- Query: "Find orders by customer" = FULL TABLE SCAN!
```

**Good:**
```sql
CREATE INDEX idx_order_customer ON "order"(customer_id);
CREATE INDEX idx_order_status ON "order"(status);
CREATE INDEX idx_order_customer_status ON "order"(customer_id, status);
-- Queries use index, 1000x faster
```

---

**Prevention Checklist:**

| Mistake | Prevention | Done |
|---------|-----------|------|
| Domain logic in Service | Move to Entity.of() | [ ] |
| Soft delete filter missing | WHERE deleted_at IS NULL | [ ] |
| Broad @Transactional | Use readOnly=true | [ ] |
| String instead of Enum | @Enumerated(EnumType.STRING) | [ ] |
| N+1 queries | Review finder methods | [ ] |
| Concurrent modifications | @Version + error handling | [ ] |
| Generic exceptions | Specific ErrorCode + context | [ ] |
| Missing indexes | Every FK and search column | [ ] |

---

## Sign-Off Template

```markdown
## Design Sign-Off

- [ ] Business logic correctly modeled as entities/value objects?
- [ ] Relationships properly defined (1:1, 1:N, N:M)?
- [ ] All queries planned (no N+1)?
- [ ] Exceptions comprehensive?
- [ ] Database schema normalized?
- [ ] API follows REST conventions?
- [ ] Tests planned for happy path + edge cases?
- [ ] Performance requirements understood?

**Ready for implementation:** [DATE]
**Design Reviewed By:** [NAME]
**Next Step:** Create entities and integrate tests
```
