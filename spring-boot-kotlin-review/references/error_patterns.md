# Exception Handling Best Practices

## Domain Exception Hierarchy

```kotlin
// Base domain exception
sealed class DomainException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

// Business rule violations
class BusinessRuleViolationException(
    message: String,
    val rule: String,
    cause: Throwable? = null
) : DomainException(message, cause)

// Resource not found
class ResourceNotFoundException(
    message: String,
    val resourceType: String,
    val resourceId: Any,
    cause: Throwable? = null
) : DomainException(message, cause)

// Invalid state
class InvalidStateException(
    message: String,
    val currentState: String,
    val expectedState: String,
    cause: Throwable? = null
) : DomainException(message, cause)

// Validation errors
class ValidationException(
    message: String,
    val errors: Map<String, List<String>>,
    cause: Throwable? = null
) : DomainException(message, cause)
```

## Infrastructure Exception Handling

```kotlin
// Database exceptions
class DatabaseException(
    message: String,
    cause: Throwable
) : RuntimeException(message, cause)

// External API exceptions
class ExternalApiException(
    message: String,
    val statusCode: Int,
    val apiName: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

// Messaging exceptions
class MessagingException(
    message: String,
    val queue: String,
    cause: Throwable
) : RuntimeException(message, cause)
```

## Global Exception Handler

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(ex: ResourceNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                code = "RESOURCE_NOT_FOUND",
                message = ex.message ?: "Resource not found",
                details = mapOf(
                    "resourceType" to ex.resourceType,
                    "resourceId" to ex.resourceId.toString()
                )
            ))
    }

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                code = "VALIDATION_ERROR",
                message = ex.message ?: "Validation failed",
                details = ex.errors
            ))
    }

    @ExceptionHandler(BusinessRuleViolationException::class)
    fun handleBusinessRuleViolation(ex: BusinessRuleViolationException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(
                code = "BUSINESS_RULE_VIOLATION",
                message = ex.message ?: "Business rule violated",
                details = mapOf("rule" to ex.rule)
            ))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        // Log full stack trace
        logger.error("Unexpected error occurred", ex)
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                code = "INTERNAL_SERVER_ERROR",
                message = "An unexpected error occurred",
                details = emptyMap()
            ))
    }
}

data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, Any>
)
```

## Context Preservation

```kotlin
// ⚠️ Consider: Lost context
try {
    externalApi.call()
} catch (e: Exception) {
    throw RuntimeException("API call failed")
}

// 💡 Alternative: Preserve context
try {
    externalApi.call()
} catch (e: Exception) {
    throw ExternalApiException(
        message = "Failed to call external API: ${e.message}",
        statusCode = extractStatusCode(e),
        apiName = "UserService",
        cause = e  // Preserve original exception
    )
}
```

## Repository Layer Exception Handling

```kotlin
interface UserRepository : JpaRepository<UserEntity, Long> {
    @Query("SELECT u FROM UserEntity u WHERE u.email = :email")
    fun findByEmail(@Param("email") email: String): UserEntity?
}

// Service layer wraps with domain exception
class FindUserByEmailUseCase(
    private val userRepository: UserRepository
) {
    fun execute(email: String): User {
        return userRepository.findByEmail(email)
            ?.toDomain()
            ?: throw ResourceNotFoundException(
                message = "User not found with email: $email",
                resourceType = "User",
                resourceId = email
            )
    }
}
```

## Transaction Boundary Exception Handling

```kotlin
@Service
@Transactional
class CreateOrderUseCase(
    private val orderRepository: OrderRepository,
    private val inventoryService: InventoryService,
    private val paymentService: PaymentService
) {
    fun execute(command: CreateOrderCommand): Order {
        return try {
            // Reserve inventory
            val reservation = inventoryService.reserve(command.items)
            
            // Process payment
            val payment = paymentService.process(command.paymentInfo)
            
            // Create order
            orderRepository.save(
                Order.create(
                    items = command.items,
                    payment = payment,
                    reservation = reservation
                )
            )
        } catch (e: InventoryException) {
            throw BusinessRuleViolationException(
                message = "Insufficient inventory",
                rule = "INVENTORY_AVAILABILITY",
                cause = e
            )
        } catch (e: PaymentException) {
            throw BusinessRuleViolationException(
                message = "Payment processing failed",
                rule = "PAYMENT_PROCESSING",
                cause = e
            )
        }
    }
}
```

## Async Exception Handling

```kotlin
@Service
class AsyncNotificationService(
    private val emailService: EmailService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Async
    fun sendNotification(userId: Long) {
        try {
            emailService.send(userId)
        } catch (e: Exception) {
            // Log but don't throw in async context
            logger.error("Failed to send notification to user: $userId", e)
            // Optionally: Store failure for retry
        }
    }
}
```

## Validation with Result Type

```kotlin
sealed class Result<out T> {
    data class Success<T>(val value: T) : Result<T>()
    data class Failure(val errors: List<String>) : Result<Nothing>()
}

class UserValidator {
    fun validate(user: User): Result<User> {
        val errors = mutableListOf<String>()
        
        if (user.email.isBlank()) {
            errors.add("Email is required")
        }
        if (!user.email.contains("@")) {
            errors.add("Email must be valid")
        }
        if (user.age < 18) {
            errors.add("User must be at least 18 years old")
        }
        
        return if (errors.isEmpty()) {
            Result.Success(user)
        } else {
            Result.Failure(errors)
        }
    }
}

// Usage
when (val result = validator.validate(user)) {
    is Result.Success -> repository.save(result.value)
    is Result.Failure -> throw ValidationException(
        message = "User validation failed",
        errors = mapOf("user" to result.errors)
    )
}
```

## Retry Logic with Resilience4j

```kotlin
@Service
class ExternalApiClient(
    private val restTemplate: RestTemplate
) {
    private val retry = Retry.of("externalApi", RetryConfig.custom<Any>()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(500))
        .retryExceptions(IOException::class.java)
        .build())

    fun callExternalApi(request: ApiRequest): ApiResponse {
        return Try.ofSupplier(
            Retry.decorateSupplier(retry) {
                restTemplate.postForObject(
                    "/api/endpoint",
                    request,
                    ApiResponse::class.java
                ) ?: throw ExternalApiException(
                    message = "Null response from API",
                    statusCode = 500,
                    apiName = "ExternalService"
                )
            }
        ).getOrElseThrow { e ->
            ExternalApiException(
                message = "Failed after retries: ${e.message}",
                statusCode = extractStatusCode(e),
                apiName = "ExternalService",
                cause = e
            )
        }
    }
}
```
