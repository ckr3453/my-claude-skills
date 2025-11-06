# Entity Design Patterns & Decision Trees

## Quick Decision Matrix

| Concept | Has Lifecycle? | Standalone? | Mutable? | Result |
|---------|---|---|---|---|
| Email, Phone | No | No | No | @Embeddable Value Object |
| Address | No | No | No | @Embeddable Value Object |
| Order Status | No | No | No | Enum |
| OrderItem | Yes | No* | Yes | @Entity, @OneToMany |
| Order | Yes | Yes | Yes | @Entity, aggregate root |
| Product | Yes | Yes | Yes | @Entity, separate bounded context |
| Stock | Yes | Maybe | Yes | @Entity or @Embeddable (per business logic) |

*: Exists only with parent Order

---

## Pattern 1: Value Object (Immutable, Embedded)

**When to use:**
- No independent lifecycle
- Always validated
- Immutable (can't change fields)
- Examples: Email, Phone, Address, Money

```kotlin
@Embeddable
data class Email(
    @Column(nullable = false, unique = true, length = 100)
    val value: String
) {
    init {
        require(value.matches(EMAIL_REGEX)) { "Invalid email" }
    }
    
    companion object {
        private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$".toRegex()
        fun of(email: String) = Email(email)
    }
}

// Usage in Entity
@Entity
class Customer : BaseEntity() {
    @Embedded
    var email: Email = Email.of("...")
}

// Database: No separate table, just columns in customer table
// customer: {id, name, email, created_date, ...}
```

**Advantages:**
- ✅ No extra table/FK
- ✅ Always loaded (no N+1 risk)
- ✅ Validated at creation
- ✅ Type-safe (Email vs String)

---

## Pattern 2: Entity (Standalone, Full Lifecycle)

**When to use:**
- Has unique identity (ID)
- Independent lifecycle
- Can be queried directly
- Can be updated/deleted independently

```kotlin
@Entity(name = "product")
class Product(
    id: String? = null,
    
    @Column(nullable = false, length = 100)
    var name: String,
    
    @Column(nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,
    
    @Embedded
    var category: Category,
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: ProductStatus = ACTIVE
) : BaseEntity(id) {
    
    // Domain logic
    fun deactivate() {
        if (status == INACTIVE) return
        status = INACTIVE
    }
    
    companion object {
        fun of(request: CreateProductRequest): Product = Product(
            name = request.name,
            price = request.price,
            category = Category.of(request.categoryId)
        )
    }
}

// Database: Separate table with full audit
CREATE TABLE product (
    product_id VARCHAR(13) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    category_id VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_date TIMESTAMP NOT NULL,
    created_by VARCHAR(50),
    ...
);
```

**Has Repository:**
```kotlin
@Repository
interface ProductRepository : JpaRepository<Product, String> {
    fun findByName(name: String): Product?
    fun findAllByStatus(status: ProductStatus): List<Product>
}
```

---

## Pattern 3: Aggregate Root (Parent Entity with Children)

**When to use:**
- Clear parent-child ownership
- Children deleted with parent
- Parent validates children

```kotlin
@Entity(name = "order")
class Order(
    id: String? = null,
    
    @ManyToOne(fetch = LAZY, cascade = [PERSIST])
    var customer: Customer,
    
    @OneToMany(cascade = [ALL], orphanRemoval = true)
    @JoinColumn(name = "order_id")
    var items: MutableList<OrderItem> = mutableListOf(),
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: OrderStatus = PENDING
    
) : BaseEntity(id) {
    
    // Business logic on aggregate root
    fun addItem(product: Product, quantity: Int) {
        require(quantity > 0) { "Quantity must be positive" }
        require(status == PENDING) { "Cannot add items to shipped order" }
        
        val item = OrderItem(order = this, product = product, quantity = quantity)
        items.add(item)
    }
    
    fun cancel() {
        if (status == SHIPPED) {
            throw OrderCannotBeCancelledException(status)
        }
        status = CANCELLED
    }
    
    companion object {
        fun of(request: CreateOrderRequest, customer: Customer): Order = Order(
            customer = customer,
            items = mutableListOf()
        )
    }
}

@Entity(name = "order_item")
class OrderItem(
    id: String? = null,
    
    @ManyToOne(fetch = LAZY)
    var order: Order,
    
    @ManyToOne(fetch = LAZY)
    var product: Product,
    
    @Column(nullable = false)
    var quantity: Int
    
) : BaseEntity(id)
```

**Database:**
```sql
CREATE TABLE "order" (
    order_id VARCHAR(13) PRIMARY KEY,
    customer_id VARCHAR(13) NOT NULL,
    status VARCHAR(20) NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);

CREATE TABLE order_item (
    order_item_id VARCHAR(13) PRIMARY KEY,
    order_id VARCHAR(13) NOT NULL,
    product_id VARCHAR(13) NOT NULL,
    quantity INT NOT NULL,
    FOREIGN KEY (order_id) REFERENCES "order"(order_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES product(product_id)
);

CREATE INDEX idx_order_item_order ON order_item(order_id);
```

**Key Points:**
- ✅ Only `@OneToMany` has `cascade = [ALL], orphanRemoval = true`
- ✅ `@ManyToOne` typically has `cascade = [PERSIST]` (link to existing)
- ✅ `FETCH = LAZY` by default (no N+1)
- ✅ Business logic on root (Order.cancel(), Order.addItem())

---

## Pattern 4: Cross-Cutting Value Object (Embedded)

Sometimes the same Value Object appears in multiple entities:

```kotlin
// Used in Customer, Store, Warehouse, etc.
@Embeddable
data class Location(
    @Column(name = "latitude")
    val latitude: Double,
    
    @Column(name = "longitude")
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "Invalid latitude" }
        require(longitude in -180.0..180.0) { "Invalid longitude" }
    }
    
    fun distanceTo(other: Location): Double {
        // Haversine formula
    }
}

// In multiple entities
@Entity class Store : BaseEntity() {
    @Embedded(attributeOverrides = [
        AttributeOverride(name = "latitude", column = Column(name = "store_latitude")),
        AttributeOverride(name = "longitude", column = Column(name = "store_longitude"))
    ])
    var location: Location
}

@Entity class Warehouse : BaseEntity() {
    @Embedded(attributeOverrides = [
        AttributeOverride(name = "latitude", column = Column(name = "warehouse_latitude")),
        AttributeOverride(name = "longitude", column = Column(name = "warehouse_longitude"))
    ])
    var location: Location
}
```

---

## Pattern 5: Collection Value Objects (List of Embeddable)

**When to use:**
- Multiple instances of same value object
- No separate queries needed
- Loaded with parent

```kotlin
@Entity class ShippingInfo : BaseEntity() {
    @ElementCollection(fetch = LAZY)
    @CollectionTable(name = "shipping_address", joinColumns = [JoinColumn(name = "shipping_info_id")])
    var addresses: MutableList<Address> = mutableListOf()
}

@Embeddable
data class Address(
    val street: String,
    val city: String,
    val zipCode: String,
    val country: String,
    val isPrimary: Boolean = false
)
```

**Database:**
```sql
CREATE TABLE shipping_info (
    shipping_info_id VARCHAR(13) PRIMARY KEY
);

CREATE TABLE shipping_address (
    shipping_info_id VARCHAR(13) NOT NULL,
    street VARCHAR(200),
    city VARCHAR(100),
    zip_code VARCHAR(20),
    country VARCHAR(100),
    is_primary BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (shipping_info_id) REFERENCES shipping_info(shipping_info_id)
);
```

---

## Pattern 6: Spatial Data (Geospatial)

Common in dtxiotv3-like projects:

```kotlin
@Embeddable
data class GeoPoint(
    @Column(nullable = false)
    val latitude: Double,
    
    @Column(nullable = false)
    val longitude: Double,
    
    @Column(nullable = true)
    val elevation: Double? = null
) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
    }
}

@Entity class Site : BaseEntity() {
    @Embedded(attributeOverrides = [
        AttributeOverride(name = "latitude", column = Column(name = "site_latitude")),
        AttributeOverride(name = "longitude", column = Column(name = "site_longitude")),
        AttributeOverride(name = "elevation", column = Column(name = "site_elevation"))
    ])
    var location: GeoPoint
}

// Repository with spatial query
@Repository
interface SiteRepository : JpaRepository<Site, String> {
    @Query("SELECT s FROM Site s WHERE " +
           "FUNCTION('st_distance', s.location, " +
           "FUNCTION('st_point', ?1, ?2)) <= ?3")
    fun findNearby(lat: Double, lon: Double, radiusKm: Double): List<Site>
}
```

---

## Pattern 7: Enum vs String

**Use Enum when:**
- Fixed set of values
- Type-safe
- Domain concept (OrderStatus, PaymentMethod)

```kotlin
@Entity class Order : BaseEntity() {
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)  // Store as "PENDING", not "0"
    var status: OrderStatus = PENDING
}

enum class OrderStatus {
    PENDING, SHIPPED, DELIVERED, CANCELLED
}
```

**Use String when:**
- Dynamic/user-defined values
- Extensible set
- Configuration/tags

```kotlin
@Entity class Product : BaseEntity() {
    @Column(nullable = false, length = 50)
    var tag: String  // "electronics", "home", custom tags
}
```

---

## Pattern 8: Soft Delete (Temporal)

```kotlin
@Entity class Product : BaseEntity() {
    // Regular fields...
    
    @Column(nullable = true)
    var deletedAt: LocalDateTime? = null
    
    // Domain logic
    fun softDelete() {
        deletedAt = LocalDateTime.now()
    }
    
    fun restore() {
        deletedAt = null
    }
}

// Repository - always filter soft-deleted
@Repository
interface ProductRepository : JpaRepository<Product, String> {
    fun findByNameAndDeletedAtIsNull(name: String): Product?
    
    @Query("SELECT p FROM Product p WHERE p.deletedAt IS NULL AND p.status = :status")
    fun findActive(@Param("status") status: ProductStatus): List<Product>
}
```

---

## Composition Patterns

### Simple Aggregate (Parent + 1 Child Type)
```
Order (1) ─< (N) OrderItem
```
Use `@OneToMany` on Order, `@ManyToOne` on OrderItem

### Complex Aggregate (Parent + Multiple Child Types)
```
Order (1) ─< (N) OrderItem
Order (1) ─< (N) OrderNote
Order (1) ─< (N) OrderPayment
```
Each child has `@ManyToOne` to Order, Order has multiple `@OneToMany`

### Hierarchical Aggregate (3+ levels)
```
Site (1) ─< (N) Pipe (1) ─< (N) Channel (1) ─< (N) Sensor
```
Each level has `@ManyToOne` to parent, `@OneToMany` to children

### Many-to-Many (Linked via Join Table)
```
Product (N) ─<>─ (N) Category
```
```kotlin
@Entity class Product : BaseEntity() {
    @ManyToMany(cascade = [PERSIST])
    @JoinTable(
        name = "product_category",
        joinColumns = [JoinColumn(name = "product_id")],
        inverseJoinColumns = [JoinColumn(name = "category_id")]
    )
    var categories: MutableList<Category> = mutableListOf()
}
```

---

## Red Flags & Fixes

| Problem | Fix |
|---------|-----|
| Entity with 10+ fields | Split into aggregate or create value objects |
| All relationships EAGER | Change to LAZY, use fetch joins in queries |
| Circular dependencies | Unidirectional relationships, clear ownership |
| No validation on creation | Add init block or companion factory method |
| String IDs everywhere | Use @Tsid for unique, sortable IDs |
| Ignoring soft delete | Add deletedAt, filter in queries |
| No indexes on FKs | Add @Index on every @ManyToOne @Column |
