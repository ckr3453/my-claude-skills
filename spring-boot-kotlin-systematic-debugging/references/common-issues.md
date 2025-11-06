# Common Spring Boot Kotlin Issues

## Transaction Management

### Issue 1: @Transactional Not Applied

**Symptom:** Changes not persisted to database

```kotlin
// ❌ Problem
@Service
class UserService {
    fun updateUser(id: Long, name: String) {  // No @Transactional
        val user = userRepository.findById(id).get()
        user.name = name
        // Changes lost after method returns
    }
}

// ✅ Solution
@Service
class UserService {
    @Transactional
    fun updateUser(id: Long, name: String) {
        val user = userRepository.findById(id).get()
        user.name = name
        userRepository.save(user)
    }
}
```

### Issue 2: Transaction Rollback Not Working

**Symptom:** Exception thrown but data still committed

```kotlin
// ❌ Problem: Catching exception prevents rollback
@Transactional
fun processOrder(order: Order) {
    try {
        orderRepository.save(order)
        paymentService.charge(order.amount)  // Throws exception
    } catch (e: PaymentException) {
        logger.error("Payment failed", e)  // Swallowed!
        // Order still saved
    }
}

// ✅ Solution: Let exception propagate
@Transactional
fun processOrder(order: Order) {
    orderRepository.save(order)
    paymentService.charge(order.amount)  // Exception rolls back all
}
```

## Lazy Loading Issues

### Issue 3: LazyInitializationException

**Symptom:** Exception when accessing related entities

```kotlin
// ❌ Problem
@RestController
class UserController(private val userRepository: UserRepository) {
    @GetMapping("/users/{id}")
    fun getUser(@PathVariable id: Long): UserDto {
        val user = userRepository.findById(id).get()
        return UserDto(
            id = user.id,
            name = user.name,
            orderCount = user.orders.size  // ❌ LazyInitializationException
        )
    }
}

// ✅ Solution 1: Fetch join
interface UserRepository : JpaRepository<User, Long> {
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.orders WHERE u.id = :id")
    fun findByIdWithOrders(@Param("id") id: Long): User?
}

// ✅ Solution 2: DTO projection
interface UserRepository : JpaRepository<User, Long> {
    @Query("""
        SELECT new com.example.UserDto(
            u.id, u.name, SIZE(u.orders)
        ) FROM User u WHERE u.id = :id
    """)
    fun findUserDtoById(@Param("id") id: Long): UserDto?
}

// ✅ Solution 3: @Transactional on controller (not recommended)
@Transactional(readOnly = true)
@GetMapping("/users/{id}")
fun getUser(@PathVariable id: Long): UserDto {
    // Works but keeps transaction open during serialization
}
```

## Bean Initialization

### Issue 4: Dependency Not Initialized

**Symptom:** lateinit property not initialized

```kotlin
// ❌ Problem
@Service
class UserService {
    lateinit var emailService: EmailService  // Not initialized
    
    fun createUser(user: User) {
        userRepository.save(user)
        emailService.sendWelcome(user.email)  // ❌ UninitializedPropertyAccessException
    }
}

// ✅ Solution: Constructor injection
@Service
class UserService(
    private val emailService: EmailService
) {
    fun createUser(user: User) {
        userRepository.save(user)
        emailService.sendWelcome(user.email)  // ✅ Works
    }
}
```

### Issue 5: Circular Dependency

**Symptom:** BeanCurrentlyInCreationException

```kotlin
// ❌ Problem
@Service
class UserService(private val orderService: OrderService)

@Service
class OrderService(private val userService: UserService)  // Circular!

// ✅ Solution 1: Refactor to break cycle
@Service
class UserService(private val orderRepository: OrderRepository)

@Service  
class OrderService(private val userRepository: UserRepository)

// ✅ Solution 2: Use @Lazy
@Service
class OrderService(@Lazy private val userService: UserService)
```

## Connection Pool Issues

### Issue 6: Connection Pool Exhausted

**Symptom:** "Connection is not available, request timed out"

```kotlin
// Check current pool status
// application.yml
management:
  endpoints:
    web:
      exposure:
        include: metrics
        
// Query: curl localhost:8080/actuator/metrics/hikaricp.connections.active

// ❌ Problem: Not closing connections
@Service
class ReportService(private val jdbcTemplate: JdbcTemplate) {
    fun generateReport(): List<Report> {
        val connection = jdbcTemplate.dataSource?.connection  // ❌ Not closed
        // ... do work
        return results
    }
}

// ✅ Solution: Use Spring abstractions
@Service
class ReportService(private val jdbcTemplate: JdbcTemplate) {
    fun generateReport(): List<Report> {
        return jdbcTemplate.query("SELECT ...") { rs, _ ->
            Report(rs.getLong("id"), rs.getString("name"))
        }  // ✅ Connection auto-closed
    }
}

// Configure pool size appropriately
spring:
  datasource:
    hikari:
      maximum-pool-size: 10  # Default is 10
      minimum-idle: 5
      connection-timeout: 30000
```

## N+1 Query Problems

### Issue 7: Accidental N+1 in Production

**Symptom:** Slow performance with many database queries

```kotlin
// ❌ Problem: N+1 queries
@Service
class OrderService(private val orderRepository: OrderRepository) {
    fun getAllOrderSummaries(): List<OrderSummary> {
        return orderRepository.findAll().map { order ->  // 1 query
            OrderSummary(
                id = order.id,
                customerName = order.customer.name,  // N queries!
                itemCount = order.items.size  // N more queries!
            )
        }
    }
}

// ✅ Solution: Entity graph
@Entity
@NamedEntityGraph(
    name = "Order.withCustomerAndItems",
    attributeNodes = [
        NamedAttributeNode("customer"),
        NamedAttributeNode("items")
    ]
)
class Order { /* ... */ }

interface OrderRepository : JpaRepository<Order, Long> {
    @EntityGraph("Order.withCustomerAndItems")
    override fun findAll(): List<Order>
}

// ✅ Solution: Custom query with fetch join
@Query("""
    SELECT DISTINCT o FROM Order o
    LEFT JOIN FETCH o.customer
    LEFT JOIN FETCH o.items
""")
fun findAllWithDetails(): List<Order>
```

## Jackson Serialization

### Issue 8: Infinite Recursion

**Symptom:** StackOverflowError during JSON serialization

```kotlin
// ❌ Problem: Bidirectional relationship
@Entity
class User {
    @OneToMany(mappedBy = "user")
    val orders: List<Order> = listOf()
}

@Entity
class Order {
    @ManyToOne
    val user: User  // Circular reference!
}

// ✅ Solution 1: @JsonManagedReference / @JsonBackReference
@Entity
class User {
    @OneToMany(mappedBy = "user")
    @JsonManagedReference
    val orders: List<Order> = listOf()
}

@Entity
class Order {
    @ManyToOne
    @JsonBackReference
    val user: User
}

// ✅ Solution 2: Use DTOs (recommended)
data class UserDto(
    val id: Long,
    val name: String,
    val orderIds: List<Long>  // Only IDs, not full objects
)

fun User.toDto() = UserDto(
    id = id,
    name = name,
    orderIds = orders.map { it.id }
)
```

## Kotlin Specifics

### Issue 9: Data Class with JPA

**Symptom:** Hibernate proxy issues

```kotlin
// ❌ Problem: Data class equals/hashCode breaks with proxies
@Entity
data class User(  // ❌ Don't use data class with JPA
    @Id val id: Long,
    val name: String
)

// ✅ Solution: Regular class with proper equals/hashCode
@Entity
class User(
    @Id val id: Long? = null,
    var name: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id != null && id == other.id
    }
    
    override fun hashCode() = id?.hashCode() ?: 0
}
```

### Issue 10: Nullable vs lateinit

**Symptom:** Confusion between nullable types and lateinit

```kotlin
// ❌ Problem: Nullable when not needed
@Service
class UserService {
    @Autowired
    var userRepository: UserRepository? = null  // ❌ Should use lateinit
    
    fun getUser(id: Long) = userRepository?.findById(id)  // Unnecessary ?
}

// ✅ Solution: Use lateinit for autowired dependencies
@Service
class UserService {
    @Autowired
    lateinit var userRepository: UserRepository  // ✅ Will be initialized
    
    fun getUser(id: Long) = userRepository.findById(id)
}

// Use nullable only for truly optional values
@Entity
class User(
    val email: String,
    val nickname: String? = null  // Legitimately optional
)
```

## Debugging Checklist

When encountering an issue:

1. **Check logs first**
   - Application logs
   - SQL logs (if enabled)
   - Exception stack trace

2. **Verify configuration**
   - application.yml for active profile
   - Database connection settings
   - Bean configuration

3. **Check common patterns**
   - Transaction boundaries
   - Lazy loading
   - Connection pool
   - N+1 queries

4. **Use actuator endpoints**
   - /actuator/health
   - /actuator/metrics
   - /actuator/threaddump

5. **Add strategic logging**
   - Method entry/exit
   - Database query execution
   - Exception context
