# Real-World Production Patterns

Learn proven architectural patterns from actual production projects. These patterns have been battle-tested in complex, data-intensive systems.

## Project Context

**Real-World Scenario:** Hierarchical data platform with spatial queries and bulk imports  
**Key Challenges:** Complex entity relationships, performance at scale, data integrity with imports  
**Technologies:** Spring Boot Kotlin, PostgreSQL, Clean Architecture

(Actual production project: IoT sensor data platform with Site → Pipe → Channel hierarchy and geospatial queries)

---

## Pattern 1: Hierarchical Aggregate Structure

The master-detail hierarchy in dtxiotv3:

```
Site (Root Aggregate)
  ├─ Pipe (Child)
  │   ├─ Channel (Grandchild)
  │   │   ├─ SensorCase (Great-grandchild)
  │   │   └─ Measurements (Time-series data)
  │   └─ PipeProperties (Embedded)
  └─ Location (Embedded)
```

**Why this structure:**
1. **Clear Ownership**: Site owns everything below
2. **Lifecycle Dependency**: Delete Site = delete all Pipes, Channels, Sensors
3. **Query Boundaries**: Load what's needed, avoid extra tables

**Entity Implementation:**

```kotlin
@Entity(name = "site")
class Site(
    id: String? = null,
    
    @Column(nullable = false, length = 100)
    var name: String,
    
    @Embedded(attributeOverrides = [
        AttributeOverride(name = "latitude", column = Column(name = "site_latitude")),
        AttributeOverride(name = "longitude", column = Column(name = "site_longitude"))
    ])
    var location: GeoPoint,
    
    // Child pipes owned by this site
    @OneToMany(cascade = [ALL], orphanRemoval = true, fetch = LAZY)
    @JoinColumn(name = "site_id")
    var pipes: MutableList<Pipe> = mutableListOf(),
    
    @Embedded
    var metadata: SiteMetadata = SiteMetadata()
    
) : BaseEntity(id) {
    
    // Domain logic on aggregate root
    fun addPipe(name: String, length: Double): Pipe {
        require(length > 0) { "Pipe length must be positive" }
        val pipe = Pipe(name = name, length = length, site = this)
        pipes.add(pipe)
        return pipe
    }
    
    fun removePipe(pipeId: String) {
        pipes.removeIf { it.id == pipeId }
    }
}

@Entity(name = "pipe")
class Pipe(
    id: String? = null,
    var name: String,
    var length: Double,
    
    @ManyToOne(fetch = LAZY, cascade = [PERSIST])
    lateinit var site: Site,
    
    @OneToMany(cascade = [ALL], orphanRemoval = true)
    @JoinColumn(name = "pipe_id")
    var channels: MutableList<Channel> = mutableListOf()
    
) : BaseEntity(id) {
    
    fun addChannel(name: String): Channel {
        val channel = Channel(name = name, pipe = this)
        channels.add(channel)
        return channel
    }
}

@Entity(name = "channel")
class Channel(
    id: String? = null,
    var name: String,
    
    @ManyToOne(fetch = LAZY, cascade = [PERSIST])
    lateinit var pipe: Pipe,
    
    @OneToMany(cascade = [ALL], orphanRemoval = true)
    @JoinColumn(name = "channel_id")
    var sensorCases: MutableList<SensorCase> = mutableListOf()
    
) : BaseEntity(id)

@Entity(name = "sensor_case")
class SensorCase(
    id: String? = null,
    var model: String,
    
    @ManyToOne(fetch = LAZY, cascade = [PERSIST])
    lateinit var channel: Channel
    
) : BaseEntity(id)
```

**Database Schema:**
```sql
CREATE TABLE site (
    site_id VARCHAR(13) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    site_latitude DOUBLE PRECISION NOT NULL,
    site_longitude DOUBLE PRECISION NOT NULL,
    ...
);

CREATE TABLE pipe (
    pipe_id VARCHAR(13) PRIMARY KEY,
    site_id VARCHAR(13) NOT NULL,
    name VARCHAR(100),
    length DOUBLE PRECISION NOT NULL,
    FOREIGN KEY (site_id) REFERENCES site(site_id) ON DELETE CASCADE
);

-- Indexes for hierarchy traversal
CREATE INDEX idx_pipe_site ON pipe(site_id);
CREATE INDEX idx_channel_pipe ON channel(pipe_id);
CREATE INDEX idx_sensor_case_channel ON sensor_case(channel_id);
```

---

## Pattern 2: Spatial Data with GeoPoint Value Object

dtxiotv3 handles geospatial queries (find sensors within radius, nearest pipes, etc.)

```kotlin
@Embeddable
data class GeoPoint(
    @Column(nullable = false)
    val latitude: Double,
    
    @Column(nullable = false)
    val longitude: Double,
    
    @Column(nullable = true)
    val elevation: Double? = null,
    
    @Column(nullable = true)
    val accuracy: Double? = null
) {
    init {
        require(latitude in -90.0..90.0) { "Invalid latitude: $latitude" }
        require(longitude in -180.0..180.0) { "Invalid longitude: $longitude" }
        elevation?.let { require(it >= -430 && it <= 8848) { "Unrealistic elevation" } }
    }
    
    // Haversine formula for distance calculation
    fun distanceTo(other: GeoPoint): Double {
        val R = 6371.0  // Earth radius in km
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
    
    companion object {
        fun of(latitude: Double, longitude: Double) = GeoPoint(latitude, longitude)
    }
}

// Usage in Site
@Entity class Site : BaseEntity() {
    @Embedded(attributeOverrides = [
        AttributeOverride(name = "latitude", column = Column(name = "site_latitude")),
        AttributeOverride(name = "longitude", column = Column(name = "site_longitude")),
        AttributeOverride(name = "elevation", column = Column(name = "site_elevation"))
    ])
    var location: GeoPoint
}
```

**Repository with Spatial Query:**
```kotlin
@Repository
interface SiteRepository : JpaRepository<Site, String> {
    
    // Native spatial query (if DB supports PostGIS)
    @Query("""
        SELECT s FROM Site s WHERE 
        FUNCTION('ST_Distance', 
            FUNCTION('ST_Point', s.location.longitude, s.location.latitude),
            FUNCTION('ST_Point', ?1, ?2)
        ) <= ?3 / 1000.0
    """)
    fun findNearby(
        @Param("lon") longitude: Double,
        @Param("lat") latitude: Double,
        @Param("radius") radiusMeters: Double
    ): List<Site>
    
    // Alternative: Manual distance calculation in application
    // Load candidates, filter by distance in Java
    fun findAllByLatitudeBetweenAndLongitudeBetween(
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): List<Site>
}

// Service layer
@Service
class SiteService(private val repository: SiteRepository) {
    
    @Transactional(readOnly = true)
    fun findNearby(point: GeoPoint, radiusKm: Double): List<Site> {
        // Option 1: DB spatial query (if PostGIS available)
        return repository.findNearby(point.longitude, point.latitude, radiusKm * 1000)
        
        // Option 2: Application-level filtering
        val bbox = point.boundingBox(radiusKm)
        val candidates = repository.findAllByLatitudeBetweenAndLongitudeBetween(
            bbox.minLat, bbox.maxLat, bbox.minLon, bbox.maxLon
        )
        return candidates.filter { it.location.distanceTo(point) <= radiusKm }
    }
}
```

---

## Pattern 3: CSV Import with Rich Exception Context

dtxiotv3 processes bulk site/pipe imports. When import fails, context matters:

```kotlin
@Embeddable
data class CsvRow(
    val lineNumber: Int,
    val rawData: String
)

// Domain exception with CSV context
class InvalidCsvDataException(
    val fileName: String,
    val lineNumber: Int,
    val columnName: String,
    val expectedType: String,
    val actualValue: String,
    cause: Throwable? = null
) : BusinessException(
    "CSV Import Error in $fileName:$lineNumber[$columnName]: " +
    "Expected $expectedType but got '$actualValue'",
    ErrorCode.INVALID_CSV_DATA
)

class CsvParseException(
    val fileName: String,
    val reason: String,
    cause: Throwable
) : BusinessException(
    "Failed to parse CSV $fileName: $reason",
    ErrorCode.CSV_PARSE_ERROR,
    cause
)

// Service layer
@Service
class SiteImportService(
    private val siteRepository: SiteRepository,
    private val siteFactory: SiteFactory
) {
    
    @Transactional
    fun importSitesFromCsv(file: MultipartFile): ImportResult {
        try {
            CSVFormat.DEFAULT
                .withHeader("name", "latitude", "longitude", "elevation")
                .parse(InputStreamReader(file.inputStream))
                .use { csvParser ->
                    val sites = mutableListOf<Site>()
                    
                    for ((index, record) in csvParser.withIndex()) {
                        try {
                            // Validate each field
                            val name = record.get("name").takeIf { it.isNotBlank() }
                                ?: throw InvalidCsvDataException(
                                    fileName = file.originalFilename ?: "unknown",
                                    lineNumber = index + 2,  // Header is line 1
                                    columnName = "name",
                                    expectedType = "String",
                                    actualValue = record.get("name")
                                )
                            
                            val latitude = record.get("latitude").toDoubleOrNull()
                                ?: throw InvalidCsvDataException(
                                    fileName = file.originalFilename ?: "unknown",
                                    lineNumber = index + 2,
                                    columnName = "latitude",
                                    expectedType = "Double",
                                    actualValue = record.get("latitude")
                                )
                            
                            val site = siteFactory.create(name, latitude, ...)
                            sites.add(site)
                            
                        } catch (e: InvalidCsvDataException) {
                            throw e  // Re-throw domain exception
                        } catch (e: Exception) {
                            throw InvalidCsvDataException(
                                fileName = file.originalFilename ?: "unknown",
                                lineNumber = index + 2,
                                columnName = "?",
                                expectedType = "?",
                                actualValue = record.toString(),
                                cause = e
                            )
                        }
                    }
                    
                    return siteRepository.saveAll(sites)
                }
        } catch (e: IOException) {
            throw CsvParseException(
                fileName = file.originalFilename ?: "unknown",
                reason = "IOException while reading file",
                cause = e
            )
        }
    }
}
```

**Exception Handler (GlobalExceptionHandler):**
```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {
    
    @ExceptionHandler(InvalidCsvDataException::class)
    fun handleCsvDataException(
        ex: InvalidCsvDataException,
        request: HttpServletRequest
    ): Payload<Map<String, Any>> {
        logger.error("""
            CSV Data Error: ${ex.fileName}:${ex.lineNumber}
            Column: ${ex.columnName}
            Expected: ${ex.expectedType}
            Actual: ${ex.actualValue}
        """.trimIndent(), ex)
        
        return Payload(
            status = BAD_REQUEST,
            message = ex.message ?: "CSV data invalid",
            path = request.requestURI,
            data = mapOf(
                "fileName" to ex.fileName,
                "lineNumber" to ex.lineNumber,
                "column" to ex.columnName,
                "expected" to ex.expectedType,
                "actual" to ex.actualValue
            ),
            errorCode = ex.errorCode
        )
    }
}
```

---

## Pattern 4: QueryDSL for Complex Queries

For complex geospatial + hierarchical queries:

```kotlin
// Repository interface
@Repository
interface SiteRepository : JpaRepository<Site, String>,
                            QuerydslPredicateExecutor<Site> {
}

// Custom implementation
@Repository
class SiteRepositoryImpl(private val queryFactory: JPAQueryFactory) {
    
    fun findSitesNearbyWithPipes(
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): List<Site> {
        val site = QSite.site
        
        return queryFactory
            .selectFrom(site)
            .leftJoin(site.pipes).fetchJoin()  // Eager load pipes to avoid N+1
            .where(
                site.location.latitude.between(
                    latitude - radiusKm / 111.0,
                    latitude + radiusKm / 111.0
                ).and(
                    site.location.longitude.between(
                        longitude - radiusKm / (111.0 * cos(Math.toRadians(latitude))),
                        longitude + radiusKm / (111.0 * cos(Math.toRadians(latitude)))
                    )
                )
            )
            .distinct()
            .fetch()
    }
    
    fun findSitesByName(prefix: String): List<Site> {
        val site = QSite.site
        return queryFactory
            .selectFrom(site)
            .where(site.name.startsWith(prefix))
            .orderBy(site.name.asc())
            .fetch()
    }
}
```

---

## Pattern 5: Value Object Collections

dtxiotv3 tracks measurement data (time-series) as embedded collections:

```kotlin
@Entity
class MeasurementSet : BaseEntity() {
    
    @ElementCollection(fetch = LAZY)
    @CollectionTable(name = "measurements", joinColumns = [JoinColumn(name = "set_id")])
    @OrderColumn(name = "measurement_index")
    var measurements: MutableList<Measurement> = mutableListOf()
    
    fun addMeasurement(value: Double, timestamp: LocalDateTime) {
        measurements.add(Measurement(value, timestamp))
    }
}

@Embeddable
data class Measurement(
    @Column(nullable = false)
    val value: Double,
    
    @Column(nullable = false)
    val timestamp: LocalDateTime,
    
    @Column(nullable = false)
    val quality: Int = 100  // 0-100 quality score
) {
    init {
        require(quality in 0..100) { "Quality must be 0-100" }
    }
}

// Database
CREATE TABLE measurements (
    set_id VARCHAR(13) NOT NULL,
    measurement_index INT NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    quality INT NOT NULL DEFAULT 100,
    PRIMARY KEY (set_id, measurement_index),
    FOREIGN KEY (set_id) REFERENCES measurement_set(set_id) ON DELETE CASCADE
);

CREATE INDEX idx_measurement_timestamp ON measurements(timestamp);
```

---

## Lessons Learned: Pitfalls & Real-World Fixes

Actual problems discovered during production use and how to prevent them:

### Pattern 1: Hierarchical Aggregates - Depth Limits

**The Problem:**
```kotlin
// Started with 3 levels - worked great
Site (1) ─< (N) Pipe (1) ─< (N) Channel

// Then added 4th level
Site (1) ─< (N) Pipe (1) ─< (N) Channel (1) ─< (N) Sensor

// Then added 5th level - queries got slow, caching failed
Site ─< Pipe ─< Channel ─< Sensor ─< Measurement

// Loading full hierarchy = massive memory, slow queries
```

**The Fix:**
```
✅ Guideline: 3-4 levels maximum before reconsidering

When depth > 4, ask:
1. Do I need the full hierarchy loaded?
   → Use projection instead of full entities
   
2. Can I split into multiple aggregates?
   → Site → Pipe (one aggregate)
   → Channel (separate aggregate, FK to Pipe)
   
3. Is there a read model (CQRS)?
   → Separate read/write models for complex queries
```

**Real Example Fix:**
```kotlin
// ❌ Before: Full hierarchy, slow
val sites = siteRepository.findAll()
sites.forEach { site ->
    site.pipes.forEach { pipe ->
        pipe.channels.forEach { channel ->
            // Process data - memory explodes!
        }
    }
}

// ✅ After: Projection only what's needed
data class SiteChannelDTO(
    val siteId: String,
    val siteName: String,
    val channelId: String,
    val channelName: String
)

@Query("""
    SELECT new com.example.SiteChannelDTO(s.id, s.name, c.id, c.name)
    FROM Site s 
    JOIN Pipe p ON s.id = p.siteId
    JOIN Channel c ON p.id = c.pipeId
    WHERE s.deletedAt IS NULL
""")
fun findSiteChannels(): List<SiteChannelDTO>
```

---

### Pattern 2: Spatial/Geolocation Queries - Database Dependencies

**The Problem:**
```kotlin
// In production: Works perfect with PostgreSQL + PostGIS
@Query("""
    SELECT s FROM Site s WHERE 
    ST_Distance(ST_Point(s.longitude, s.latitude), 
                ST_Point(?1, ?2)) <= ?3
""")
fun findNearby(lon: Double, lat: Double, radiusKm: Double): List<Site>

// In integration tests with H2: ST_Distance not available!
// Application-level distance calculation: Slow for large datasets
```

**The Fix:**
```kotlin
// Abstract spatial queries
interface SiteRepository : JpaRepository<Site, String> {
    fun findNearby(lat: Double, lon: Double, radiusKm: Double): List<Site>
}

// PostgreSQL implementation: Uses PostGIS
@Repository
class SiteRepositoryPostgres(private val jpa: SiteJpaRepository) 
    : SiteRepository {
    override fun findNearby(lat: Double, lon: Double, radiusKm: Double) 
        = jpa.findNearbyPostGIS(lon, lat, radiusKm)
    
    @Query("""SELECT s FROM Site s WHERE 
              ST_Distance(...) <= ?3""")
    fun findNearbyPostGIS(...): List<Site>
}

// H2/Test implementation: Application-level calculation
@Repository
class SiteRepositoryFallback(private val jpa: SiteJpaRepository) 
    : SiteRepository {
    override fun findNearby(lat: Double, lon: Double, radiusKm: Double) {
        val candidates = jpa.findAllByLatitudeBetween(...)
        return candidates.filter { it.location.distanceTo(target) <= radiusKm }
    }
}

// Test configuration
@TestConfiguration
class TestConfig {
    @Bean
    fun siteRepository(jpa: SiteJpaRepository): SiteRepository 
        = SiteRepositoryFallback(jpa)  // Use fallback in tests
}
```

**Recommendation:**
- ✅ Use spatial database features (PostGIS) in production
- ✅ Provide fallback for testing/development
- ⚠️ Document spatial query limitations clearly
- ⚠️ Be aware of coordinate system (WGS84 vs local)

---

### Pattern 3: Bulk Imports (CSV/File) - Context-Rich Exceptions

**The Problem:**
```kotlin
// User uploads 100k rows
// Parse fails at row 50,000
// Error: "CSV parsing failed"

// Questions:
// - Which row?
// - Which column?
// - What was the bad value?
// - What did we expect?
```

**The Fix:**
```kotlin
data class CsvParseException(
    val fileName: String,
    val lineNumber: Int,          // EXACT row
    val columnName: String,       // EXACT column
    val expectedType: String,     // What we wanted
    val actualValue: String,      // What we got
    val context: Map<String, Any> // Additional context
) : BusinessException(...)

// Usage
try {
    val latitude = row["latitude"].toDouble()
} catch (e: NumberFormatException) {
    throw CsvParseException(
        fileName = file.originalFilename ?: "unknown",
        lineNumber = lineIndex + 2,  // +2 for header and 0-indexing
        columnName = "latitude",
        expectedType = "Double",
        actualValue = row["latitude"],
        context = mapOf(
            "pipeId" to row["pipe_id"],
            "siteId" to row["site_id"],
            "previousRow" to rows.getOrNull(lineIndex - 1)
        )
    )
}

// GlobalExceptionHandler
@ExceptionHandler(CsvParseException::class)
fun handle(ex: CsvParseException) {
    logger.error("""
        CSV Import Failed
        File: ${ex.fileName}
        Line: ${ex.lineNumber}
        Column: ${ex.columnName}
        Expected: ${ex.expectedType}
        Got: ${ex.actualValue}
        Context: ${ex.context}
    """.trimIndent())
    
    return Payload(
        status = BAD_REQUEST,
        message = "Import failed at line ${ex.lineNumber}: ${ex.columnName}",
        data = ex.context
    )
}
```

**Result:** User can immediately fix the exact row/column and retry

---

### Pattern 4: Complex Queries - QueryDSL vs @Query

**The Problem:**
```kotlin
// ❌ @Query gets messy with multiple conditions
@Query("""
    SELECT o FROM Order o
    LEFT JOIN FETCH o.items
    WHERE (:status IS NULL OR o.status = :status)
    AND (:customerId IS NULL OR o.customerId = :customerId)
    AND (:minAmount IS NULL OR o.totalAmount >= :minAmount)
    AND (:maxAmount IS NULL OR o.totalAmount <= :maxAmount)
    AND o.createdDate >= :from
    AND o.createdDate <= :to
    ORDER BY o.createdDate DESC
""")
fun findOrders(
    @Param("status") status: OrderStatus?,
    @Param("customerId") customerId: String?,
    @Param("minAmount") minAmount: BigDecimal?,
    @Param("maxAmount") maxAmount: BigDecimal?,
    @Param("from") from: LocalDateTime,
    @Param("to") to: LocalDateTime,
    pageable: Pageable
): Page<Order>

// Hard to read, easy to miss fetch clause, hard to modify
```

**The Fix with QueryDSL:**
```kotlin
@Repository
class OrderRepositoryCustom(private val queryFactory: JPAQueryFactory) {
    
    fun findOrdersFiltered(filter: OrderFilter, pageable: Pageable): Page<Order> {
        val order = QOrder.order
        
        var query = queryFactory
            .selectFrom(order)
            .leftJoin(order.items).fetchJoin()
            .distinct()
        
        // Build conditions fluently
        filter.status?.let { 
            query = query.where(order.status.eq(it))
        }
        filter.customerId?.let {
            query = query.where(order.customerId.eq(it))
        }
        filter.minAmount?.let {
            query = query.where(order.totalAmount.goe(it))
        }
        filter.maxAmount?.let {
            query = query.where(order.totalAmount.loe(it))
        }
        
        query = query
            .where(order.createdDate.between(filter.from, filter.to))
            .orderBy(order.createdDate.desc())
        
        // Pagination
        val total = query.fetchCount()
        val results = query
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
        
        return PageImpl(results, pageable, total)
    }
}

// Usage: Clear intent, easy to modify
val orders = repository.findOrdersFiltered(
    OrderFilter(
        status = OrderStatus.SHIPPED,
        minAmount = BigDecimal("1000"),
        from = LocalDateTime.now().minusDays(30),
        to = LocalDateTime.now()
    ),
    PageRequest.of(0, 20)
)
```

**Recommendation:**
- ✅ Use QueryDSL for complex queries (>3 conditions)
- ✅ Keep @Query for simple, stable queries
- ⚠️ Don't forget `.distinct()` with `LEFT JOIN FETCH`
- ⚠️ Test pagination carefully with joins

---

### Pattern 5: Time-Series Data - Collection vs Separate Entities

**The Problem:**
```kotlin
// Option 1: ElementCollection (embedded in parent table)
@Entity class MeasurementSet {
    @ElementCollection(fetch = LAZY)
    var measurements: List<Measurement>  // Loaded from separate table
}

// Pros: Simple, loaded with parent
// Cons: Not independently queryable, joins can be slow

// Option 2: Separate Entity (One-To-Many)
@Entity class MeasurementSet {
    @OneToMany(cascade = [ALL], orphanRemoval = true)
    var measurements: List<Measurement>  // Separate table, own lifecycle
}

// Pros: Can query independently, better for large datasets
// Cons: More complex, cascading operations slower
```

**The Fix: Choose based on data characteristics**
```kotlin
// Use ElementCollection for:
// - Small, bounded collections (< 100 items)
// - Never queried independently
// - Example: Measurement tags, metadata

// Use One-To-Many for:
// - Large collections (1000+ items)
// - Need independent queries
// - Need independent pagination
// - Example: Order history, sensor readings

// Time-series specifically:
@Entity class SensorReadings : BaseEntity() {
    @Column(nullable = false)
    var sensorId: String
    
    @OneToMany(cascade = [ALL], orphanRemoval = true, fetch = LAZY)
    @OrderBy("timestamp ASC")  // Important for time-series!
    var readings: MutableList<Reading> = mutableListOf()
}

@Entity class Reading : BaseEntity() {
    @Column(nullable = false)
    var timestamp: LocalDateTime
    
    @Column(nullable = false)
    var value: Double
    
    @Column(nullable = false)
    var quality: Int
}

// Query: Get last 7 days of readings efficiently
@Query("""
    SELECT r FROM Reading r
    WHERE r.sensorId = ?1
    AND r.timestamp >= ?2
    ORDER BY r.timestamp DESC
""")
fun findRecentReadings(sensorId: String, from: LocalDateTime): List<Reading>
```

**Archival Strategy:**
```kotlin
// As readings accumulate, archive old data
@Entity class ReadingArchive : BaseEntity() {
    // Same structure as Reading
    var timestamp: LocalDateTime
    var value: Double
    var quality: Int
    var archivedDate: LocalDateTime = LocalDateTime.now()
}

// Periodically:
// 1. Copy readings older than 1 year to archive
// 2. Delete from active table
// 3. Keep indexes optimized

@Service
class ReadingArchivalService(...) {
    @Scheduled(cron = "0 0 1 1 * *")  // Monthly
    @Transactional
    fun archiveOldReadings() {
        val cutoff = LocalDateTime.now().minusYears(1)
        val toArchive = readingRepository.findByTimestampBefore(cutoff)
        
        archiveRepository.saveAll(toArchive.map { it.toArchive() })
        readingRepository.deleteAllInBatch(toArchive)
    }
}
```

---

## Key Takeaways

| Pattern | When | How | Watch Out |
|---------|------|-----|-----------|
| Hierarchical Aggregate | Clear ownership hierarchy | 3-4 levels max | Depth → query complexity |
| Spatial Queries | Location-based data | DB functions or app layer | DB dependency, coordinate systems |
| Bulk Imports | CSV/file uploads | Rich exception context | Line numbers, large files |
| Complex Queries | Multiple filters | QueryDSL > @Query | Fetch clauses, pagination |
| Time-Series | Sequential measurements | Archival strategy | Performance at scale |

---

## Applying These Patterns to Your Projects

**After reading this section, ask yourself:**

1. **Do I have hierarchical data?**
   → Pattern 1: Monitor hierarchy depth

2. **Do I need location-based queries?**
   → Pattern 2: Use DB functions, provide fallbacks

3. **Do I import bulk data?**
   → Pattern 3: Add rich context to exceptions

4. **Do I have complex queries?**
   → Pattern 4: Consider QueryDSL

5. **Do I have time-series or accumulated data?**
   → Pattern 5: Plan archival upfront

These patterns emerge from real production systems dealing with scale and complexity. They're not academic exercises—they solve actual problems you'll face.
