# Test Patterns for Spring Boot Kotlin

This guide provides comprehensive patterns for writing effective tests in the TDD cycle.

## 1. Basic Test Structure

### @Nested Pattern for Organization

```kotlin
@SpringBootTest
@ExtendWith(MockitoExtension::class)
class InventoryServiceTest {
    
    @Mock
    private lateinit var repository: InventoryRepository
    
    @InjectMocks
    private lateinit var service: InventoryService
    
    @Nested
    inner class WhenCreatingInventory {
        
        @BeforeEach
        fun setup() {
            // Reset mocks
            reset(repository)
        }
        
        @Test
        fun shouldCreateWithValidData() {
            // Arrange
            val request = InventoryRequest(
                name = "Item A",
                code = "A001",
                quantity = 100
            )
            val expectedResponse = InventoryResponse(
                id = 1L,
                name = "Item A",
                code = "A001",
                quantity = 100
            )
            given(repository.save(any()))
                .willReturn(expectedResponse.toEntity())
            
            // Act
            val result = service.create(request)
            
            // Assert
            assertThat(result)
                .isNotNull
                .extracting("id", "code")
                .contains(1L, "A001")
            
            verify(repository, times(1)).save(any())
        }
        
        @Test
        fun shouldThrowExceptionWhenCodeDuplicate() {
            // Arrange
            val request = InventoryRequest(
                name = "Item B",
                code = "B001",
                quantity = 50
            )
            given(repository.findByCode("B001"))
                .willReturn(Optional.of(Inventory(...)))
            
            // Act & Assert
            assertThatThrownBy { service.create(request) }
                .isInstanceOf(DuplicateCodeException::class.java)
                .hasMessage("Code B001 already exists")
            
            verify(repository, never()).save(any())
        }
    }
    
    @Nested
    inner class WhenUpdatingInventory {
        // More test methods...
    }
}
```

## 2. Mocking Strategies

### MockK vs Mockito

**Use MockK for Kotlin-first code:**

```kotlin
@Test
fun testWithMockK() {
    val mock = mockk<InventoryRepository>()
    
    every { mock.findById(1L) } returns Optional.of(inventory)
    
    // Test code
    
    verify { mock.findById(1L) }
}
```

**Use @MockBean for Spring Boot integration:**

```kotlin
@SpringBootTest
class InventoryControllerTest {
    
    @MockBean
    private lateinit var service: InventoryService
    
    @Test
    fun testEndpoint() {
        given(service.getById(1L))
            .willReturn(InventoryResponse(...))
        
        // Test controller endpoint
    }
}
```

### Spying Pattern

```kotlin
@Test
fun testWithSpy() {
    val real = InventoryService(realRepository)
    val spy = spyk(real)
    
    // Call real method but track invocations
    every { spy.validate(any()) } returns Unit
    
    spy.create(request)
    
    verify { spy.validate(any()) }
}
```

## 3. Testing Complex Relationships

### Many-to-One Relationship

```kotlin
@Nested
inner class WhenHandlingWarehouseRelationship {
    
    @Test
    fun shouldCreateWithValidWarehouse() {
        // Arrange
        val warehouse = Warehouse(id = 1L, name = "Main")
        given(warehouseRepository.findById(1L))
            .willReturn(Optional.of(warehouse))
        
        val request = InventoryRequest(
            name = "Item",
            code = "I001",
            quantity = 100,
            warehouseId = 1L
        )
        
        // Act
        val result = service.create(request)
        
        // Assert
        assertThat(result.warehouse.id).isEqualTo(1L)
    }
    
    @Test
    fun shouldThrowExceptionWhenWarehouseNotFound() {
        given(warehouseRepository.findById(99L))
            .willReturn(Optional.empty())
        
        assertThatThrownBy { 
            service.create(requestWithWarehouse(99L))
        }.isInstanceOf(WarehouseNotFoundException::class.java)
    }
}
```

### One-to-Many Relationship

```kotlin
@Test
fun shouldFetchWithMovementHistory() {
    // Arrange
    val inventory = Inventory(id = 1L, ...)
    val movements = listOf(
        InventoryMovement(...),
        InventoryMovement(...)
    )
    
    given(repository.findById(1L))
        .willReturn(Optional.of(inventory))
    given(movementRepository.findByInventoryId(1L))
        .willReturn(movements)
    
    // Act
    val result = service.getWithMovements(1L)
    
    // Assert
    assertThat(result.movements).hasSize(2)
}
```

### Many-to-Many Relationship

```kotlin
@Test
fun shouldHandleGroupMembers() {
    // Arrange
    val group = Group(id = 1L, name = "Admins")
    val members = setOf(
        User(id = 1L, email = "admin1@test.com"),
        User(id = 2L, email = "admin2@test.com")
    )
    group.members = members
    
    given(groupRepository.findById(1L))
        .willReturn(Optional.of(group))
    
    // Act
    val result = service.getGroupWithMembers(1L)
    
    // Assert
    assertThat(result.members).hasSize(2)
}
```

## 4. Testing Validations

### Business Rule Violations

```kotlin
@Nested
inner class WhenValidatingBusinessRules {
    
    @Test
    fun shouldNotAllowNegativeQuantity() {
        val request = InventoryRequest(
            name = "Item",
            code = "I001",
            quantity = -10
        )
        
        assertThatThrownBy { service.create(request) }
            .isInstanceOf(InvalidQuantityException::class.java)
            .hasMessage("Quantity cannot be negative")
    }
    
    @Test
    fun shouldNotDeleteIfQuantityGreaterThanZero() {
        // Arrange
        val inventory = Inventory(id = 1L, quantity = 50)
        given(repository.findById(1L))
            .willReturn(Optional.of(inventory))
        
        // Act & Assert
        assertThatThrownBy { service.delete(1L) }
            .isInstanceOf(CannotDeleteException::class.java)
            .hasMessage("Cannot delete inventory with quantity > 0")
    }
    
    @Test
    fun shouldAllowDeleteIfQuantityIsZero() {
        // Arrange
        val inventory = Inventory(id = 1L, quantity = 0)
        given(repository.findById(1L))
            .willReturn(Optional.of(inventory))
        
        // Act
        service.delete(1L)
        
        // Assert
        verify(repository).delete(inventory)
    }
}
```

## 5. Testing Edge Cases

### Null and Empty Cases

```kotlin
@Nested
inner class WhenHandlingEdgeCases {
    
    @Test
    fun shouldHandleNullName() {
        val request = InventoryRequest(
            name = null,
            code = "I001",
            quantity = 100
        )
        
        assertThatThrownBy { service.create(request) }
            .isInstanceOf(ValidationException::class.java)
    }
    
    @Test
    fun shouldHandleEmptyString() {
        val request = InventoryRequest(
            name = "",
            code = "I001",
            quantity = 100
        )
        
        assertThatThrownBy { service.create(request) }
            .isInstanceOf(ValidationException::class.java)
    }
    
    @Test
    fun shouldHandleZeroQuantity() {
        val request = InventoryRequest(
            name = "Item",
            code = "I001",
            quantity = 0
        )
        
        // Should be allowed (zero is valid, negative is not)
        assertDoesNotThrow { service.create(request) }
    }
}
```

### Boundary Values

```kotlin
@Test
fun shouldHandleBoundaryValues() {
    val cases = listOf(
        Pair(Long.MAX_VALUE, true),  // Should pass
        Pair(Long.MIN_VALUE, false), // Should fail
        Pair(0L, true),
        Pair(1L, true)
    )
    
    cases.forEach { (value, shouldPass) ->
        val request = InventoryRequest(
            name = "Item",
            code = "I001",
            quantity = value
        )
        
        if (shouldPass) {
            assertDoesNotThrow { service.create(request) }
        } else {
            assertThatThrownBy { service.create(request) }
                .isInstanceOf(ValidationException::class.java)
        }
    }
}
```

## 6. Testing State Transitions

### State Machine Testing

```kotlin
@Nested
inner class WhenTransitioningOrderStatus {
    
    @Test
    fun shouldTransitionFromPendingToConfirmed() {
        // Arrange
        val order = Order(id = 1L, status = OrderStatus.PENDING)
        given(repository.findById(1L))
            .willReturn(Optional.of(order))
        given(paymentService.verifyPayment(1L))
            .willReturn(true)
        
        // Act
        val result = service.confirm(1L)
        
        // Assert
        assertThat(result.status)
            .isEqualTo(OrderStatus.CONFIRMED)
        
        verify(repository).save(any())
    }
    
    @Test
    fun shouldNotAllowInvalidTransition() {
        // Arrange
        val order = Order(id = 1L, status = OrderStatus.DELIVERED)
        given(repository.findById(1L))
            .willReturn(Optional.of(order))
        
        // Act & Assert
        assertThatThrownBy { service.confirm(1L) }
            .isInstanceOf(InvalidStateTransitionException::class.java)
            .hasMessage("Cannot confirm order in DELIVERED state")
    }
    
    @Test
    fun shouldEnforceStateTransitionConditions() {
        // Test that transition requires payment verification
        val order = Order(id = 1L, status = OrderStatus.PENDING)
        given(repository.findById(1L))
            .willReturn(Optional.of(order))
        given(paymentService.verifyPayment(1L))
            .willReturn(false)
        
        assertThatThrownBy { service.confirm(1L) }
            .isInstanceOf(PaymentNotVerifiedException::class.java)
    }
}
```

## 7. Testing Exception Handling

### Custom Exception Testing

```kotlin
@Test
fun shouldThrowCustomExceptionWithContext() {
    val request = InventoryRequest(...)
    given(repository.findByCode("I001"))
        .willReturn(Optional.of(existing))
    
    assertThatThrownBy { service.create(request) }
        .isInstanceOf(DuplicateCodeException::class.java)
        .hasMessage("Code I001 already exists")
        .hasCauseInstanceOf(DataIntegrityViolationException::class.java)
}
```

## 8. Fixture Patterns

### Builder Pattern

```kotlin
data class InventoryBuilder(
    var id: Long? = null,
    var name: String = "Item",
    var code: String = "I001",
    var quantity: Long = 100,
    var warehouseId: Long = 1L
) {
    fun build(): Inventory = Inventory(
        id = id,
        name = name,
        code = code,
        quantity = quantity,
        warehouseId = warehouseId
    )
}

// Usage
val inventory = InventoryBuilder(
    name = "Custom Item",
    quantity = 50
).build()
```

### Factory Pattern

```kotlin
object InventoryFactory {
    fun createDefault(): Inventory = Inventory(
        id = 1L,
        name = "Default Item",
        code = "DEFAULT",
        quantity = 100
    )
    
    fun createWithCode(code: String): Inventory = Inventory(
        id = 1L,
        name = "Item",
        code = code,
        quantity = 100
    )
}

// Usage
val inventory = InventoryFactory.createWithCode("SPECIAL")
```

## 9. Integration Testing with Testcontainers

```kotlin
@SpringBootTest
class InventoryServiceIntegrationTest {
    
    companion object {
        @Container
        private val database = PostgreSQLContainer<Nothing>("postgres:14")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
        
        @DynamicPropertySource
        @JvmStatic
        fun configureDatabase(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", database::getJdbcUrl)
            registry.add("spring.datasource.username", database::getUsername)
            registry.add("spring.datasource.password", database::getPassword)
        }
    }
    
    @Autowired
    private lateinit var service: InventoryService
    
    @Autowired
    private lateinit var repository: InventoryRepository
    
    @Test
    fun shouldCreateAndRetrieveFromDatabase() {
        // Arrange
        val request = InventoryRequest(...)
        
        // Act
        val created = service.create(request)
        val retrieved = repository.findById(created.id)
        
        // Assert
        assertThat(retrieved).isPresent
        assertThat(retrieved.get().code).isEqualTo(created.code)
    }
}
```

## 10. Performance Testing

```kotlin
@Test
fun shouldHandleLargeDatasetEfficiently() {
    // Arrange
    val largeDataset = (1..10000).map {
        Inventory(name = "Item $it", code = "I$it", quantity = 100L)
    }
    repository.saveAll(largeDataset)
    
    // Act
    val startTime = System.currentTimeMillis()
    val results = service.searchByCode("I5")
    val endTime = System.currentTimeMillis()
    
    // Assert
    assertThat(endTime - startTime)
        .isLessThan(200) // Should complete in < 200ms
    assertThat(results).isNotEmpty
}
```

---

These patterns should cover most testing scenarios in your TDD development cycle.
