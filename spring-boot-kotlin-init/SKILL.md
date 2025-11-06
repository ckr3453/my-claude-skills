---
name: spring-boot-kotlin-init
description: Initializes Spring Boot Kotlin projects with Clean Architecture principles. Use when users request to create a new Spring Boot project, initialize a backend project structure, or set up a Kotlin-based web application with best practices. Includes standardized project structure, dependencies configuration, security setup, comprehensive logging with AOP, exception handling with context, and thorough conventions documentation. Supports PostgreSQL, MariaDB, MySQL with flexible configuration.
---

# Spring Boot Init

Initialize production-ready Spring Boot projects with Kotlin, following Clean Architecture and industry best practices.

## Pre-Initialization Questions

Before starting, gather requirements from the user:

1. **Project Information**
   - Project name?
   - Group ID? (e.g., com.company.project)
   - Base package? (e.g., com.company.project)

2. **Database**
   - Which database? (PostgreSQL / MariaDB / MySQL / H2 only)
   - Need spatial/GIS features? (Hibernate Spatial)

3. **Authentication**
   - Need JWT authentication? (Recommended: Yes)

4. **Features**
   - Need file upload/download (S3)?
   - Need caching (Redis)?
   - Need batch processing?

5. **Version Preferences**
   - Use latest stable versions? (Recommended: Yes)

## Quick Start

1. **Initialize Project Structure**
   ```bash
   bash references/init_structure.sh <project-name> <base-package>
   ```

2. **Configure Build Files**
   - Customize `assets/build.gradle.kts` based on requirements
   - Update database dependencies
   - Add optional features

3. **Generate Core Files**
   - Copy `assets/config/` files to project
   - BaseEntity (id + auditing)
   - LoggerAspect (unified logging)
   - BusinessException (with context)
   - GlobalExceptionHandler
   - SecurityConfig, SwaggerConfig

4. **Create PROJECT_CONVENTIONS.md**
   - Use `references/PROJECT_CONVENTIONS.md` as template
   - **This becomes the single source of truth for all other skills**

## Project Structure

```
project-root/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── {basePackage}/
│   │   │       ├── domain/
│   │   │       │   └── base/         # BaseEntity (id + auditing)
│   │   │       ├── dto/
│   │   │       │   ├── request/
│   │   │       │   └── response/
│   │   │       ├── repository/
│   │   │       ├── service/          # @Service = 자동 로깅
│   │   │       ├── controller/       # 자동 로깅
│   │   │       ├── config/
│   │   │       ├── exception/
│   │   │       │   └── base/         # BusinessException
│   │   │       ├── aop/              # LoggerAspect
│   │   │       └── util/
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       ├── kotlin/
│       │   └── {basePackage}/
│       │       ├── unit/             # Fake objects
│       │       └── integration/      # Testcontainers
│       └── resources/
├── build.gradle.kts
├── settings.gradle.kts
└── PROJECT_CONVENTIONS.md            # ⭐ Source of truth
```

## Core Features

### 1. BaseEntity (ID + Auditing 통합)

```kotlin
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity(
    @Id @Tsid
    @Column(name = "id", length = 13)
    open var id: String? = null
) {
    @CreatedDate
    var createdDate: LocalDateTime
    
    @LastModifiedDate
    var lastModifiedDate: LocalDateTime
    
    @CreatedBy
    var createdBy: String?
    
    @LastModifiedBy
    var lastModifiedBy: String?
}
```

**Usage:**
```kotlin
@Entity(name = "user")
class User(
    id: String? = null,  // Inherited
    var name: String,
    @Embedded var email: Email
) : BaseEntity(id)
```

### 2. Unified Logging with AOP

**Controller Layer (자동):**
```
[requestId] INFO - Request: POST /api/members < 192.168.1.100 (25ms)
```

**Service Layer (자동):**
```
[requestId] DEBUG - >>> START: MemberService.create with args: [...]
[requestId] DEBUG - <<< END: MemberService.create - SUCCESS (23ms)
```

**No logging code needed in Service:**
```kotlin
@Service
class MemberService {
    fun create(request: CreateRequest): Member {
        // Just business logic!
        return memberRepository.save(Member.of(request))
    }
}
```

### 3. Exception with Context

```kotlin
throw MemberNotFoundException(memberId)
// Context: {memberId=123}

throw InvalidCsvDataException(
    fileName = "sites.csv",
    lineNumber = 15,
    columnName = "latitude",
    expectedType = "Double",
    actualValue = "invalid"
)
// Context: {fileName, lineNumber, columnName, expectedType, actualValue}
```

**GlobalExceptionHandler automatically logs:**
```
ERROR - ErrorCode: USR-001, Context: {memberId=123}, Path: /api/members/123
```

### 4. Enhanced ErrorCode

```kotlin
enum class ErrorCode(
    val code: String,
    val message: String,
    val httpStatus: HttpStatus
) {
    // User
    MEMBER_NOT_FOUND("USR-001", "Member not found", NOT_FOUND),
    MEMBER_EMAIL_ALREADY_EXISTS("USR-002", "Email already exists", BAD_REQUEST),
    
    // CSV
    CSV_PARSE_ERROR("CSV-001", "CSV parse error", BAD_REQUEST),
    INVALID_CSV_DATA("CSV-003", "Invalid CSV data", BAD_REQUEST),
    
    // ...
}
```

### 5. Testing Strategy

**Unit Tests (Fake Objects):**
```kotlin
class MemberServiceTest {
    private lateinit var fakeRepository: FakeUserRepository
    private lateinit var service: MemberService
    
    @Test
    fun `should create member`() {
        val result = service.create(request)
        fakeRepository.saved.size shouldBe 1
    }
}
```

**Integration Tests (Testcontainers):**
```kotlin
@SpringBootTest
@Testcontainers
class MemberIntegrationTest {
    @Container
    companion object {
        val postgres = PostgreSQLContainer("postgres:15-alpine")
    }
}
```

**Coverage: 70% for domain, repository, service, controller, util, aop**

## Database Configuration

### PostgreSQL (Default)
```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

### MariaDB
```yaml
spring:
  datasource:
    driver-class-name: org.mariadb.jdbc.Driver
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MariaDBDialect
```

### MySQL
```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
```

## Logging Configuration

```yaml
logging:
  level:
    kr.movements.dtxiotv3.aop.LoggerAspect: DEBUG  # Service 메서드 로깅
    kr.movements.dtxiotv3.exception: ERROR          # 예외 로깅
  
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{requestId}] %-5level %logger{36} - %msg%n"
```

**Production (Service 로깅 끔):**
```yaml
logging:
  level:
    kr.movements.dtxiotv3.aop.LoggerAspect: INFO
```

## Usage Example

**User Request:**
```
"Create a Spring Boot project for e-commerce with PostgreSQL and JWT"
```

**Claude Actions:**
1. ✅ Asks: Project name? Group ID? Redis? S3?
2. ✅ Runs `init_structure.sh`
3. ✅ Configures PostgreSQL in `build.gradle.kts`
4. ✅ Copies all config files (BaseEntity, LoggerAspect, etc.)
5. ✅ Sets up JWT security
6. ✅ Generates PROJECT_CONVENTIONS.md
7. ✅ Creates example: Product, Order entities (as templates)

## Validation Checklist

- [ ] Directory structure created
- [ ] build.gradle.kts configured for DB
- [ ] BaseEntity (id + auditing) in place
- [ ] LoggerAspect configured (Controller + Service)
- [ ] BusinessException with context support
- [ ] GlobalExceptionHandler with detailed logging
- [ ] SecurityConfig (if JWT)
- [ ] application.yml (all profiles)
- [ ] Test configuration (Testcontainers)
- [ ] JaCoCo (70% coverage)
- [ ] PROJECT_CONVENTIONS.md complete

## Integration with Other Skills

**PROJECT_CONVENTIONS.md is the single source of truth:**

- **architecture-design**: Read conventions before designing
- **tdd-development**: Follow testing patterns (fake vs mock, Testcontainers)
- **code-review-refactoring**: Validate against conventions
- **documentation**: Use conventions as baseline

## Key Advantages

✅ **Unified Logging**: All logs use same format  
✅ **Auto Logging**: @Service = automatic method logging  
✅ **Context-Rich Exceptions**: Detailed error information  
✅ **Clean Code**: No logging code in services  
✅ **Request Tracking**: MDC with requestId  
✅ **Performance**: DEBUG level check (no overhead in production)  
✅ **Security**: Sensitive data masking  
✅ **Testing**: Fake objects + Testcontainers  

## Resources

- **scripts/generate_entity.py**: Entity scaffold
- **scripts/generate_exception.py**: Exception hierarchy
- **references/init_structure.sh**: Directory creator
- **references/PROJECT_CONVENTIONS.md**: Conventions template ⭐
- **references/ADDITIONAL_FEATURES.md**: Optional features
- **assets/build.gradle.kts**: Gradle config
- **assets/application.yml**: Spring config
- **assets/config/BaseEntity.kt**: Base entity template
- **assets/config/LoggerAspect.kt**: Unified logging
- **assets/config/BusinessException.kt**: Exception with context
- **assets/config/GlobalExceptionHandler.kt**: Exception handler
- **assets/config/SecurityConfig.kt**: Security setup
- **assets/config/SwaggerConfig.kt**: API docs
- **assets/config/FakeRepositoryExample.kt**: Test patterns
- **assets/config/TestcontainersExample.kt**: Integration tests
