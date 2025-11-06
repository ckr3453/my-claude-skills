# Project Conventions

This document defines the coding standards, architectural patterns, and best practices for this Spring Boot Kotlin project.

**⚠️ CRITICAL: All other Claude skills (architecture-design, tdd-development, code-review, documentation) MUST read and follow this document.**

## Table of Contents

1. [Project Overview](#project-overview)
2. [Technology Stack](#technology-stack)
3. [Project Structure](#project-structure)
4. [Naming Conventions](#naming-conventions)
5. [Coding Standards](#coding-standards)
6. [Architecture Patterns](#architecture-patterns)
7. [Exception Handling](#exception-handling)
8. [Security](#security)
9. [Testing](#testing)
10. [API Design](#api-design)
11. [Database](#database)

---

## Project Overview

**Project Name:** [TODO: Fill in]  
**Purpose:** [TODO: Fill in]  
**Base Package:** [TODO: Fill in]  
**Build Tool:** Gradle (Kotlin DSL)  
**Java Version:** 17  
**Kotlin Version:** 1.9.23  
**Spring Boot Version:** 3.2.5

---

## Technology Stack

### Core Framework
- Spring Boot 3.2.5
- Kotlin 1.9.23 with Coroutines support
- Gradle (Kotlin DSL)

### Persistence
- Spring Data JPA
- PostgreSQL (production)
- H2 (test)
- Hibernate 6.x
- Flyway/Liquibase (migrations)

### Security
- Spring Security 6.x
- JWT (jjwt 0.12.5)
- BCrypt password encoding

### API Documentation
- SpringDoc OpenAPI 3.x (Swagger UI)

### Testing
- JUnit 5
- MockK (Kotlin mocking)
- Spring Security Test
- TestContainers (integration tests)
- JaCoCo (code coverage)

### Utilities
- Jackson (JSON processing)
- Bean Validation API
- Spring Boot Actuator

---

## Project Structure

```
src/main/kotlin/{basePackage}/
├── domain/              # Domain entities and value objects
│   └── base/           # Base entities (BaseEntity, BaseTimeEntity)
├── dto/
│   ├── request/        # API request DTOs
│   └── response/       # API response DTOs
├── repository/         # JPA repositories
│   └── impl/          # Custom repository implementations
├── service/           # Business logic layer
├── controller/        # REST API controllers
├── config/            # Spring configuration classes
├── exception/         # Custom exceptions
│   └── base/         # Base exception classes
├── filter/           # Servlet filters
├── aop/              # Aspect-oriented programming
└── util/             # Utility classes
```

### Package Responsibilities

**domain/**
- JPA entities
- Value Objects (Email, Password, etc.)
- Enums
- Domain logic encapsulated in entities
- Must not depend on infrastructure concerns

**dto/**
- Data Transfer Objects
- Validation annotations (@NotNull, @Size, etc.)
- Separated into request/response subdirectories

**repository/**
- Spring Data JPA repositories
- Custom query methods
- QueryDSL implementations in impl/

**service/**
- Business logic implementation
- Transaction boundaries (@Transactional)
- Should be stateless
- Dependency injection via constructor

**controller/**
- REST API endpoints
- Request/response handling
- Swagger documentation
- Minimal logic (delegate to service)

**config/**
- Spring configuration classes
- Security, JPA, OpenAPI, CORS, etc.

**exception/**
- Custom domain exceptions
- Global exception handler (@RestControllerAdvice)

**filter/**
- JWT authentication filter
- Custom servlet filters

**aop/**
- Cross-cutting concerns
- Logging aspects
- Performance monitoring

**util/**
- Helper methods
- Static utility functions
- No business logic

---

## Naming Conventions

### Classes

**Entities:** Singular noun (Member, Product, Order)  
**DTOs:** 
- Request: `{Action}{Resource}Request` (CreateMemberRequest)
- Response: `{Resource}Response` or `{Resource}sResponse` (MemberResponse, MembersResponse)

**Services:** `{Resource}Service` (MemberService)  
**Controllers:** `{Resource}Controller` (MemberController)  
**Repositories:** `{Resource}Repository` or `{Resource}JpaRepository`  
**Exceptions:** `{Condition}Exception` (MemberNotFoundException)

### Methods

**CRUD Operations:**
```kotlin
// Service layer
fun save(entity: Entity): Entity
fun findById(id: String): Entity?
fun getById(id: String): Entity  // throws exception if not found
fun findAll(): List<Entity>
fun update(id: String, request: UpdateRequest): Entity
fun delete(id: String)

// Controller layer (HTTP verbs)
fun postMember()    // POST
fun getMember()     // GET
fun putMember()     // PUT
fun patchMember()   // PATCH
fun deleteMember()  // DELETE
```

**Boolean Methods:**
```kotlin
fun isActive(): Boolean
fun hasPermission(): Boolean
fun canAccess(): Boolean
```

**Collection Methods:**
```kotlin
fun findAllBy...(): List<Entity>
fun existsBy...(): Boolean
fun countBy...(): Long
```

### Variables and Properties

- Use camelCase for variables
- Use UPPER_SNAKE_CASE for constants
- Meaningful names, avoid single letters except loops
- Boolean variables: `isXxx`, `hasXxx`, `canXxx`

```kotlin
// Good
val memberList: List<Member>
val isActive: Boolean
const val MAX_RETRY_COUNT = 3

// Bad
val list: List<Member>
val active: Boolean
val max = 3
```

---

## Coding Standards

### Kotlin Style

**Prefer immutability:**
```kotlin
// Good
val name: String = "John"
data class Member(val id: String, val name: String)

// Avoid
var name: String = "John"
```

**Use data classes for DTOs:**
```kotlin
data class CreateMemberRequest(
    val name: String,
    val email: String
)
```

**Null safety:**
```kotlin
// Good - explicit nullability
val member: Member? = memberRepository.findById(id)
member?.let { /* use member */ }

// Good - throw exception for non-null cases
fun getById(id: String): Member = 
    memberRepository.findById(id) ?: throw MemberNotFoundException()
```

**Companion objects for factory methods:**
```kotlin
class Member(...) {
    companion object {
        fun of(request: CreateMemberRequest): Member {
            return Member(...)
        }
    }
}
```

**Extension functions for utilities:**
```kotlin
fun String.toEmail(): Email = Email(this)
fun LocalDateTime.toKoreanFormat(): String = 
    this.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))
```

### Spring Boot Style

**Constructor injection (preferred):**
```kotlin
@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder
)
```

**Transaction management:**
```kotlin
@Transactional(readOnly = true)  // Read operations
fun findById(id: String): Member

@Transactional  // Write operations
fun save(member: Member): Member
```

**Validation:**
```kotlin
data class CreateMemberRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(max = 50, message = "Name must be less than 50 characters")
    val name: String,
    
    @field:Email(message = "Invalid email format")
    val email: String
)
```

---

## Architecture Patterns

### Domain-Driven Design (DDD)

**Entities:**
- Have unique identity (ID)
- Encapsulate domain logic
- Extend BaseEntity for auditing

**Value Objects:**
- Immutable
- No identity
- Validate on creation
- Examples: Email, Password, Address

```kotlin
@Embeddable
data class Email(
    @Column(nullable = false, unique = true, length = 100)
    val value: String
) {
    init {
        require(value.matches(EMAIL_REGEX)) { "Invalid email format" }
    }
    
    companion object {
        private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$".toRegex()
        
        fun of(email: String): Email = Email(email)
    }
}
```

### Repository Pattern

```kotlin
interface MemberRepository {
    fun save(member: Member): Member
    fun findById(id: String): Member?
    fun findByEmail(email: String): Member?
    fun findAll(): List<Member>
    fun delete(member: Member)
}

interface MemberJpaRepository : JpaRepository<Member, String> {
    fun findByEmail(email: String): Member?
    fun existsByEmail(email: String): Boolean
}
```

### Service Layer Pattern

- Business logic goes here
- Transactional boundaries
- Orchestrates domain objects
- Calls repositories

```kotlin
@Service
class MemberService(
    private val memberRepository: MemberRepository
) {
    @Transactional
    fun register(request: CreateMemberRequest): Member {
        // 1. Validate
        if (memberRepository.existsByEmail(request.email)) {
            throw MemberEmailAlreadyExistsException()
        }
        
        // 2. Create entity
        val member = Member.of(request)
        
        // 3. Save
        return memberRepository.save(member)
    }
}
```

---

## Exception Handling

### Exception Hierarchy

```kotlin
// Base exception
sealed class BusinessException(
    message: String,
    val errorCode: ErrorCode
) : RuntimeException(message)

// Domain exceptions
class MemberNotFoundException : BusinessException(
    "Member not found",
    ErrorCode.MEMBER_NOT_FOUND
)

class MemberEmailAlreadyExistsException : BusinessException(
    "Member email already exists",
    ErrorCode.MEMBER_EMAIL_ALREADY_EXISTS
)
```

### Global Exception Handler

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: HttpServletRequest
    ): Payload<Unit> {
        return Payload(
            status = HttpStatus.BAD_REQUEST,
            message = ex.message ?: "Business error occurred",
            path = request.requestURI,
            errorCode = ex.errorCode
        )
    }
    
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): Payload<Map<String, String>> {
        val errors = ex.bindingResult.fieldErrors
            .associate { it.field to (it.defaultMessage ?: "Invalid value") }
        
        return Payload(
            status = HttpStatus.BAD_REQUEST,
            message = "Validation failed",
            path = request.requestURI,
            data = errors
        )
    }
}
```

---

## Security

### JWT Authentication

**Filter chain:**
1. JwtFilter extracts token from Authorization header
2. Validates token and extracts claims
3. Sets SecurityContext with authentication
4. Proceeds to controller

**Configuration:**
```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtUtil: JwtUtil
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/login", "/swagger-ui/**").permitAll()
                it.requestMatchers("/admin/**").hasRole("ADMIN")
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
        
        return http.build()
    }
}
```

### Password Encoding

```kotlin
@Embeddable
data class Password(
    @Column(nullable = false, length = 100)
    val value: String
) {
    fun matches(rawPassword: String, encoder: PasswordEncoder): Boolean {
        return encoder.matches(rawPassword, value)
    }
    
    companion object {
        fun of(rawPassword: String, encoder: PasswordEncoder): Password {
            validateFormat(rawPassword)
            return Password(encoder.encode(rawPassword))
        }
        
        private fun validateFormat(password: String) {
            require(password.length >= 8) { "Password must be at least 8 characters" }
            require(password.any { it.isUpperCase() }) { "Password must contain uppercase letter" }
            require(password.any { it.isLowerCase() }) { "Password must contain lowercase letter" }
            require(password.any { it.isDigit() }) { "Password must contain digit" }
        }
    }
}
```

---

## Testing

### Test Structure

```kotlin
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberServiceTest {
    
    @Autowired
    private lateinit var memberService: MemberService
    
    @MockK
    private lateinit var memberRepository: MemberRepository
    
    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
    }
    
    @Test
    fun `should create member successfully`() {
        // given
        val request = CreateMemberRequest("John", "john@example.com")
        val expected = Member.of(request)
        every { memberRepository.save(any()) } returns expected
        
        // when
        val result = memberService.register(request)
        
        // then
        result shouldBe expected
        verify(exactly = 1) { memberRepository.save(any()) }
    }
}
```

### Test Naming

Use backticks for descriptive test names:
```kotlin
`should return member when id exists`
`should throw exception when member not found`
`should validate email format on creation`
```

### Test Coverage

- Minimum 80% line coverage
- Focus on business logic in service layer
- Exclude: DTOs, configs, constants

---

## API Design

### REST Conventions

**Endpoints:**
```
GET    /members           # List members
GET    /members/{id}      # Get member
POST   /members           # Create member
PUT    /members/{id}      # Update member (full)
PATCH  /members/{id}      # Update member (partial)
DELETE /members/{id}      # Delete member
```

### Response Format

**Standard Response:**
```kotlin
data class Payload<T>(
    val status: HttpStatus,
    val message: String,
    val path: String,
    val data: T? = null,
    val errorCode: ErrorCode? = null
)
```

**Success Response:**
```json
{
  "status": "OK",
  "message": "Success",
  "path": "/members/123",
  "data": {
    "id": "123",
    "name": "John Doe",
    "email": "john@example.com"
  }
}
```

**Error Response:**
```json
{
  "status": "BAD_REQUEST",
  "message": "Member not found",
  "path": "/members/999",
  "errorCode": "MEMBER_NOT_FOUND"
}
```

### Swagger Documentation

```kotlin
@Tag(name = "Members", description = "Member management APIs")
@RestController
@RequestMapping("/members")
class MemberController {
    
    @Operation(
        summary = "Get member by ID",
        description = "Returns member details for given ID"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Success"),
        ApiResponse(responseCode = "404", description = "Member not found")
    )
    @GetMapping("/{memberId}")
    fun getMember(@PathVariable memberId: String): Payload<MemberResponse> {
        // ...
    }
}
```

---

## Database

### Entity Mapping

```kotlin
@Entity(name = "member")
class Member(
    @Id @Tsid
    @Column(name = "member_id", length = 13)
    var id: String? = null,
    
    @Column(nullable = false, length = 50)
    var name: String,
    
    @Embedded
    var email: Email,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: Role = Role.USER
    
): BaseEntity()
```

### Auditing

```kotlin
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdDate: LocalDateTime = LocalDateTime.now()
    
    @LastModifiedDate
    @Column(nullable = false)
    var lastModifiedDate: LocalDateTime = LocalDateTime.now()
    
    @CreatedBy
    @Column(length = 50, updatable = false)
    var createdBy: String? = null
    
    @LastModifiedBy
    @Column(length = 50)
    var lastModifiedBy: String? = null
}
```

### Query Methods

```kotlin
interface MemberRepository : JpaRepository<Member, String> {
    fun findByEmail(email: String): Member?
    fun existsByEmail(email: String): Boolean
    fun findAllByRole(role: Role): List<Member>
    
    @Query("SELECT m FROM member m WHERE m.createdDate >= :from")
    fun findRecentMembers(@Param("from") from: LocalDateTime): List<Member>
}
```

---

## Summary

This conventions document serves as the single source of truth for development practices. All team members and automated tools must adhere to these standards to maintain code quality and consistency.

**Remember:**
- Clean architecture principles
- SOLID principles
- Test-driven development
- Code reviews before merge
- Continuous refactoring
