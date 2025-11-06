# Practical Review Scenarios

## Scenario 1: Legacy Code Migration

### Context
Migrating legacy Spring Boot 2.x Java code to Spring Boot 3.x Kotlin with Clean Architecture.

### Review Workflow

```kotlin
// Step 1: Identify migration blockers
jetbrains:search_in_files_content "javax.persistence"
jetbrains:search_in_files_content "@Autowired"
jetbrains:search_in_files_content "new "

// Step 2: Assess current architecture
jetbrains:list_directory_tree_in_folder src/main 3

// Step 3: Review dependency injection patterns
jetbrains:search_in_files_content "@Component\|@Service\|@Repository"
```

### Common Legacy Issues

#### Issue 1: Field Injection
```kotlin
// ⚠️ LEGACY: Field injection
@Service
class UserService {
    @Autowired
    private lateinit var userRepository: UserRepository
    
    @Autowired
    private lateinit var emailService: EmailService
}

// 💡 MODERN: Constructor injection
@Service
class UserService(
    private val userRepository: UserRepository,
    private val emailService: EmailService
)
```

#### Issue 2: Mutable Entities
```kotlin
// ⚠️ LEGACY: Mutable everything
@Entity
data class User(
    @Id var id: Long = 0,
    var name: String = "",
    var email: String = "",
    var status: String = "ACTIVE"
)

// 💡 MODERN: Immutable with domain behavior
@Entity
class User private constructor(
    @Id val id: Long = 0,
    val name: String,
    val email: Email,
    @Enumerated(EnumType.STRING)
    private var status: UserStatus
) {
    companion object {
        fun create(name: String, email: Email): User {
            require(name.isNotBlank()) { "Name cannot be blank" }
            return User(
                name = name,
                email = email,
                status = UserStatus.ACTIVE
            )
        }
    }
    
    fun deactivate() {
        require(status == UserStatus.ACTIVE) { "User is not active" }
        status = UserStatus.INACTIVE
    }
    
    fun isActive() = status == UserStatus.ACTIVE
}
```

#### Issue 3: Service Layer with Business Logic
```kotlin
// ⚠️ LEGACY: Anemic domain model, logic in service
@Service
class OrderService(
    private val orderRepository: OrderRepository
) {
    fun cancelOrder(orderId: Long) {
        val order = orderRepository.findById(orderId).get()
        if (order.status == "SHIPPED") {
            throw IllegalStateException("Cannot cancel shipped order")
        }
        order.status = "CANCELLED"
        orderRepository.save(order)
    }
}

// 💡 MODERN: Rich domain model with use cases
@Entity
class Order private constructor(
    @Id val id: Long = 0,
    val customerId: Long,
    @Enumerated(EnumType.STRING)
    private var status: OrderStatus
) {
    fun cancel() {
        require(status.canCancelFrom()) { 
            "Cannot cancel order in status: $status" 
        }
        status = OrderStatus.CANCELLED
    }
}

@Service
class CancelOrderUseCase(
    private val orderRepository: OrderRepository
) {
    @Transactional
    fun execute(orderId: Long) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { OrderNotFoundException(orderId) }
        order.cancel()
        orderRepository.save(order)
    }
}
```

### Migration Checklist

- [ ] Replace `javax.persistence.*` with `jakarta.persistence.*`
- [ ] Convert field injection to constructor injection
- [ ] Replace `new` keyword with factory methods or builders
- [ ] Convert mutable data classes to immutable domain entities
- [ ] Extract business logic from services to domain models
- [ ] Replace string-based status/types with enums
- [ ] Add domain exceptions
- [ ] Implement global exception handler
- [ ] Add validation at domain boundaries
- [ ] Write tests for migrated code

## Scenario 2: Performance Emergency

### Context
Production system experiencing slow response times. Need immediate performance review.

### Emergency Review Protocol

```kotlin
// Step 1: Check recent changes
jetbrains:get_project_vcs_status

// Step 2: Review database queries
jetbrains:search_in_files_content "@Query"
jetbrains:search_in_files_content "findAll()"

// Step 3: Check N+1 problems
jetbrains:search_in_files_content "@OneToMany\|@ManyToMany"

// Step 4: Review slow endpoints
jetbrains:search_in_files_content "@GetMapping\|@PostMapping"
```

### Critical Performance Issues

#### Issue 1: N+1 Query in Loop
```kotlin
// ⚠️ CRITICAL: N+1 query problem
@Service
class ReportService(
    private val userRepository: UserRepository,
    private val orderRepository: OrderRepository
) {
    fun generateUserReport(): List<UserReport> {
        val users = userRepository.findAll()  // 1 query
        return users.map { user ->
            val orders = orderRepository.findByUserId(user.id)  // N queries
            UserReport(user, orders)
        }
    }
}

// 💡 FIX: Fetch join or batch fetch
interface UserRepository : JpaRepository<User, Long> {
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.orders")
    fun findAllWithOrders(): List<User>
}

@Service
class ReportService(
    private val userRepository: UserRepository
) {
    fun generateUserReport(): List<UserReport> {
        return userRepository.findAllWithOrders()  // 1 query
            .map { user -> UserReport(user, user.orders) }
    }
}
```

#### Issue 2: Missing Pagination
```kotlin
// ⚠️ CRITICAL: Loading millions of records
@RestController
class ProductController(
    private val productRepository: ProductRepository
) {
    @GetMapping("/products")
    fun getAllProducts(): List<Product> {
        return productRepository.findAll()  // OOM risk
    }
}

// 💡 FIX: Implement pagination
@RestController
class ProductController(
    private val productRepository: ProductRepository
) {
    @GetMapping("/products")
    fun getAllProducts(
        @PageableDefault(size = 50, sort = ["id"]) pageable: Pageable
    ): Page<ProductDto> {
        return productRepository.findAll(pageable)
            .map { it.toDto() }
    }
}
```

#### Issue 3: Missing Database Index
```kotlin
// ⚠️ CRITICAL: Query without index
@Entity
@Table(name = "orders")  // No indexes defined
class Order(
    @Id val id: Long,
    val userId: Long,
    val createdAt: LocalDateTime
)

interface OrderRepository : JpaRepository<Order, Long> {
    // Full table scan on every call
    fun findByUserIdAndCreatedAtAfter(
        userId: Long, 
        createdAt: LocalDateTime
    ): List<Order>
}

// 💡 FIX: Add composite index
@Entity
@Table(
    name = "orders",
    indexes = [
        Index(name = "idx_user_created", columnList = "user_id,created_at"),
        Index(name = "idx_created_at", columnList = "created_at")
    ]
)
class Order(
    @Id val id: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime
)
```

#### Issue 4: Inefficient Aggregation
```kotlin
// ⚠️ CRITICAL: Load all data to aggregate in memory
@Service
class StatisticsService(
    private val orderRepository: OrderRepository
) {
    fun getTotalRevenue(): BigDecimal {
        val orders = orderRepository.findAll()  // Load millions
        return orders.sumOf { it.amount }  // Calculate in memory
    }
}

// 💡 FIX: Aggregate in database
interface OrderRepository : JpaRepository<Order, Long> {
    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM Order o")
    fun getTotalRevenue(): BigDecimal
    
    @Query("""
        SELECT new com.example.RevenueByMonth(
            YEAR(o.createdAt),
            MONTH(o.createdAt),
            SUM(o.amount)
        )
        FROM Order o
        GROUP BY YEAR(o.createdAt), MONTH(o.createdAt)
        ORDER BY YEAR(o.createdAt) DESC, MONTH(o.createdAt) DESC
    """)
    fun getRevenueByMonth(): List<RevenueByMonth>
}
```

### Emergency Fix Priority

1. **Immediate (< 1 hour)**
   - N+1 queries in hot paths
   - Missing pagination on list endpoints
   - Full table scans on large tables

2. **Urgent (< 1 day)**
   - Missing database indexes
   - Inefficient aggregations
   - Memory leaks

3. **Important (< 1 week)**
   - Suboptimal queries
   - Missing caching
   - Algorithm optimization

## Scenario 3: Code Review Automation

### Context
Setting up automated code review checks for pull requests.

### Automated Review Checklist

#### Architecture Checks
```kotlin
// Check 1: No business logic in controllers
fun checkControllerLogic(file: String): List<Issue> {
    val issues = mutableListOf<Issue>()
    
    if (file.contains("@RestController") || file.contains("@Controller")) {
        if (file.contains("repository.save") || 
            file.contains("repository.find")) {
            issues.add(Issue(
                severity = "HIGH",
                message = "Controller directly accessing repository. Use use case/service layer.",
                file = file
            ))
        }
    }
    
    return issues
}

// Check 2: Proper exception handling
fun checkExceptionHandling(file: String): List<Issue> {
    val issues = mutableListOf<Issue>()
    
    if (file.contains("catch (e: Exception)") && 
        !file.contains("logger.error")) {
        issues.add(Issue(
            severity = "MEDIUM",
            message = "Catching generic Exception without logging",
            file = file
        ))
    }
    
    return issues
}
```

#### Performance Checks
```kotlin
// Check 3: N+1 query detection
fun checkPotentialN1(file: String): List<Issue> {
    val issues = mutableListOf<Issue>()
    
    // Look for findAll() followed by repository calls in loop
    val pattern = Regex("""findAll\(\).*?\.map.*?repository\.find""", 
                       RegexOption.DOT_MATCHES_ALL)
    
    if (pattern.containsMatchIn(file)) {
        issues.add(Issue(
            severity = "CRITICAL",
            message = "Potential N+1 query detected. Consider using fetch join.",
            file = file
        ))
    }
    
    return issues
}

// Check 4: Missing pagination
fun checkMissingPagination(file: String): List<Issue> {
    val issues = mutableListOf<Issue>()
    
    if ((file.contains("@GetMapping") || file.contains("@RequestMapping")) &&
        file.contains("findAll()") &&
        !file.contains("Pageable")) {
        issues.add(Issue(
            severity = "HIGH",
            message = "List endpoint without pagination",
            file = file
        ))
    }
    
    return issues
}
```

#### Security Checks
```kotlin
// Check 5: SQL injection vulnerability
fun checkSqlInjection(file: String): List<Issue> {
    val issues = mutableListOf<Issue>()
    
    // Look for string concatenation in @Query
    val pattern = Regex("""@Query\(".*?\$\{.*?\}.*?"\)""")
    
    if (pattern.containsMatchIn(file)) {
        issues.add(Issue(
            severity = "CRITICAL",
            message = "Potential SQL injection. Use parameterized queries.",
            file = file
        ))
    }
    
    return issues
}
```

### CI/CD Integration Example

```yaml
# .github/workflows/code-review.yml
name: Automated Code Review

on: [pull_request]

jobs:
  review:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Run Architecture Checks
        run: |
          ./gradlew detekt
          
      - name: Check Code Quality
        run: |
          ./gradlew sonarqube
          
      - name: Performance Analysis
        run: |
          ./gradlew spotbugs
          
      - name: Security Scan
        run: |
          ./gradlew dependencyCheckAnalyze
```

## Scenario 4: Technical Debt Assessment

### Context
Quarterly technical debt review and prioritization.

### Assessment Framework

#### 1. Code Smell Detection
```kotlin
// Metric: Cyclomatic Complexity
// Target: < 10 per method
fun analyzeComplexity(file: String): ComplexityReport {
    // Count decision points: if, when, for, while, &&, ||, ?:
    val decisionPoints = countDecisionPoints(file)
    
    return ComplexityReport(
        file = file,
        complexity = decisionPoints + 1,
        severity = when {
            decisionPoints < 10 -> "LOW"
            decisionPoints < 20 -> "MEDIUM"
            else -> "HIGH"
        }
    )
}

// Metric: Method Length
// Target: < 20 lines
fun analyzeMethodLength(file: String): List<LongMethod> {
    return extractMethods(file)
        .filter { it.lineCount > 20 }
        .map { method ->
            LongMethod(
                name = method.name,
                lineCount = method.lineCount,
                recommendation = "Extract submethods"
            )
        }
}
```

#### 2. Test Coverage Analysis
```kotlin
// Target Coverage
data class CoverageTarget(
    val lineCoverage: Double = 80.0,      // 80% line coverage
    val branchCoverage: Double = 75.0,    // 75% branch coverage
    val classMinimum: Double = 70.0       // 70% minimum per class
)

// JaCoCo configuration
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
                counter = "LINE"
            }
        }
        rule {
            limit {
                minimum = "0.75".toBigDecimal()
                counter = "BRANCH"
            }
        }
    }
}
```

#### 3. Dependency Analysis
```kotlin
// Check for outdated dependencies
// Check for security vulnerabilities
// Analyze dependency tree depth

tasks.register("analyzeDependencies") {
    doLast {
        // OWASP dependency check
        dependencyCheckAnalyze {
            format = "JSON"
            failBuildOnCVSS = 7.0f
        }
        
        // Version updates
        dependencyUpdates {
            revision = "release"
            checkConstraints = true
        }
    }
}
```

### Debt Prioritization Matrix

```kotlin
data class TechnicalDebt(
    val issue: String,
    val severity: Severity,      // Critical, High, Medium, Low
    val effort: Effort,          // Hours to fix
    val businessImpact: Impact   // High, Medium, Low
) {
    // Priority score: Higher is more urgent
    fun priorityScore(): Int {
        val severityScore = when(severity) {
            Severity.CRITICAL -> 40
            Severity.HIGH -> 30
            Severity.MEDIUM -> 20
            Severity.LOW -> 10
        }
        
        val effortScore = when(effort.hours) {
            in 0..4 -> 30      // Quick wins
            in 5..16 -> 20     // 1-2 days
            in 17..40 -> 10    // 1 week
            else -> 5          // > 1 week
        }
        
        val impactScore = when(businessImpact) {
            Impact.HIGH -> 30
            Impact.MEDIUM -> 20
            Impact.LOW -> 10
        }
        
        return severityScore + effortScore + impactScore
    }
}

// Example prioritization
val debts = listOf(
    TechnicalDebt("N+1 query in order list", Severity.CRITICAL, Effort(2), Impact.HIGH),
    TechnicalDebt("Missing indexes on user table", Severity.HIGH, Effort(1), Impact.HIGH),
    TechnicalDebt("Legacy authentication code", Severity.MEDIUM, Effort(40), Impact.MEDIUM)
)

val prioritized = debts.sortedByDescending { it.priorityScore() }
```
