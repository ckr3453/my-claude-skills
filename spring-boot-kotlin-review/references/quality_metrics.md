# Code Quality Metrics and Standards

## Cyclomatic Complexity

### Definition
Measures the number of linearly independent paths through code. Higher complexity = harder to test and maintain.

### Thresholds
```kotlin
// Complexity = 1 (no decision points)
fun simple(x: Int): Int {
    return x * 2
}

// Complexity = 2 (one decision point)
fun moderate(x: Int): Int {
    return if (x > 0) x * 2 else x
}

// Complexity = 11 (too complex!)
fun tooComplex(status: String, type: String, amount: BigDecimal): Boolean {
    if (status == "ACTIVE") {
        if (type == "PREMIUM") {
            if (amount > BigDecimal("1000")) {
                return true
            } else if (amount > BigDecimal("500")) {
                return true
            }
        } else if (type == "STANDARD") {
            if (amount > BigDecimal("100")) {
                return true
            }
        }
    } else if (status == "PENDING") {
        if (amount < BigDecimal("50")) {
            return true
        }
    }
    return false
}
```

### Standards
- **1-5**: Simple, easy to test
- **6-10**: Moderate, acceptable
- **11-20**: Complex, should refactor
- **21+**: Very complex, must refactor

### Refactoring High Complexity

```kotlin
// 💡 Alternative: Refactored using strategy pattern
sealed class DiscountStrategy {
    abstract fun isEligible(amount: BigDecimal): Boolean
    
    object Premium : DiscountStrategy() {
        override fun isEligible(amount: BigDecimal) = 
            amount > BigDecimal("500")
    }
    
    object Standard : DiscountStrategy() {
        override fun isEligible(amount: BigDecimal) = 
            amount > BigDecimal("100")
    }
    
    object None : DiscountStrategy() {
        override fun isEligible(amount: BigDecimal) = false
    }
}

enum class UserStatus {
    ACTIVE, PENDING, INACTIVE;
    
    fun getDiscountStrategy(type: String): DiscountStrategy {
        return when (this) {
            ACTIVE -> when (type) {
                "PREMIUM" -> DiscountStrategy.Premium
                "STANDARD" -> DiscountStrategy.Standard
                else -> DiscountStrategy.None
            }
            PENDING -> DiscountStrategy.None
            INACTIVE -> DiscountStrategy.None
        }
    }
}

// Now complexity is 1-2 per method
fun isEligibleForDiscount(
    status: UserStatus, 
    type: String, 
    amount: BigDecimal
): Boolean {
    val strategy = status.getDiscountStrategy(type)
    return strategy.isEligible(amount)
}
```

### Detekt Configuration

```yaml
# detekt.yml
complexity:
  active: true
  ComplexCondition:
    active: true
    threshold: 4
  ComplexInterface:
    active: true
    threshold: 10
  ComplexMethod:
    active: true
    threshold: 15
  LargeClass:
    active: true
    threshold: 600
  LongMethod:
    active: true
    threshold: 60
  LongParameterList:
    active: true
    functionThreshold: 6
    constructorThreshold: 7
  MethodOverloading:
    active: true
    threshold: 6
  NestedBlockDepth:
    active: true
    threshold: 4
  TooManyFunctions:
    active: true
    thresholdInFiles: 11
    thresholdInClasses: 11
```

## Code Coverage

### Coverage Types

#### Line Coverage
Percentage of code lines executed during tests.

```kotlin
class Calculator {
    fun divide(a: Int, b: Int): Int {
        if (b == 0) {
            throw IllegalArgumentException("Cannot divide by zero")
        }
        return a / b
    }
}

// Test
@Test
fun `divide should work`() {
    val result = calculator.divide(10, 2)
    assertEquals(5, result)
}
// Line coverage: 66% (4 of 6 lines)
// Missing: exception path
```

#### Branch Coverage
Percentage of decision branches executed.

```kotlin
@Test
fun `divide should handle zero`() {
    assertThrows<IllegalArgumentException> {
        calculator.divide(10, 0)
    }
}
// Now branch coverage: 100% (both paths tested)
```

### Coverage Targets

**Project Level**
- Line Coverage: **80% minimum**
- Branch Coverage: **75% minimum**
- Class Coverage: **90% minimum**

**Critical Code**
- Domain entities: **90%+**
- Use cases: **95%+**
- Repositories: **80%+** (integration tests)
- Controllers: **80%+**

**Infrastructure Code**
- Configuration: **60%+**
- Utilities: **80%+**

### JaCoCo Configuration

```kotlin
// build.gradle.kts
plugins {
    jacoco
}

jacoco {
    toolVersion = "0.8.10"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/config/**",
                    "**/entity/**",
                    "**/dto/**",
                    "**/*Application.kt"
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    
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
        
        rule {
            element = "CLASS"
            limit {
                minimum = "0.70".toBigDecimal()
                counter = "LINE"
            }
            excludes = listOf(
                "*.config.*",
                "*.dto.*",
                "*Application"
            )
        }
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
```

### Coverage Best Practices

```kotlin
// ⚠️ Consider: Testing implementation details
@Test
fun `test private method behavior`() {
    // Trying to test private methods
    val privateMethod = MyClass::class.java.getDeclaredMethod("privateHelper")
    privateMethod.isAccessible = true
    // ...
}

// 💡 Alternative: Test public API, coverage comes naturally
@Test
fun `should calculate discount correctly`() {
    val order = Order.create(items, customer)
    val discount = order.calculateDiscount()
    
    assertEquals(expected, discount)
    // Private methods covered through public API
}

// ⚠️ Consider: 100% coverage with meaningless tests
@Test
fun `test getter`() {
    user.name = "John"
    assertEquals("John", user.name)  // Useless test
}

// 💡 Alternative: Test meaningful behavior
@Test
fun `should not allow empty name`() {
    assertThrows<IllegalArgumentException> {
        User.create(name = "", email = validEmail)
    }
}
```

## Method and Class Size Metrics

### Method Length

**Standards**
- Ideal: **< 20 lines**
- Acceptable: **< 30 lines**
- Maximum: **< 50 lines**

```kotlin
// ⚠️ Consider: 60+ line method
fun processOrder(orderId: Long) {
    // Validation (10 lines)
    // Inventory check (15 lines)
    // Payment (20 lines)
    // Notification (10 lines)
    // Logging (5 lines)
}

// 💡 Alternative: Extracted to focused methods
fun processOrder(orderId: Long) {
    val order = validateOrder(orderId)
    checkInventory(order)
    val payment = processPayment(order)
    sendNotification(order, payment)
    logOrderProcessing(order)
}
```

### Class Size

**Standards**
- Ideal: **< 200 lines**
- Acceptable: **< 300 lines**
- Maximum: **< 500 lines**

**Indicators of Large Class Problems**
- More than 10 dependencies
- More than 15 public methods
- More than 20 total methods
- Multiple responsibilities

```kotlin
// ⚠️ Consider: 500+ line god class
class UserService {
    // User CRUD (100 lines)
    // Authentication (100 lines)
    // Authorization (80 lines)
    // Profile management (90 lines)
    // Notification preferences (80 lines)
    // Subscription management (50 lines)
}

// 💡 Alternative: Single responsibility classes
class CreateUserUseCase { /* 30 lines */ }
class AuthenticationService { /* 80 lines */ }
class AuthorizationService { /* 90 lines */ }
class ProfileService { /* 70 lines */ }
class NotificationPreferenceService { /* 60 lines */ }
class SubscriptionService { /* 50 lines */ }
```

## Maintainability Index

### Calculation
```
MI = 171 - 5.2 * ln(HV) - 0.23 * CC - 16.2 * ln(LOC)

Where:
- HV = Halstead Volume (function of operators and operands)
- CC = Cyclomatic Complexity
- LOC = Lines of Code
```

### Scale
- **85-100**: Highly maintainable (green)
- **65-84**: Moderately maintainable (yellow)
- **< 65**: Difficult to maintain (red)

### Improving Maintainability

```kotlin
// MI = 45 (red - hard to maintain)
fun processData(data: List<String>): List<Result> {
    val results = mutableListOf<Result>()
    for (item in data) {
        if (item.startsWith("A")) {
            if (item.length > 10) {
                val processed = item.substring(1).uppercase()
                if (processed.contains("X")) {
                    results.add(Result.Success(processed))
                } else {
                    results.add(Result.Warning(processed))
                }
            }
        } else if (item.startsWith("B")) {
            // Another complex branch...
        }
    }
    return results
}

// MI = 82 (yellow - acceptable)
fun processData(data: List<String>): List<Result> {
    return data
        .filter { it.isNotBlank() }
        .mapNotNull { processItem(it) }
}

private fun processItem(item: String): Result? {
    return when {
        item.startsWith("A") -> processTypeA(item)
        item.startsWith("B") -> processTypeB(item)
        else -> null
    }
}
```

## Dependency Metrics

### Afferent Coupling (Ca)
Number of classes that depend on this class.

### Efferent Coupling (Ce)
Number of classes this class depends on.

### Instability (I)
```
I = Ce / (Ce + Ca)

Where:
- 0 = Maximally stable (many dependents, few dependencies)
- 1 = Maximally unstable (few dependents, many dependencies)
```

### Abstractness (A)
```
A = Abstract Classes / Total Classes

Where:
- 0 = Completely concrete
- 1 = Completely abstract
```

### Distance from Main Sequence (D)
```
D = | A + I - 1 |

Ideal: D ≈ 0
```

### Standards
- **Domain Layer**: Low instability (I < 0.3), High abstractness (A > 0.7)
- **Application Layer**: Medium instability (0.3 < I < 0.7)
- **Infrastructure Layer**: High instability (I > 0.7), Low abstractness (A < 0.3)

```kotlin
// Domain Layer (Stable, Abstract)
interface UserRepository {  // Abstract
    fun save(user: User): User
}

class User {  // Stable (many depend on it)
    // Rich domain model
}

// Infrastructure Layer (Unstable, Concrete)
class JpaUserRepository(  // Concrete implementation
    private val jpaRepository: SpringDataUserRepository,
    private val entityMapper: EntityMapper
) : UserRepository {  // Depends on many (unstable)
    override fun save(user: User): User {
        // Implementation
    }
}
```

## Technical Debt Ratio

### Formula
```
Technical Debt Ratio = (Remediation Cost / Development Cost) * 100

Where:
- Remediation Cost = Estimated time to fix all issues
- Development Cost = Time to develop from scratch
```

### Standards
- **< 5%**: Excellent
- **5-10%**: Good
- **10-20%**: Moderate
- **> 20%**: High debt, needs action

### SonarQube Integration

```kotlin
// build.gradle.kts
plugins {
    id("org.sonarqube") version "4.0.0.2929"
}

sonarqube {
    properties {
        property("sonar.projectKey", "my-project")
        property("sonar.projectName", "My Project")
        property("sonar.host.url", "http://localhost:9000")
        property("sonar.login", System.getenv("SONAR_TOKEN"))
        
        // Quality Gates
        property("sonar.coverage.jacoco.xmlReportPaths", 
                 "build/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.kotlin.detekt.reportPaths", 
                 "build/reports/detekt/detekt.xml")
        
        // Exclusions
        property("sonar.exclusions", 
                 "**/config/**,**/dto/**,**/entity/**,**/*Application.kt")
    }
}
```

### Quality Gate Configuration

```yaml
# SonarQube Quality Gate
conditions:
  - metric: new_coverage
    operator: LESS_THAN
    value: 80
    
  - metric: new_duplicated_lines_density
    operator: GREATER_THAN
    value: 3
    
  - metric: new_maintainability_rating
    operator: WORSE_THAN
    value: A
    
  - metric: new_reliability_rating
    operator: WORSE_THAN
    value: A
    
  - metric: new_security_rating
    operator: WORSE_THAN
    value: A
    
  - metric: new_security_hotspots_reviewed
    operator: LESS_THAN
    value: 100
```

## Code Duplication

### Standards
- **< 3%**: Excellent
- **3-5%**: Good
- **5-10%**: Acceptable
- **> 10%**: Needs refactoring

### Detection and Resolution

```kotlin
// ⚠️ Consider: Duplicated code (15% duplication)
class UserController {
    fun createUser(dto: CreateUserDto): UserDto {
        if (dto.email.isBlank()) throw BadRequestException("Email required")
        if (dto.name.isBlank()) throw BadRequestException("Name required")
        if (!dto.email.contains("@")) throw BadRequestException("Invalid email")
        // ... create logic
    }
}

class ProductController {
    fun createProduct(dto: CreateProductDto): ProductDto {
        if (dto.name.isBlank()) throw BadRequestException("Name required")
        if (dto.price < BigDecimal.ZERO) throw BadRequestException("Invalid price")
        // ... create logic
    }
}

// 💡 Alternative: Extract common validation (< 3% duplication)
abstract class BaseController {
    protected fun requireNotBlank(value: String, field: String) {
        if (value.isBlank()) {
            throw BadRequestException("$field is required")
        }
    }
    
    protected fun requireValidEmail(email: String) {
        requireNotBlank(email, "Email")
        if (!email.contains("@")) {
            throw BadRequestException("Invalid email format")
        }
    }
}

class UserController : BaseController() {
    fun createUser(dto: CreateUserDto): UserDto {
        requireValidEmail(dto.email)
        requireNotBlank(dto.name, "Name")
        // ... create logic
    }
}
```

## Monitoring and Reporting

### Gradle Task for Metrics

```kotlin
tasks.register("generateQualityReport") {
    group = "verification"
    description = "Generate comprehensive quality metrics report"
    
    dependsOn(
        "detekt",
        "jacocoTestReport",
        "sonarqube"
    )
    
    doLast {
        println("""
            Quality Metrics Report Generated:
            - Detekt: build/reports/detekt/detekt.html
            - JaCoCo: build/reports/jacoco/test/html/index.html
            - SonarQube: Check dashboard
        """.trimIndent())
    }
}
```

### CI/CD Quality Gates

```yaml
# .github/workflows/quality-gate.yml
name: Quality Gate

on: [pull_request]

jobs:
  quality:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Run Tests with Coverage
        run: ./gradlew test jacocoTestReport
        
      - name: Verify Coverage
        run: ./gradlew jacocoTestCoverageVerification
        
      - name: Run Detekt
        run: ./gradlew detekt
        
      - name: SonarQube Scan
        run: ./gradlew sonarqube
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          
      - name: Quality Gate Check
        run: |
          # Fail if quality gate fails
          curl -u $SONAR_TOKEN: \
            "$SONAR_HOST/api/qualitygates/project_status?projectKey=my-project" \
            | jq -e '.projectStatus.status == "OK"'
```
