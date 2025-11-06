# Clean Code Checklist for Kotlin

## Naming Conventions

### Classes and Interfaces
- [ ] Use PascalCase (UserService, OrderRepository)
- [ ] Names are nouns or noun phrases
- [ ] Interfaces don't have 'I' prefix
- [ ] Implementation classes have descriptive suffix (UserServiceImpl only if necessary)

```kotlin
// 💡 GOOD
class UserService
interface UserRepository
class JpaUserRepository : UserRepository

// ⚠️ BAD
class userService
interface IUserRepository
class UserRepositoryImpl
```

### Functions and Methods
- [ ] Use camelCase (findUser, calculateTotal)
- [ ] Names are verbs or verb phrases
- [ ] Boolean functions start with is/has/can/should
- [ ] Names reveal intent without comments

```kotlin
// 💡 GOOD
fun findUserByEmail(email: String): User?
fun isActive(): Boolean
fun hasPermission(permission: String): Boolean
fun canAccessResource(resourceId: Long): Boolean

// ⚠️ BAD
fun getUserByEmail(email: String)  // 'get' is Java convention, use 'find' in Kotlin
fun active(): Boolean  // Not clear it's a boolean
fun check(id: Long)  // What are we checking?
```

### Variables
- [ ] Use camelCase
- [ ] Avoid abbreviations unless widely known
- [ ] Collections have plural names
- [ ] Boolean variables start with is/has/can
- [ ] No Hungarian notation

```kotlin
// 💡 GOOD
val userRepository: UserRepository
val users: List<User>
val isActive: Boolean
val hasPermission: Boolean

// ⚠️ BAD
val usrRepo  // Unnecessary abbreviation
val user: List<User>  // Should be plural
val active: Boolean  // Should start with 'is'
val strName: String  // Hungarian notation
```

### Constants
- [ ] Use UPPER_SNAKE_CASE
- [ ] Defined in companion object or object

```kotlin
// 💡 GOOD
companion object {
    const val MAX_RETRY_ATTEMPTS = 3
    const val DEFAULT_PAGE_SIZE = 20
}

// ⚠️ BAD
val maxRetryAttempts = 3
val DEFAULTPAGESIZE = 20
```

## Function Design

### Function Size
- [ ] Functions are small (< 20 lines ideal, < 50 lines maximum)
- [ ] Functions do one thing
- [ ] One level of abstraction per function
- [ ] Extract complex conditions into named functions

```kotlin
// 💡 Alternative: Small, focused functions
fun processOrder(orderId: Long) {
    val order = findOrderOrThrow(orderId)
    validateOrder(order)
    executePayment(order)
    updateInventory(order)
    notifyCustomer(order)
}

// ⚠️ Consider: Large function doing everything
fun processOrder(orderId: Long) {
    // 100+ lines of code
}
```

### Function Arguments
- [ ] Prefer 0-2 arguments
- [ ] 3 arguments should be carefully considered
- [ ] More than 3 arguments: use data class
- [ ] Avoid boolean arguments (use two functions instead)

```kotlin
// 💡 GOOD
data class CreateUserCommand(
    val email: String,
    val name: String,
    val password: String,
    val age: Int
)
fun createUser(command: CreateUserCommand): User

// 💡 Alternative: Two functions instead of boolean
fun enableUser(userId: Long)
fun disableUser(userId: Long)

// ⚠️ BAD
fun createUser(email: String, name: String, password: String, age: Int): User
fun setUserStatus(userId: Long, enabled: Boolean)  // Boolean parameter
```

### Return Values
- [ ] Prefer non-null return types
- [ ] Use sealed classes for multiple return types
- [ ] Don't return null, throw exception or use Optional

```kotlin
// 💡 GOOD
fun findUser(id: Long): User {
    return userRepository.findById(id).orElseThrow {
        UserNotFoundException("User not found: $id")
    }
}

sealed class Result<out T> {
    data class Success<T>(val value: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

// ⚠️ BAD
fun findUser(id: Long): User? {
    return userRepository.findById(id).orElse(null)
}
```

## Class Design

### Single Responsibility
- [ ] Each class has one reason to change
- [ ] Class name describes its responsibility
- [ ] If class name has "And" or "Manager", consider splitting

```kotlin
// 💡 GOOD
class CreateUserUseCase(private val userRepository: UserRepository)
class SendWelcomeEmailService(private val emailService: EmailService)
class UserPasswordEncoder(private val passwordEncoder: PasswordEncoder)

// ⚠️ BAD
class UserServiceAndEmailService  // Two responsibilities
class UserManager  // Too vague
```

### Class Size
- [ ] Classes are small (< 200 lines ideal, < 500 lines maximum)
- [ ] High cohesion: methods use instance variables
- [ ] Low coupling: minimal dependencies on other classes

### Kotlin-Specific Class Features
- [ ] Use data classes for DTOs and value objects
- [ ] Use sealed classes for restricted hierarchies
- [ ] Use object for singletons
- [ ] Prefer immutability (val over var)

```kotlin
// 💡 GOOD
data class UserDto(
    val id: Long,
    val email: String,
    val name: String
)

sealed class PaymentResult {
    data class Success(val transactionId: String) : PaymentResult()
    data class Failure(val reason: String) : PaymentResult()
}

object DatabaseConfig {
    const val MAX_POOL_SIZE = 20
}

// ⚠️ BAD
class UserDto {
    var id: Long = 0
    var email: String = ""
    var name: String = ""
}
```

## Code Organization

### File Structure
- [ ] One public class per file
- [ ] File name matches class name
- [ ] Imports organized (no wildcard imports)
- [ ] Related classes in same package

### Package Structure
- [ ] Package by feature (not by layer)
- [ ] Clear separation of layers
- [ ] No cyclic dependencies

```
// 💡 Alternative: Package by feature
com.example.user/
  ├── User.kt
  ├── UserRepository.kt
  ├── CreateUserUseCase.kt
  └── UserController.kt

// ⚠️ Consider: Package by layer
com.example.domain/User.kt
com.example.repository/UserRepository.kt
com.example.service/UserService.kt
com.example.controller/UserController.kt
```

## Error Handling

### Exception Usage
- [ ] Use exceptions for exceptional cases
- [ ] Create custom domain exceptions
- [ ] Preserve exception context (use cause parameter)
- [ ] Don't catch generic Exception unless necessary
- [ ] Clean up resources (use `use` function)

```kotlin
// 💡 GOOD
class UserNotFoundException(
    message: String,
    val userId: Long,
    cause: Throwable? = null
) : RuntimeException(message, cause)

fun processFile(path: String) {
    File(path).useLines { lines ->
        lines.forEach { processLine(it) }
    }
}

// ⚠️ BAD
throw Exception("Error")  // Generic exception
catch (e: Exception) { }  // Too broad
File(path).readLines()  // No resource cleanup
```

## Comments

### When to Comment
- [ ] WHY, not WHAT
- [ ] Complex algorithms need explanation
- [ ] Public APIs documented with KDoc
- [ ] No commented-out code (use version control)

```kotlin
// 💡 GOOD
/**
 * Calculates compound interest using the formula A = P(1 + r/n)^(nt)
 * where P is principal, r is annual rate, n is compounds per year, t is years
 */
fun calculateCompoundInterest(principal: BigDecimal, rate: Double, years: Int): BigDecimal

// Using binary search for O(log n) performance on sorted data
val index = items.binarySearch(target)

// ⚠️ BAD
// Get the user  <- Obvious from code
val user = userRepository.findById(id)

// i++  <- Useless comment
i++

// val oldImplementation = doSomethingOld()  <- Delete, don't comment out
```

### Code Self-Documentation
- [ ] Code is self-explanatory through good naming
- [ ] Extract methods for clarity instead of comments
- [ ] Use meaningful variable names

```kotlin
// 💡 Alternative: Self-documenting
fun isEligibleForDiscount(user: User, order: Order): Boolean {
    return user.isPremium && order.totalAmount > DISCOUNT_THRESHOLD
}

// ⚠️ Consider: Needs comment
fun check(u: User, o: Order): Boolean {
    // Check if user is premium and order is above threshold
    return u.p && o.t > 100
}
```

## Kotlin Idioms

### Null Safety
- [ ] Use ? for nullable types
- [ ] Use ?: (Elvis operator) for defaults
- [ ] Use ?. (safe call) for chaining
- [ ] Use !! only when absolutely certain

```kotlin
// 💡 GOOD
val name = user?.name ?: "Unknown"
val email = user?.profile?.email

// ⚠️ BAD
val name = user!!.name  // Will crash if user is null
```

### Collections
- [ ] Prefer immutable collections
- [ ] Use collection operations (map, filter, etc.)
- [ ] Avoid manual iteration when collection operations work

```kotlin
// 💡 GOOD
val activeUsers = users.filter { it.isActive }
val userNames = users.map { it.name }
val totalAmount = orders.sumOf { it.amount }

// ⚠️ BAD
val activeUsers = mutableListOf<User>()
for (user in users) {
    if (user.isActive) {
        activeUsers.add(user)
    }
}
```

### When Expression
- [ ] Use when instead of if-else chains
- [ ] Make when exhaustive when possible
- [ ] Use when for type checking

```kotlin
// 💡 GOOD
val message = when (status) {
    OrderStatus.PENDING -> "Order is pending"
    OrderStatus.SHIPPED -> "Order has been shipped"
    OrderStatus.DELIVERED -> "Order delivered"
    OrderStatus.CANCELLED -> "Order cancelled"
}

// ⚠️ BAD
val message = if (status == OrderStatus.PENDING) {
    "Order is pending"
} else if (status == OrderStatus.SHIPPED) {
    "Order has been shipped"
} // ... multiple else if
```

### Extension Functions
- [ ] Use extensions to add behavior to external classes
- [ ] Don't overuse extensions on domain classes
- [ ] Keep extensions in same file or dedicated file

```kotlin
// 💡 GOOD
fun String.isValidEmail(): Boolean {
    return this.contains("@") && this.contains(".")
}

fun LocalDateTime.toIsoString(): String {
    return this.format(DateTimeFormatter.ISO_DATE_TIME)
}

// Usage
val isValid = email.isValidEmail()
```

## Testing Considerations

### Testable Code
- [ ] Use constructor injection (not field injection)
- [ ] Depend on interfaces, not implementations
- [ ] Avoid static methods and global state
- [ ] Functions are pure when possible

```kotlin
// 💡 Alternative: Testable
class CreateUserUseCase(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    fun execute(command: CreateUserCommand): User {
        val encodedPassword = passwordEncoder.encode(command.password)
        val user = User.create(command.email, encodedPassword)
        return userRepository.save(user)
    }
}

// ⚠️ Consider: Hard to test
class UserService {
    @Autowired
    private lateinit var userRepository: UserRepository
    
    fun createUser(dto: UserDto): User {
        val password = BCryptPasswordEncoder().encode(dto.password)  // Hardcoded dependency
        return userRepository.save(dto.toEntity())
    }
}
```

## Performance Considerations

### Premature Optimization
- [ ] Write clean code first
- [ ] Optimize only when profiling shows need
- [ ] Don't sacrifice readability for minor gains

### Known Performance Patterns
- [ ] Use lazy initialization when appropriate
- [ ] Avoid creating unnecessary objects in loops
- [ ] Use appropriate data structures (Set vs List)
- [ ] Consider immutability trade-offs

```kotlin
// 💡 GOOD
val expensiveResource by lazy {
    // Only created when first accessed
    initializeExpensiveResource()
}

// 💡 Alternative: Efficient check
val uniqueUsers = users.toSet()
if (userId in uniqueUsers) { /* ... */ }

// ⚠️ Consider: Inefficient
if (users.any { it.id == userId }) { /* ... */ }  // O(n) every time
```
