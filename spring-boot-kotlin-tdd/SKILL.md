---
name: spring-boot-kotlin-tdd
description: Test-Driven Development for Spring Boot Kotlin projects. Generates comprehensive test suites and production-ready implementations from architecture-design specifications. Produces unit tests (JUnit5+MockK), business logic (Service/Repository), and validation reports. Use when you have a design document from architecture-design skill and need to implement a feature with full TDD cycle - tests first, then implementation, then validation.
---

# TDD Kotlin Development

## Overview

This skill automates the Test-Driven Development cycle for Spring Boot Kotlin projects. Given an architecture-design specification and entity name, it generates comprehensive test suites, production-ready implementations, and quality validation reports in a single pass.

## When to Use This Skill

**Perfect for:**
- Implementing features with design documents (from architecture-design skill)
- Ensuring high test coverage (85%+) from the start
- Following strict TDD discipline (RED → GREEN → REFACTOR)
- Working with Spring Boot Kotlin projects
- Maintaining consistent code quality across the team

**Not ideal for:**
- Quick prototypes (use ad-hoc development instead)
- Projects without clear design documents
- Non-Spring Boot projects
- When you already have working code (use for testing instead)

## Quick Start

### Minimal Example

```
You: "I have a design for an Inventory entity. Here's the design doc.
      Can you generate the full TDD implementation?"

Claude: "I'll generate comprehensive tests, implementation, and validation for Inventory.
         Output:
         - InventoryTest.kt (55 tests)
         - InventoryService.kt (business logic)
         - InventoryController.kt (endpoints)
         - ValidationReport.md"
```

## Full Workflow

### Step 1: Prepare Your Design Document

You need a design document from the `architecture-design` skill. It should include:

```
Entity: Inventory
├─ Fields: id, name, code, quantity, warehouseId, createdAt, updatedAt
├─ Relationships: 
│  ├─ ManyToOne: Warehouse
│  └─ OneToMany: InventoryMovement
├─ Operations: create, update, delete, search, getByWarehouse
├─ Validations:
│  ├─ code must be unique
│  ├─ quantity >= 0
│  └─ warehouse must exist
└─ Business Rules:
   ├─ Cannot delete if quantity > 0
   └─ Create movement record on quantity change
```

### Step 2: Provide the Skill with Context

Ensure you have:

1. **Design Document** - From architecture-design skill output
2. **PROJECT_CONVENTIONS.md** - From your spring-boot-init project
3. **Entity Name** - Clear specification of what to implement

### Step 3: Full TDD Cycle Execution

The skill executes three phases automatically:

#### Phase 1: Generate Comprehensive Tests
- Analyzes the design document
- Creates test scenarios for:
  - Happy path operations
  - Edge cases (boundary values, null, empty)
  - Error conditions and exceptions
  - Validation rule violations
  - State transitions (if applicable)
- Generates: `@Nested` structured tests with Arrange-Act-Assert pattern
- Uses: MockK for mocking, AssertJ for assertions
- Output: Complete test suite (~45-80 tests depending on complexity)

#### Phase 2: Write Minimal Implementation
- Reads all test requirements
- Implements ONLY what's needed to pass tests (YAGNI principle)
- Generates:
  - `@Entity` with fields, relationships, validations
  - `@Repository` interface (Spring Data JPA)
  - `@Service` with business logic
  - `@RestController` with CRUD endpoints
  - Request/Response DTOs
  - Custom exceptions following BusinessException pattern
- Follows: PROJECT_CONVENTIONS.md patterns exactly
- Output: Production-ready code (~200-400 lines depending on complexity)

#### Phase 3: Validate and Generate Report
- Verifies test execution (assumes all tests pass)
- Analyzes code quality:
  - Test coverage percentage (target: 85%+)
  - Method complexity (cyclomatic)
  - Naming convention adherence
  - Null-safety checks
  - Exception handling completeness
- Suggests refactoring improvements
- Identifies performance issues (e.g., N+1 queries)
- Output: Validation report with actionable metrics

### Step 4: Integrate into Your Project

The skill produces organized output:

```
src/test/kotlin/domain/[Entity]/
├─ [Entity]ServiceTest.kt
└─ [Entity]ControllerTest.kt

src/main/kotlin/domain/[Entity]/
├─ [Entity].kt (entity)
├─ [Entity]Request.kt
├─ [Entity]Response.kt
├─ [Entity]Exception.kt
├─ repository/
│  └─ [Entity]Repository.kt
└─ service/
   └─ [Entity]Service.kt

src/main/kotlin/api/
└─ controller/
   └─ [Entity]Controller.kt

IMPLEMENTATION_REPORT.md
```

## Handling Different Complexity Levels

### Simple Entity (Example: User)
- 5-10 fields
- 1-2 relationships
- 3-5 business rules
- ~30-40 tests
- Time: ~10 minutes

### Medium Entity (Example: Inventory)
- 10-20 fields
- 3-5 relationships
- 8-12 business rules
- ~50-60 tests
- Time: ~15 minutes

### Complex Entity (Example: Order with State Machine)
- 20+ fields
- 8+ relationships
- 15+ business rules + state transitions
- ~80-100 tests
- Time: ~25 minutes

## References for Advanced Scenarios

This skill includes comprehensive guides for complex scenarios. Read these for specific use cases:

### test-patterns.md
When: You need deep dive into testing strategies
- Kotlin Spring Boot testing best practices
- MockK vs @MockBean differences
- Testing complex relationships (N:M, circular)
- Testcontainers for integration tests
- Fixture and builder patterns

### implementation-patterns.md
When: Your implementation needs special handling
- Repository custom query strategies
- Service layer transaction boundaries
- Exception hierarchy and conversion
- Validation and business rule implementation
- Controller design patterns

### refactoring-guidelines.md
When: You want to optimize generated code
- Detecting and fixing N+1 query problems
- Performance optimization techniques
- Code complexity reduction
- Kotlin idioms and efficiency patterns

### kotlin-best-practices.md
When: You want more idiomatic Kotlin code
- Data class usage patterns
- Extension functions for cleaner APIs
- Scope functions (let, apply, run, with)
- Coroutine testing patterns
- Null safety best practices

### complex-domain-patterns.md
When: Your domain logic is non-trivial
- State machine implementation and testing
- Complex validation rule composition
- Concurrency control (optimistic/pessimistic locking)
- Aggregate root patterns
- Eventual consistency patterns

## Troubleshooting & FAQ

### Q: "Tests and implementation don't match"
**A:** The skill validates this automatically. Check:
1. Test file for exact method names and signatures
2. Implementation file matches those names
3. Run `test_impl_matcher.py` script to identify specific mismatches
4. Regenerate if needed with clarified design requirements

### Q: "How do I handle complex business logic like state machines?"
**A:** Include state machine in your design document:
```
OrderStateTransition:
  Pending →[confirm]→ Confirmed (requires inventory > 0)
  Confirmed →[ship]→ Shipped (requires payment verified)
  Shipped →[deliver]→ Delivered (final state)
```
Specify transition conditions and side effects. The skill generates complete tests and implementation.

### Q: "What about performance requirements?"
**A:** Specify in your request:
```
"Generate Inventory service with these performance requirements:
- List operations: < 100ms
- Search: < 200ms  
- Batch insert: < 5s for 10k items
- Optimize for concurrent access"
```
The validation phase includes performance analysis and optimization suggestions.

### Q: "Can I use this for existing projects?"
**A:** Yes, use Brownfield mode:
```
"I have existing InventoryService (attached).
Generate tests for it and refactor to follow current conventions."
```
See **complex-domain-patterns.md** for migration strategies.

### Q: "How do I know if I have enough test coverage?"
**A:** General rules:
- **Simple CRUD**: 3-4 tests per operation
- **With validation**: +2-3 tests per rule
- **With business logic**: +5-10 tests
- **With state machine**: +10-20 tests per transition

Aim for **80%+ coverage** with meaningful tests. The skill generates all necessary tests.

### Q: "What if my entity has circular relationships?"
**A:** Specify in design and the skill handles it:
```
User ↔ Group (many-to-many with join table)
Group → User (group owner)
```
The skill generates:
- Appropriate JPA annotations (@ManyToMany, @ManyToOne)
- Lazy loading strategies to avoid infinite loops
- Comprehensive tests for both directions
- Validation to prevent circular reference violations

## Complete Examples

### Example 1: Simple Entity - User Registration

**Your Request:**
```
"Design: User entity with email registration
- Fields: id, email, name, passwordHash, createdAt, verified
- Operations: register, verifyEmail, updateProfile
- Validations: email unique, email format valid, password strength
- Relationships: none"
```

**Generated Output:**
- 38 test cases (registration flow, validations, edge cases)
- UserService with email verification logic
- UserController with REST endpoints
- Validation Report: 88% coverage, 3 refactoring suggestions

**Time: 10 minutes**

### Example 2: Medium Entity - Inventory Management

**Your Request:**
```
"Design: Inventory management system
- Fields: id, name, code, quantity, warehouseId, lastUpdated
- Relationships: ManyToOne(Warehouse), OneToMany(InventoryMovement)
- Operations: create, transfer, adjust, search, report
- Validations: code unique per warehouse, quantity >= 0
- Business Rules: auto-create movement record, track history"
```

**Generated Output:**
- 56 test cases (CRUD, transfers, searches, business rules)
- InventoryService with custom repository queries
- InventoryController with pagination and filtering
- Validation Report: 87% coverage, N+1 query optimization suggested

**Time: 15 minutes**

### Example 3: Complex Entity - Order with State Machine

**Your Request:**
```
"Design: E-commerce Order system with state machine
- State Machine:
  NEW →[submit]→ PENDING_PAYMENT →[confirm]→ CONFIRMED →[ship]→ SHIPPED →[deliver]→ DELIVERED
  (Any state) →[cancel]→ CANCELLED
- Fields: id, customerId, items[], totalPrice, status, deliveryAddress, createdAt, updatedAt
- Relationships: ManyToOne(Customer), OneToMany(OrderItem), ManyToOne(Invoice)
- Operations: createOrder, submitOrder, confirmPayment, shipOrder, deliverOrder, cancelOrder
- Validations: customer exists, items not empty, valid transitions only
- Business Rules: recalculate total on item changes, track status history"
```

**Generated Output:**
- 92 test cases (all state transitions, validations, business rules, edge cases)
- OrderService with state machine implementation using strategy pattern
- OrderController with full order lifecycle endpoints
- OrderItem entity for item management
- Validation Report: 89% coverage, transaction boundary optimization, concurrency handling reviewed

**Time: 25 minutes**

## Integration with Your Workflow

### With architecture-design Skill
1. Use architecture-design to create detailed design specifications
2. Pass design output to this skill for implementation
3. Get complete, tested feature

### With spring-boot-init Skill
1. Initialize your project with spring-boot-init
2. Ensure PROJECT_CONVENTIONS.md is present
3. Use this skill with that convention reference
4. Generated code automatically follows your conventions

## Next Steps

1. **Prepare**: Gather your design document
2. **Request**: Call this skill with entity details and design
3. **Review**: Inspect generated tests and implementation
4. **Integrate**: Copy files into your project structure
5. **Verify**: Run tests locally (`./gradlew test`)
6. **Extend**: Add more entities using the same process

---

## Resources

This skill includes helper resources for complex scenarios:

**scripts/test_impl_matcher.py**
- Validates test and implementation consistency
- Identifies method signature mismatches
- Used automatically during generation

**scripts/prompt_generator.py**
- Generates optimized prompts for different entity complexities
- Adapts prompts based on design specifications

**assets/prompts/**
- Pre-optimized prompt templates
- Customizable for different scenarios

**assets/templates/**
- Code templates for common patterns
- Base classes for custom extensions
