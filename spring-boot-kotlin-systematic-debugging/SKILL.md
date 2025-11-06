---
name: spring-boot-kotlin-systematic-debugging
description: Systematic 4-phase debugging process for Spring Boot Kotlin applications. Use when you need to debug production issues, investigate bugs, trace exceptions, or solve performance problems. Provides structured methodology from problem reproduction to root cause verification, with Spring Boot specific tools and techniques.
---

# Systematic Debugging for Spring Boot Kotlin

Debug production issues methodically using a 4-phase process: Reproduce → Gather Evidence → Hypothesize → Verify.

## When to Use This Skill

**Immediate triggers:**
- Production bug reports with unclear cause
- Intermittent failures or flaky behavior
- Performance degradation without obvious reason
- Exception stack traces that don't reveal root cause
- "It works on my machine" scenarios

**Don't use for:**
- Code review (use spring-boot-kotlin-review)
- Known issues with clear solutions
- Simple compilation errors

## The 4-Phase Process

### Phase 1: Reproduce the Problem

**Goal:** Create a reliable way to trigger the bug

**Steps:**

1. **Gather Initial Information**
   ```kotlin
   // What do we know?
   - Error message/stack trace
   - When did it start? (deployment, config change?)
   - Frequency (always, intermittent, specific conditions?)
   - Affected environment (prod, staging, local?)
   - Affected users (all, specific roles, random?)
   ```

2. **Reproduce Locally**
   ```bash
   # Try to reproduce in local environment
   # Check for environment-specific differences
   
   # Compare configurations
   diff application-local.yml application-prod.yml
   
   # Check database state
   # Verify external service availability
   ```

3. **Create Minimal Reproduction**
   ```kotlin
   // Isolate the problem
   @Test
   fun `reproduce bug - user cannot login with valid credentials`() {
       // Arrange: Set up minimum state needed
       val user = createTestUser(email = "test@example.com")
       
       // Act: Trigger the bug
       val result = authService.login(email, password)
       
       // Assert: Verify bug occurs
       assertThrows<AuthenticationException> { result }
   }
   ```

**If you cannot reproduce:**
- Add extensive logging (see Phase 2)
- Check production-only conditions (load, data volume, timing)
- Look for race conditions or concurrency issues

### Phase 2: Gather Evidence

**Goal:** Collect all relevant data about the failure

**Spring Boot Logging Strategy:**

```kotlin
// 1. Add request tracing
@Component
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = UUID.randomUUID().toString()
        MDC.put("requestId", requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove("requestId")
        }
    }
}

// 2. Strategic debug logging
@Service
class UserService(private val userRepository: UserRepository) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    fun authenticate(email: String, password: String): User {
        logger.debug("Authentication attempt for email: {}", email)
        
        val user = userRepository.findByEmail(email)
        logger.debug("User found: {}, active: {}", user != null, user?.isActive)
        
        if (user == null) {
            logger.warn("Authentication failed: user not found")
            throw UserNotFoundException(email)
        }
        
        val passwordMatches = passwordEncoder.matches(password, user.password)
        logger.debug("Password match result: {}", passwordMatches)
        
        if (!passwordMatches) {
            logger.warn("Authentication failed: invalid password for user: {}", email)
            throw InvalidCredentialsException()
        }
        
        logger.info("Authentication successful for user: {}", email)
        return user
    }
}
```

**Actuator Endpoints for Evidence:**

```bash
# Check application health
curl http://localhost:8080/actuator/health

# View metrics
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Thread dump for deadlock analysis
curl http://localhost:8080/actuator/threaddump

# Check database connection pool
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

**Database Query Logging:**

```yaml
# application.yml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

**Evidence Checklist:**
- [ ] Full stack trace with line numbers
- [ ] Request/response logs with IDs
- [ ] Database query logs
- [ ] Exception context (BusinessException fields)
- [ ] System state (memory, CPU, connections)
- [ ] Timeline of events leading to failure

### Phase 3: Form Hypothesis

**Goal:** Develop testable theories about root cause

**Analysis Framework:**

```
1. Review the evidence systematically
   ├─ Exception type and message
   ├─ Stack trace (where it failed)
   ├─ Timeline (what happened before)
   └─ State (what was different)

2. Ask the 5 Whys
   Problem: User login fails
   └─ Why? Password check returns false
      └─ Why? Encoded password doesn't match
         └─ Why? Password was recently changed
            └─ Why? Password update didn't encode
               └─ Root: Password encoder missing in update

3. Check common Spring Boot issues
   ├─ Transaction boundaries (@Transactional missing?)
   ├─ Lazy loading (LazyInitializationException?)
   ├─ Bean lifecycle (dependency not initialized?)
   ├─ Thread safety (shared mutable state?)
   └─ Configuration (wrong environment properties?)
```

**Common Spring Boot Kotlin Patterns:**

```kotlin
// Hypothesis 1: Lazy loading issue
// Problem: LazyInitializationException when accessing orders
val user = userRepository.findById(id).get()
user.orders.size  // ❌ Fails outside transaction

// Test hypothesis
@Query("SELECT u FROM User u LEFT JOIN FETCH u.orders WHERE u.id = :id")
fun findByIdWithOrders(id: Long): User?

// Hypothesis 2: Transaction not committed
// Problem: Data not visible in database after save
@Transactional  // ❌ Missing on service method
fun updateUser(id: Long, dto: UpdateUserDto) {
    val user = userRepository.findById(id).get()
    user.name = dto.name
    // Not saved in transaction
}

// Test hypothesis
@Transactional  // ✅ Add annotation
fun updateUser(id: Long, dto: UpdateUserDto) {
    val user = userRepository.findById(id).get()
    user.name = dto.name
    userRepository.save(user)  // Explicit save
}

// Hypothesis 3: Null safety violation
// Problem: NullPointerException in Kotlin code
lateinit var service: SomeService  // ❌ Not initialized

// Test hypothesis
@Autowired
lateinit var service: SomeService  // ✅ Proper injection
```

**Prioritize hypotheses:**
1. Most likely based on evidence
2. Easiest to test
3. Highest impact if correct

### Phase 4: Verify Solution

**Goal:** Confirm the fix resolves the root cause

**Verification Steps:**

```kotlin
// 1. Create failing test
@Test
fun `user update should persist name change`() {
    val user = createUser(name = "Original")
    
    userService.updateUser(user.id, UpdateUserDto(name = "Updated"))
    
    val updated = userRepository.findById(user.id).get()
    assertEquals("Updated", updated.name)  // ❌ Fails before fix
}

// 2. Apply fix
@Service
class UserService {
    @Transactional  // ← Fix applied
    fun updateUser(id: Long, dto: UpdateUserDto) {
        val user = userRepository.findById(id).orElseThrow()
        user.name = dto.name
        userRepository.save(user)
    }
}

// 3. Verify test passes
// ✅ Test now passes

// 4. Run full test suite
./gradlew test
// ✅ No regressions

// 5. Test in staging environment
// ✅ Works in production-like environment

// 6. Monitor in production
// ✅ Error rate drops to zero
```

**Post-Fix Actions:**
- [ ] Remove debug logging (or reduce level)
- [ ] Document the issue and fix
- [ ] Add regression test
- [ ] Update runbooks if needed
- [ ] Share learnings with team

## Common Spring Boot Issues

See `references/common-issues.md` for detailed patterns:
- Transaction management problems
- Lazy loading exceptions
- Bean initialization order
- Connection pool exhaustion
- N+1 query problems in production

## Debugging Tools Reference

### JetBrains Debugging

```kotlin
// Conditional breakpoints
jetbrains:toggle_debugger_breakpoint path/to/File.kt 42

// Evaluate expression during debug
// Use "Evaluate Expression" (Alt+F8)
user.orders.filter { it.status == OrderStatus.PENDING }
```

### Spring Boot DevTools

```kotlin
// Auto-restart on code changes
// Add to build.gradle.kts
dependencies {
    developmentOnly("org.springframework.boot:spring-boot-devtools")
}
```

### Logging Best Practices

```kotlin
// Use structured logging
logger.info(
    "User operation completed",
    kv("userId", user.id),
    kv("operation", "update"),
    kv("duration", duration)
)

// Not just string concatenation
logger.info("User ${user.id} operation update took ${duration}ms")  // ❌
```

## Integration with Other Skills

Use in combination with:
- `spring-boot-kotlin-review`: After fixing, review for similar issues
- `spring-boot-kotlin-tdd`: Add regression tests
- `spring-boot-kotlin-architecture-design`: Check if architecture caused the issue

## Quick Reference Card

```
Problem reported
↓
Phase 1: Can I reproduce it?
├─ YES → Minimal reproduction test
└─ NO → Add logging, try production conditions
↓
Phase 2: What's the evidence?
├─ Stack traces (where?)
├─ Logs (what happened?)
├─ State (what's different?)
└─ Timeline (when?)
↓
Phase 3: What's the hypothesis?
├─ Review evidence
├─ Ask 5 Whys
├─ Check common patterns
└─ Prioritize theories
↓
Phase 4: Does the fix work?
├─ Write failing test
├─ Apply fix
├─ Verify test passes
├─ Check no regressions
└─ Monitor in production
```

## Remember

**Don't skip phases:** Each phase builds on the previous one. Jumping to solutions without evidence leads to cargo cult fixes.

**Stay systematic:** When you feel stuck, go back to Phase 2 (more evidence) or Phase 3 (different hypothesis).

**Document as you go:** Your debugging notes become the bug report and fix documentation.

**Learn patterns:** Common issues become faster to debug with experience. Keep a personal runbook.
