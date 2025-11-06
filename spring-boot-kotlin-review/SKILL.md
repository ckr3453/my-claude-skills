---
name: spring-boot-kotlin-review
description: Comprehensive code review and refactoring for Spring Boot Kotlin projects following Clean Code and Clean Architecture principles. Use when you need to (1) Review code quality and identify issues, (2) Analyze exception handling and error management, (3) Detect performance bottlenecks, (4) Suggest refactoring improvements, (5) Verify adherence to project conventions, or (6) Validate SOLID principles and design patterns. Provides detailed feedback with actionable improvements.
---

# Spring Boot Kotlin Code Review & Refactoring

Perform comprehensive code review and refactoring for Spring Boot Kotlin projects, ensuring adherence to Clean Code principles, Clean Architecture, and industry best practices.

## Review Philosophy

**Core Principles:**
- **Safety First**: Security and data integrity are non-negotiable
- **Context Matters**: Understand the problem, team, and constraints before suggesting changes
- **Multiple Valid Approaches**: There's rarely one "right" way
- **Pragmatism Over Purity**: Production-ready > theoretically perfect

**Hard Rules (Always enforce):**
- Security vulnerabilities (SQL injection, XSS, etc.)
- Data corruption risks
- Performance bottlenecks in production
- Breaking changes without migration path

**Soft Guidelines (Suggest with context):**
- Architecture patterns (Rich vs Anemic domain)
- Design patterns (Strategy vs if-else)
- Code organization preferences
- Naming conventions (within team standards)

**When Reviewing:**
1. Ask "why" before criticizing - there may be valid reasons
2. Suggest improvements, don't demand changes
3. Explain trade-offs, let team decide
4. Respect team conventions over personal preferences
5. Focus on high-impact improvements

## Review Process

Follow this systematic approach when reviewing code:

### 1. Initial Analysis

Understand the context before reviewing:

```bash
# Get file structure
jetbrains:list_directory_tree_in_folder / 5

# Check project conventions
jetbrains:get_file_text_by_path PROJECT_CONVENTIONS.md

# Identify changed files
jetbrains:get_project_vcs_status

# Review related files for context
```

### 2. Multi-Dimensional Review

Analyze code across these dimensions in order:

#### A. Architecture & Design (High Priority)

**SOLID Principles Verification**
- Single Responsibility: Each class has one reason to change
- Open/Closed: Open for extension, closed for modification
- Liskov Substitution: Subtypes are substitutable for base types
- Interface Segregation: No client depends on unused methods
- Dependency Inversion: Depend on abstractions, not concretions

**Clean Architecture Compliance**
- Layer boundaries: Domain → Application → Infrastructure → Presentation
- Dependency rule: Dependencies point inward only
- Entity purity: Domain entities have no framework dependencies
- Use case isolation: Business logic independent of delivery mechanism

**Common Violations to Check**
```kotlin
// ❌ BAD: Business logic in controller
@RestController
class UserController(private val userRepository: UserRepository) {
    fun createUser(dto: UserDto) = userRepository.save(dto.toEntity())
}

// ✅ GOOD: Proper layer separation
@RestController
class UserController(private val createUserUseCase: CreateUserUseCase) {
    fun createUser(dto: UserDto) = createUserUseCase.execute(dto.toDomain())
}
```

#### B. Exception Handling & Error Management (Critical)

**Error Handling Patterns**
- Domain exceptions for business rule violations
- Infrastructure exceptions for technical failures
- Global exception handler for consistent responses
- Context preservation in exception chains

See `references/error_patterns.md` for comprehensive patterns.

**Common Issues to Detect**
```kotlin
// ❌ BAD: Silent failure
fun findUser(id: Long): User? = userRepository.findById(id).orElse(null)

// ✅ GOOD: Explicit error handling
fun findUser(id: Long): User = 
    userRepository.findById(id).orElseThrow { 
        UserNotFoundException("User not found: $id") 
    }

// ❌ BAD: Generic exceptions
throw Exception("Something went wrong")

// ✅ GOOD: Specific domain exceptions
throw InvalidUserStateException("User must be active to perform this operation")
```

#### C. Performance & Scalability

**Critical Checks**
- N+1 query problems (check for @EntityGraph or fetch joins)
- Missing pagination on list operations
- Inefficient database queries (missing indexes, full table scans)
- Unnecessary object creation in loops
- Blocking operations in async contexts
- Missing caching for frequently accessed data

See `references/performance_patterns.md` for detailed optimization techniques and examples.

#### D. Code Quality & Maintainability

**Clean Code Principles**
- Meaningful names (reveal intent, no mental mapping)
- Small functions (do one thing, one level of abstraction)
- DRY principle (don't repeat yourself)
- Boy Scout Rule (leave code cleaner than you found it)

**Kotlin Best Practices**
- Use null safety operators (?, ?:, ?.)
- Prefer immutable collections
- Leverage type inference
- Use when expressions over if-else chains
- Apply extension functions appropriately

See `references/clean_code_checklist.md` for comprehensive guidelines and examples.

#### E. Security

**Critical Vulnerabilities to Check**
- SQL injection (use parameterized queries, never string concatenation)
- Missing authentication/authorization checks
- Sensitive data in logs
- Insecure password handling
- Missing input validation
- CORS misconfiguration

Review security patterns in `references/error_patterns.md` and check against OWASP Top 10.

#### F. Testing & Testability

**Testability Indicators**
- Constructor injection (enables easy mocking)
- Pure functions (deterministic, no side effects)
- Small, focused classes
- Minimal dependencies
- Interface-based design

See `references/clean_code_checklist.md` for testable code patterns and examples.

### 3. Refactoring Recommendations

When suggesting refactoring, prioritize by impact:

**Priority 1: Critical Issues**
- Security vulnerabilities
- Data corruption risks
- Performance bottlenecks causing production issues
- Architecture violations breaking layer boundaries

**Priority 2: High Impact**
- Exception handling gaps
- N+1 queries and missing indexes
- SOLID violations
- Missing transaction management

**Priority 3: Maintainability**
- Code duplication
- Complex methods (cyclomatic complexity > 10)
- Poor naming
- Missing documentation for complex logic

**Priority 4: Style & Convention**
- Kotlin idiom improvements
- Consistent formatting
- Minor optimizations

### 4. Providing Feedback

**Feedback Structure**

For each issue found, provide:

1. **Location**: File path and line numbers
2. **Severity**: Critical / High / Medium / Low
3. **Issue**: Clear description of the problem
4. **Impact**: Why this matters (performance, security, maintainability)
5. **Solution**: Specific code example showing the fix
6. **Reference**: Link to principle or pattern (if applicable)

**Example Feedback Format**

```markdown
## Critical Issues

### 1. SQL Injection Vulnerability
**Location**: `UserRepository.kt:45-47`
**Severity**: Critical
**Issue**: Query uses string interpolation instead of parameterized query
**Impact**: Exposes application to SQL injection attacks
**Current Code**:
\`\`\`kotlin
@Query("SELECT u FROM User u WHERE u.email = '\$email'")
fun findByEmail(email: String): User?
\`\`\`
**Recommended Fix**:
\`\`\`kotlin
@Query("SELECT u FROM User u WHERE u.email = :email")
fun findByEmail(@Param("email") email: String): User?
\`\`\`
**Reference**: OWASP SQL Injection Prevention

---
```

### 5. Automated Checks

Use IDE tools when available:

```kotlin
// Get current file errors
jetbrains:get_current_file_errors

// Get all project problems
jetbrains:get_project_problems

// Reformat code
jetbrains:reformat_current_file
```

### 6. Refactoring Execution

When implementing refactoring:

1. **Small, incremental changes**: One refactoring at a time
2. **Verify tests pass**: Run tests after each change
3. **Preserve behavior**: Ensure no functional changes
4. **Update documentation**: Keep docs in sync with code changes

```bash
# Run tests after refactoring
jetbrains:run_configuration "Run Tests"

# Check for new issues
jetbrains:get_project_problems
```

## Review Checklist

Use this checklist to ensure comprehensive review:

**Architecture & Design**
- [ ] Clean Architecture layers properly separated
- [ ] SOLID principles followed
- [ ] Appropriate design patterns used
- [ ] Domain model rich and expressive
- [ ] No circular dependencies

**Exception Handling**
- [ ] All exceptions properly handled
- [ ] Domain exceptions for business rules
- [ ] Infrastructure exceptions for technical failures
- [ ] Context preserved in exception chains
- [ ] Global exception handler configured

**Performance**
- [ ] No N+1 query problems
- [ ] Appropriate indexes on database columns
- [ ] Pagination implemented for list operations
- [ ] Caching used where appropriate
- [ ] Efficient algorithms (no O(n²) where O(n) is possible)

**Code Quality**
- [ ] Meaningful variable and function names
- [ ] Functions small and focused (< 20 lines)
- [ ] No code duplication
- [ ] Kotlin idioms properly used
- [ ] Immutability preferred

**Security**
- [ ] Input validation implemented
- [ ] SQL injection prevented (parameterized queries)
- [ ] Authentication/authorization checked
- [ ] Sensitive data not logged
- [ ] Secure password handling

**Testing**
- [ ] Code is testable (constructor injection, pure functions)
- [ ] Unit tests cover business logic
- [ ] Integration tests for database operations
- [ ] Edge cases handled

**Documentation**
- [ ] Complex logic documented
- [ ] Public APIs documented
- [ ] README updated if needed
- [ ] Architecture decisions recorded

## Reference Documentation

### Core Patterns
- `references/refactoring_patterns.md`: Common code smells and refactoring solutions
- `references/error_patterns.md`: Exception handling best practices and patterns
- `references/performance_patterns.md`: Database and application performance optimization
- `references/clean_code_checklist.md`: Comprehensive code quality guidelines

### Specialized Reviews
- `references/geospatial_patterns.md`: PostGIS optimization, spatial queries, and GeoJSON handling
- `references/practical_scenarios.md`: Real-world review scenarios (legacy migration, performance emergency, automation)
- `references/quality_metrics.md`: Measurable quality standards (complexity, coverage, maintainability)

Use these references based on review context:
- **Architecture review** → Start with refactoring_patterns.md and clean_code_checklist.md
- **Performance issues** → performance_patterns.md, then geospatial_patterns.md if spatial data involved
- **Production emergency** → practical_scenarios.md (Scenario 2: Performance Emergency)
- **Legacy code migration** → practical_scenarios.md (Scenario 1: Legacy Code Migration)
- **Quality assessment** → quality_metrics.md for objective standards

## Specialized Review Scenarios

### Geospatial Code Review
When reviewing code involving spatial data:

1. Check spatial index usage (GIST indexes)
2. Verify SRID consistency (4326 for WGS84)
3. Review Geometry vs Geography usage
4. Validate coordinate bounds (-180/180, -90/90)
5. Check for bounding box pre-filtering
6. Assess GeoJSON streaming for large datasets

See `references/geospatial_patterns.md` for detailed patterns.

### Performance Emergency Response
For production performance issues:

1. Identify recent changes via VCS
2. Check for N+1 queries
3. Verify pagination on list endpoints
4. Review database indexes
5. Analyze query execution plans
6. Check for missing caching

See `references/practical_scenarios.md` Scenario 2 for emergency protocol.

### Technical Debt Assessment
Quarterly or on-demand debt review:

1. Run complexity analysis (Detekt)
2. Check test coverage (JaCoCo)
3. Review dependency vulnerabilities
4. Calculate technical debt ratio
5. Prioritize by impact/effort matrix

See `references/quality_metrics.md` for measurable standards.

## Working with Existing Code

When reviewing or refactoring existing code:

1. **Understand first**: Read and understand the code thoroughly before suggesting changes
2. **Respect history**: There may be good reasons for current implementation
3. **Ask why**: If something seems odd, investigate the reason before criticizing
4. **Be pragmatic**: Not all code needs to be perfect; focus on high-impact improvements
5. **Incremental improvement**: Don't try to fix everything at once

## Integration with Other Skills

This skill works best when combined with:
- `spring-boot-kotlin-architecture-design`: Validates implementation against design
- `spring-boot-kotlin-tdd`: Ensures tests exist before refactoring
- `spring-boot-kotlin-init`: Verifies adherence to project conventions

## Review Scope

**File-Level Review**
```bash
# Review currently open file
jetbrains:get_open_in_editor_file_text
```

**Module-Level Review**
```bash
# Review specific module
jetbrains:list_directory_tree_in_folder src/main/kotlin/com/example/module
```

**Project-Level Review**
```bash
# Review changed files only
jetbrains:get_project_vcs_status

# Full project review
jetbrains:get_project_problems
```

## Final Deliverables

After review, provide:

1. **Summary Report**: High-level findings and priorities
2. **Detailed Issues**: Each issue with location, severity, and solution
3. **Refactoring Plan**: Prioritized list of recommended changes
4. **Metrics**: Before/after comparison (if measurable)
5. **Action Items**: Clear next steps for developer

Remember: The goal is to improve code quality while maintaining team velocity. Focus on high-impact improvements that align with project goals.
