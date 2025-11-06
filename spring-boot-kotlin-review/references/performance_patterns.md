# Performance Optimization Patterns

## Database Query Optimization

### N+1 Query Problems

```kotlin
// ⚠️ Consider: N+1 query problem
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository
) {
    fun getAllOrdersWithItems(): List<OrderWithItems> {
        val orders = orderRepository.findAll()
        return orders.map { order ->
            val items = orderItemRepository.findByOrderId(order.id)  // N queries
            OrderWithItems(order, items)
        }
    }
}

// 💡 Alternative: Fetch join
interface OrderRepository : JpaRepository<OrderEntity, Long> {
    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.items")
    fun findAllWithItems(): List<OrderEntity>
}

// 💡 Alternative: @EntityGraph
interface OrderRepository : JpaRepository<OrderEntity, Long> {
    @EntityGraph(attributePaths = ["items", "customer"])
    fun findAll(): List<OrderEntity>
}
```

### Pagination

```kotlin
// ⚠️ Consider: Load all records
@RestController
class UserController(private val userRepository: UserRepository) {
    @GetMapping("/users")
    fun getAllUsers(): List<User> = userRepository.findAll()
}

// 💡 Alternative: Paginated
@RestController
class UserController(private val userRepository: UserRepository) {
    @GetMapping("/users")
    fun getAllUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "id") sort: String
    ): Page<User> {
        val pageable = PageRequest.of(page, size, Sort.by(sort))
        return userRepository.findAll(pageable)
    }
}
```

### Batch Operations

```kotlin
// ⚠️ Consider: Multiple individual saves
fun createUsers(users: List<User>) {
    users.forEach { user ->
        userRepository.save(user)  // N queries
    }
}

// 💡 Alternative: Batch save
fun createUsers(users: List<User>) {
    userRepository.saveAll(users)  // Single batch operation
}

// 💡 BEST: With batch size configuration
// application.yml:
# spring:
#   jpa:
#     properties:
#       hibernate:
#         jdbc:
#           batch_size: 50
#         order_inserts: true
#         order_updates: true

@Service
class BulkUserService(
    private val entityManager: EntityManager
) {
    @Transactional
    fun createUsers(users: List<User>) {
        users.chunked(50).forEach { batch ->
            batch.forEach { entityManager.persist(it) }
            entityManager.flush()
            entityManager.clear()
        }
    }
}
```

### Query Projections

```kotlin
// ⚠️ Consider: Load entire entity when only need few fields
interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findAll(): List<UserEntity>
}
val users = userRepository.findAll()
val names = users.map { it.name }  // Loaded all columns unnecessarily

// 💡 Alternative: Projection interface
interface UserNameProjection {
    val id: Long
    val name: String
}

interface UserRepository : JpaRepository<UserEntity, Long> {
    @Query("SELECT u.id as id, u.name as name FROM UserEntity u")
    fun findAllNames(): List<UserNameProjection>
}

// 💡 Alternative: DTO projection
data class UserNameDto(
    val id: Long,
    val name: String
)

interface UserRepository : JpaRepository<UserEntity, Long> {
    @Query("SELECT new com.example.UserNameDto(u.id, u.name) FROM UserEntity u")
    fun findAllNames(): List<UserNameDto>
}
```

### Database Indexes

```kotlin
// Entity with proper indexes
@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_email", columnList = "email"),
        Index(name = "idx_created_at", columnList = "created_at"),
        Index(name = "idx_status_created", columnList = "status,created_at")
    ]
)
class UserEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(unique = true, nullable = false)
    val email: String,
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val status: UserStatus,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

// Composite index for common query patterns
@Query("SELECT u FROM UserEntity u WHERE u.status = :status AND u.createdAt > :since")
fun findActiveUsersSince(status: UserStatus, since: LocalDateTime): List<UserEntity>
```

## Caching Strategies

### Simple Cache

```kotlin
@Service
@CacheConfig(cacheNames = ["users"])
class UserService(
    private val userRepository: UserRepository
) {
    @Cacheable(key = "#id")
    fun findById(id: Long): User? {
        return userRepository.findById(id).orElse(null)
    }
    
    @CachePut(key = "#user.id")
    fun update(user: User): User {
        return userRepository.save(user)
    }
    
    @CacheEvict(key = "#id")
    fun delete(id: Long) {
        userRepository.deleteById(id)
    }
}
```

### Cache with TTL

```kotlin
@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    fun cacheManager(): CacheManager {
        return CaffeineCacheManager().apply {
            setCaffeine(
                Caffeine.newBuilder()
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .maximumSize(1000)
            )
        }
    }
}
```

### Distributed Cache with Redis

```kotlin
@Configuration
class RedisCacheConfig {
    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): CacheManager {
        val config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    GenericJackson2JsonRedisSerializer()
                )
            )
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build()
    }
}
```

## Memory Management

### Streaming Large Results

```kotlin
// ⚠️ Consider: Load all into memory
@Service
class ReportService(
    private val userRepository: UserRepository
) {
    fun generateReport(): ByteArray {
        val users = userRepository.findAll()  // OOM risk with millions of records
        return users.map { formatUser(it) }
            .joinToString("\n")
            .toByteArray()
    }
}

// 💡 Alternative: Stream results
@Service
class ReportService(
    private val userRepository: UserRepository
) {
    @Transactional(readOnly = true)
    fun generateReport(outputStream: OutputStream) {
        userRepository.streamAll().use { stream ->
            stream.forEach { user ->
                outputStream.write(formatUser(user).toByteArray())
                outputStream.write("\n".toByteArray())
            }
        }
    }
}

interface UserRepository : JpaRepository<UserEntity, Long> {
    @QueryHints(QueryHint(name = HINT_FETCH_SIZE, value = "50"))
    @Query("SELECT u FROM UserEntity u")
    fun streamAll(): Stream<UserEntity>
}
```

### Lazy Loading

```kotlin
// ⚠️ Consider: Eager loading of large collections
@Entity
class Order(
    @Id val id: Long,
    @OneToMany(fetch = FetchType.EAGER)  // Loads all items always
    val items: List<OrderItem>
)

// 💡 Alternative: Lazy loading with explicit fetch when needed
@Entity
class Order(
    @Id val id: Long,
    @OneToMany(fetch = FetchType.LAZY)
    val items: List<OrderItem>
)

// Fetch explicitly when needed
@Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
fun findByIdWithItems(@Param("id") id: Long): Order?
```

## Asynchronous Processing

### Async Methods

```kotlin
@Configuration
@EnableAsync
class AsyncConfig {
    @Bean
    fun taskExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 5
            maxPoolSize = 10
            queueCapacity = 100
            setThreadNamePrefix("async-")
            initialize()
        }
    }
}

@Service
class NotificationService {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @Async
    fun sendEmail(userId: Long) {
        logger.info("Sending email to user: $userId")
        // Email sending logic
    }
    
    @Async
    fun sendPushNotification(userId: Long) {
        logger.info("Sending push notification to user: $userId")
        // Push notification logic
    }
}

// Usage - fires and forgets
@Service
class UserService(
    private val notificationService: NotificationService
) {
    fun registerUser(user: User): User {
        val savedUser = userRepository.save(user)
        notificationService.sendEmail(savedUser.id)  // Non-blocking
        notificationService.sendPushNotification(savedUser.id)  // Non-blocking
        return savedUser
    }
}
```

### CompletableFuture

```kotlin
@Service
class AggregationService(
    private val userService: UserService,
    private val orderService: OrderService,
    private val reviewService: ReviewService
) {
    fun getUserDashboard(userId: Long): UserDashboard {
        val userFuture = CompletableFuture.supplyAsync { 
            userService.findById(userId) 
        }
        val ordersFuture = CompletableFuture.supplyAsync { 
            orderService.findByUserId(userId) 
        }
        val reviewsFuture = CompletableFuture.supplyAsync { 
            reviewService.findByUserId(userId) 
        }
        
        return CompletableFuture.allOf(userFuture, ordersFuture, reviewsFuture)
            .thenApply {
                UserDashboard(
                    user = userFuture.get(),
                    orders = ordersFuture.get(),
                    reviews = reviewsFuture.get()
                )
            }.get()
    }
}
```

## Algorithm Optimization

### Avoid O(n²) Complexity

```kotlin
// ⚠️ Consider: O(n²) nested loops
fun findDuplicates(users: List<User>): List<User> {
    val duplicates = mutableListOf<User>()
    for (i in users.indices) {
        for (j in i + 1 until users.size) {
            if (users[i].email == users[j].email) {
                duplicates.add(users[i])
            }
        }
    }
    return duplicates
}

// 💡 Alternative: O(n) with Set
fun findDuplicates(users: List<User>): List<User> {
    val seen = mutableSetOf<String>()
    val duplicates = mutableListOf<User>()
    
    users.forEach { user ->
        if (!seen.add(user.email)) {
            duplicates.add(user)
        }
    }
    return duplicates
}

// 💡 BEST: O(n) with grouping
fun findDuplicates(users: List<User>): List<User> {
    return users.groupBy { it.email }
        .filter { it.value.size > 1 }
        .flatMap { it.value }
}
```

### Early Exit

```kotlin
// ⚠️ Consider: Process all elements
fun hasActiveUser(users: List<User>): Boolean {
    return users.filter { it.isActive }.isNotEmpty()
}

// 💡 Alternative: Exit early
fun hasActiveUser(users: List<User>): Boolean {
    return users.any { it.isActive }
}
```

## Connection Pool Configuration

```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
```

## Query Performance Monitoring

```kotlin
@Component
class QueryPerformanceInterceptor : Interceptor {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val threshold = 1000L // 1 second
    
    override fun onLoad(
        entity: Any?,
        id: Serializable?,
        state: Array<out Any>?,
        propertyNames: Array<out String>?,
        types: Array<out Type>?
    ): Boolean {
        val start = System.currentTimeMillis()
        return super.onLoad(entity, id, state, propertyNames, types).also {
            val duration = System.currentTimeMillis() - start
            if (duration > threshold) {
                logger.warn("Slow query detected: ${entity?.javaClass?.simpleName} took ${duration}ms")
            }
        }
    }
}
```
