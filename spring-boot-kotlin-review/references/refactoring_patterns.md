# Common Code Smells and Refactoring Solutions

## How to Use This Guide

**Important:** These are patterns and trade-offs, not absolute rules.

**Legend:**
- 🚨 **Critical**: Security/data integrity issue - must fix
- ⚠️ **Consider**: Potential problem - evaluate based on context
- 💡 **Option**: Alternative approach - choose what fits your needs
- 🤔 **Trade-off**: Multiple valid solutions exist

**When to Apply:**
- Team agrees on the pattern
- Improves code quality measurably
- Doesn't break existing functionality
- Worth the refactoring time

## Long Method

### Problem
Methods that are too long and do too many things.

```kotlin
// ⚠️ Consider: Long method doing multiple things
class OrderService {
    fun processOrder(orderId: Long) {
        // Validate order
        val order = orderRepository.findById(orderId).orElseThrow()
        if (order.status != OrderStatus.PENDING) {
            throw IllegalStateException("Order is not pending")
        }
        if (order.items.isEmpty()) {
            throw IllegalStateException("Order has no items")
        }
        
        // Check inventory
        order.items.forEach { item ->
            val available = inventoryRepository.findByProductId(item.productId)
            if (available.quantity < item.quantity) {
                throw IllegalStateException("Insufficient inventory")
            }
        }
        
        // Process payment
        val paymentRequest = PaymentRequest(
            amount = order.totalAmount,
            currency = order.currency,
            customerId = order.customerId
        )
        val payment = paymentGateway.process(paymentRequest)
        
        // Update inventory
        order.items.forEach { item ->
            inventoryRepository.decreaseQuantity(item.productId, item.quantity)
        }
        
        // Update order
        order.status = OrderStatus.COMPLETED
        order.paymentId = payment.id
        orderRepository.save(order)
        
        // Send notification
        emailService.sendOrderConfirmation(order.customerId, order.id)
    }
}
```

### Solution: Extract Methods

```kotlin
// 💡 Alternative: Extracted to focused methods
class ProcessOrderUseCase(
    private val validateOrder: ValidateOrderUseCase,
    private val checkInventory: CheckInventoryUseCase,
    private val processPayment: ProcessPaymentUseCase,
    private val updateInventory: UpdateInventoryUseCase,
    private val completeOrder: CompleteOrderUseCase,
    private val notifyCustomer: NotifyCustomerUseCase
) {
    @Transactional
    fun execute(orderId: Long) {
        val order = validateOrder.execute(orderId)
        checkInventory.execute(order)
        val payment = processPayment.execute(order)
        updateInventory.execute(order)
        completeOrder.execute(order, payment)
        notifyCustomer.execute(order)
    }
}
```

## Large Class

### Problem
Class with too many responsibilities.

```kotlin
// ⚠️ Consider: God class doing everything
class UserService(
    private val userRepository: UserRepository,
    private val emailService: EmailService,
    private val smsService: SmsService,
    private val authService: AuthService,
    private val profileService: ProfileService,
    private val subscriptionService: SubscriptionService
) {
    fun createUser(dto: CreateUserDto): User { /* ... */ }
    fun updateUser(id: Long, dto: UpdateUserDto): User { /* ... */ }
    fun deleteUser(id: Long) { /* ... */ }
    fun sendWelcomeEmail(userId: Long) { /* ... */ }
    fun sendPasswordResetSms(userId: Long) { /* ... */ }
    fun authenticate(email: String, password: String): Token { /* ... */ }
    fun updateProfile(userId: Long, profile: Profile) { /* ... */ }
    fun subscribe(userId: Long, planId: Long) { /* ... */ }
    fun cancelSubscription(userId: Long) { /* ... */ }
    // ... 20+ more methods
}
```

### Solution: Split by Responsibility

```kotlin
// 💡 Alternative: Single responsibility classes
class CreateUserUseCase(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    fun execute(command: CreateUserCommand): User {
        val user = User.create(
            email = command.email,
            password = passwordEncoder.encode(command.password),
            name = command.name
        )
        return userRepository.save(user)
    }
}

class UserNotificationService(
    private val emailService: EmailService,
    private val smsService: SmsService
) {
    fun sendWelcomeEmail(userId: Long) { /* ... */ }
    fun sendPasswordResetSms(userId: Long) { /* ... */ }
}

class AuthenticationService(
    private val userRepository: UserRepository,
    private val tokenService: TokenService
) {
    fun authenticate(credentials: Credentials): Token { /* ... */ }
}
```

## Primitive Obsession

### Problem
Using primitives instead of small objects for simple tasks.

```kotlin
// ⚠️ Consider: Primitives everywhere
class User(
    val id: Long,
    val email: String,  // Should be Email value object
    val phoneNumber: String,  // Should be PhoneNumber value object
    val countryCode: String,  // Should be part of Address
    val city: String,  // Should be part of Address
    val street: String,  // Should be part of Address
    val zipCode: String  // Should be part of Address
)

fun sendEmail(email: String) {
    if (!email.contains("@")) {
        throw IllegalArgumentException("Invalid email")
    }
    // Validation logic repeated everywhere email is used
}
```

### Solution: Value Objects

```kotlin
// 💡 Alternative: Value objects
@Embeddable
data class Email(
    @Column(name = "email")
    private val value: String
) {
    init {
        require(value.contains("@")) { "Invalid email format" }
        require(value.length <= 255) { "Email too long" }
    }
    
    override fun toString() = value
}

@Embeddable
data class PhoneNumber(
    @Column(name = "phone_number")
    private val value: String
) {
    init {
        require(value.matches(Regex("^\\+?[1-9]\\d{1,14}$"))) { 
            "Invalid phone number format" 
        }
    }
    
    override fun toString() = value
}

@Embeddable
data class Address(
    @Column(name = "country_code")
    val countryCode: String,
    
    @Column(name = "city")
    val city: String,
    
    @Column(name = "street")
    val street: String,
    
    @Column(name = "zip_code")
    val zipCode: String
) {
    init {
        require(countryCode.length == 2) { "Country code must be 2 characters" }
        require(city.isNotBlank()) { "City is required" }
    }
}

@Entity
class User(
    @Id val id: Long,
    @Embedded val email: Email,
    @Embedded val phoneNumber: PhoneNumber,
    @Embedded val address: Address
)
```

## Feature Envy

### Problem
Method that seems more interested in a class other than the one it's in.

```kotlin
// ⚠️ Consider: OrderService envying Order's data
class OrderService {
    fun calculateDiscount(order: Order): BigDecimal {
        var discount = BigDecimal.ZERO
        
        if (order.items.size > 5) {
            discount = discount.add(order.totalAmount.multiply(BigDecimal("0.1")))
        }
        
        if (order.customer.isPremium) {
            discount = discount.add(order.totalAmount.multiply(BigDecimal("0.05")))
        }
        
        if (order.totalAmount > BigDecimal("1000")) {
            discount = discount.add(order.totalAmount.multiply(BigDecimal("0.15")))
        }
        
        return discount
    }
}
```

### Solution: Move Method

```kotlin
// 💡 Alternative: Behavior where data is
class Order(
    val items: List<OrderItem>,
    val customer: Customer,
    val totalAmount: BigDecimal
) {
    fun calculateDiscount(): BigDecimal {
        var discount = BigDecimal.ZERO
        
        if (items.size > 5) {
            discount += totalAmount * BigDecimal("0.1")
        }
        
        if (customer.isPremium) {
            discount += totalAmount * BigDecimal("0.05")
        }
        
        if (totalAmount > BigDecimal("1000")) {
            discount += totalAmount * BigDecimal("0.15")
        }
        
        return discount
    }
    
    fun finalAmount() = totalAmount - calculateDiscount()
}
```

## Data Clumps

### Problem
Groups of data that always appear together.

```kotlin
// ⚠️ Consider: Repeated parameter groups
class PaymentService {
    fun processPayment(
        amount: BigDecimal,
        currency: String,
        customerId: Long,
        customerName: String,
        customerEmail: String
    ): Payment { /* ... */ }
    
    fun refundPayment(
        amount: BigDecimal,
        currency: String,
        customerId: Long,
        customerName: String,
        customerEmail: String
    ): Refund { /* ... */ }
}
```

### Solution: Extract Class

```kotlin
// 💡 Alternative: Data clump as object
data class Money(
    val amount: BigDecimal,
    val currency: String
) {
    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Currency mismatch" }
        return Money(amount + other.amount, currency)
    }
}

data class CustomerInfo(
    val id: Long,
    val name: String,
    val email: String
)

class PaymentService {
    fun processPayment(money: Money, customer: CustomerInfo): Payment { /* ... */ }
    fun refundPayment(money: Money, customer: CustomerInfo): Refund { /* ... */ }
}
```

## Switch Statements

### Problem
Complex conditional logic that should be polymorphic.

```kotlin
// ⚠️ Consider: Switch on type
class ShippingCalculator {
    fun calculateCost(order: Order): BigDecimal {
        return when (order.shippingMethod) {
            "STANDARD" -> {
                order.weight * BigDecimal("2.0")
            }
            "EXPRESS" -> {
                order.weight * BigDecimal("5.0") + BigDecimal("10.0")
            }
            "OVERNIGHT" -> {
                order.weight * BigDecimal("10.0") + BigDecimal("25.0")
            }
            else -> throw IllegalArgumentException("Unknown shipping method")
        }
    }
}
```

### Solution: Polymorphism

```kotlin
// 💡 Alternative: Polymorphic strategy
sealed class ShippingStrategy {
    abstract fun calculateCost(weight: BigDecimal): BigDecimal
    
    object Standard : ShippingStrategy() {
        override fun calculateCost(weight: BigDecimal) = 
            weight * BigDecimal("2.0")
    }
    
    object Express : ShippingStrategy() {
        override fun calculateCost(weight: BigDecimal) = 
            weight * BigDecimal("5.0") + BigDecimal("10.0")
    }
    
    object Overnight : ShippingStrategy() {
        override fun calculateCost(weight: BigDecimal) = 
            weight * BigDecimal("10.0") + BigDecimal("25.0")
    }
}

class Order(
    val weight: BigDecimal,
    val shippingStrategy: ShippingStrategy
) {
    fun calculateShippingCost() = shippingStrategy.calculateCost(weight)
}
```

## Null Checks Everywhere

### Problem
Excessive null checking throughout the codebase.

```kotlin
// ⚠️ Consider: Null checks everywhere
fun processUser(userId: Long?): String {
    if (userId == null) {
        return "Unknown"
    }
    
    val user = userRepository.findById(userId).orElse(null)
    if (user == null) {
        return "Unknown"
    }
    
    val name = user.name
    if (name == null) {
        return "Unknown"
    }
    
    return name
}
```

### Solution: Null Safety & Default Values

```kotlin
// 💡 Alternative: Kotlin null safety
fun processUser(userId: Long): String {
    return userRepository.findById(userId)
        .map { it.name }
        .orElse("Unknown")
}

// 💡 Alternative: Non-null types
@Entity
class User(
    @Id val id: Long,
    @Column(nullable = false)
    val name: String,  // Non-null
    @Column(nullable = true)
    val nickname: String? = null  // Explicitly nullable
)

// 💡 Alternative: Elvis operator
fun getDisplayName(user: User): String {
    return user.nickname ?: user.name
}
```

## Magic Numbers

### Problem
Unnamed numerical constants in code.

```kotlin
// ⚠️ Consider: Magic numbers
class OrderValidator {
    fun validate(order: Order): Boolean {
        if (order.items.size > 100) {  // What is 100?
            return false
        }
        if (order.totalAmount > BigDecimal("50000")) {  // What is 50000?
            return false
        }
        if (order.customerId < 1000000) {  // What is 1000000?
            return false
        }
        return true
    }
}
```

### Solution: Named Constants

```kotlin
// 💡 Alternative: Named constants
class OrderValidator {
    companion object {
        private const val MAX_ITEMS_PER_ORDER = 100
        private val MAX_ORDER_AMOUNT = BigDecimal("50000")
        private const val LEGACY_CUSTOMER_ID_THRESHOLD = 1000000
    }
    
    fun validate(order: Order): Boolean {
        if (order.items.size > MAX_ITEMS_PER_ORDER) {
            return false
        }
        if (order.totalAmount > MAX_ORDER_AMOUNT) {
            return false
        }
        if (order.customerId < LEGACY_CUSTOMER_ID_THRESHOLD) {
            return false
        }
        return true
    }
}
```

## Anemic Domain Model

### Context
⚠️ **Consider** - This is problematic mainly in complex domains with rich business logic.

### When It's Actually a Problem
- Complex state transitions (Order: Pending → Processing → Shipped → Delivered)
- Multiple business rules per entity (Pricing, discounts, validation)
- Domain-driven design (DDD) approach
- Entities collaborating with complex interactions

### When It's Acceptable or Even Preferred
- Simple CRUD operations with minimal logic
- Read models in CQRS (Command Query Responsibility Segregation)
- Data transfer between systems
- Reporting/Analytics domains
- Microservices with transaction script pattern
- Teams more comfortable with procedural style

### Pattern: Anemic Model with Service Layer

```kotlin
// 📋 PATTERN A: Anemic Model + Service
// 💡 Good for: Simple CRUD, read-heavy systems, junior teams

@Entity
class Order(
    @Id var id: Long = 0,
    var customerId: Long = 0,
    var status: String = "PENDING",
    var totalAmount: BigDecimal = BigDecimal.ZERO,
    @OneToMany var items: MutableList<OrderItem> = mutableListOf()
)

// Business logic in service
class OrderService {
    fun cancelOrder(orderId: Long) {
        val order = orderRepository.findById(orderId).get()
        if (order.status == "SHIPPED" || order.status == "DELIVERED") {
            throw IllegalStateException("Cannot cancel shipped order")
        }
        order.status = "CANCELLED"
        orderRepository.save(order)
    }
}
```

### Alternative: Rich Domain Model

```kotlin
// 📋 PATTERN B: Rich Domain Model
// 💡 Good for: Complex business rules, DDD, high code reuse

@Entity
class Order private constructor(
    @Id val id: Long = 0,
    val customerId: Long,
    @Enumerated(EnumType.STRING)
    private var status: OrderStatus,
    private var totalAmount: BigDecimal,
    @OneToMany(cascade = [CascadeType.ALL])
    private val items: MutableList<OrderItem>
) {
    companion object {
        fun create(customerId: Long, items: List<OrderItem>): Order {
            require(items.isNotEmpty()) { "Order must have at least one item" }
            return Order(
                customerId = customerId,
                status = OrderStatus.PENDING,
                totalAmount = items.sumOf { it.price * it.quantity.toBigDecimal() },
                items = items.toMutableList()
            )
        }
    }
    
    fun cancel() {
        require(status.canTransitionTo(OrderStatus.CANCELLED)) {
            "Cannot cancel order in status: $status"
        }
        status = OrderStatus.CANCELLED
    }
    
    fun ship() {
        require(status.canTransitionTo(OrderStatus.SHIPPED)) {
            "Cannot ship order in status: $status"
        }
        status = OrderStatus.SHIPPED
    }
    
    fun getStatus(): OrderStatus = status
    fun getTotalAmount(): BigDecimal = totalAmount
}

enum class OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED;
    
    fun canTransitionTo(newStatus: OrderStatus): Boolean {
        return when (this) {
            PENDING -> newStatus in setOf(CONFIRMED, CANCELLED)
            CONFIRMED -> newStatus in setOf(SHIPPED, CANCELLED)
            SHIPPED -> newStatus == DELIVERED
            DELIVERED, CANCELLED -> false
        }
    }
}
```

### 🤔 Trade-offs

| Aspect | Anemic Model | Rich Model |
|--------|--------------|------------|
| **Simplicity** | ✅ Easier to understand | ⚠️ More concepts |
| **Testing** | ⚠️ Service tests need mocks | ✅ Pure domain tests |
| **Reusability** | ⚠️ Logic scattered | ✅ Behavior travels with data |
| **Team Onboarding** | ✅ Familiar to most | ⚠️ Requires DDD knowledge |
| **CRUD Operations** | ✅ Very straightforward | ⚠️ May be overkill |
| **Complex Rules** | ⚠️ Gets messy quickly | ✅ Well encapsulated |

### 💡 Decision Framework

```
Is your business logic complex? (>5 rules per entity)
├─ NO  → Anemic model is fine
│        Simple CRUD, use services
│
└─ YES → Consider rich domain model
         ├─ Team comfortable with DDD? 
         │  ├─ YES → Rich model
         │  └─ NO  → Start anemic, refactor later
         │
         └─ Time for refactoring?
            ├─ YES → Rich model
            └─ NO  → Anemic model, add TODO
```

