# Geospatial Optimization Patterns for Spring Boot Kotlin

## PostGIS Query Optimization

### Spatial Index Usage

```kotlin
// ⚠️ Consider: No spatial index, full table scan
@Entity
@Table(name = "locations")
class Location(
    @Id val id: Long,
    
    @Column(columnDefinition = "geometry(Point, 4326)")
    val point: Point
)

interface LocationRepository : JpaRepository<Location, Long> {
    @Query("""
        SELECT l FROM Location l 
        WHERE ST_Distance(l.point, :targetPoint) < :radius
    """, nativeQuery = true)
    fun findNearby(targetPoint: Point, radius: Double): List<Location>
}

// 💡 Alternative: Use spatial index with ST_DWithin
@Entity
@Table(
    name = "locations",
    indexes = [
        Index(name = "idx_location_point", columnList = "point")
    ]
)
class Location(
    @Id val id: Long,
    
    @Column(columnDefinition = "geometry(Point, 4326)", nullable = false)
    val point: Point
)

// Create spatial index in migration
// CREATE INDEX idx_location_point ON locations USING GIST(point);

interface LocationRepository : JpaRepository<Location, Long> {
    // ST_DWithin uses spatial index
    @Query("""
        SELECT * FROM locations 
        WHERE ST_DWithin(point, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
    """, nativeQuery = true)
    fun findWithinRadius(
        @Param("lng") longitude: Double,
        @Param("lat") latitude: Double,
        @Param("radiusMeters") radiusMeters: Double
    ): List<Location>
}
```

### Geometry vs Geography

```kotlin
// Use GEOMETRY for:
// - Planar calculations (faster)
// - Small areas where earth curvature negligible
// - When precision < 1km is acceptable
@Column(columnDefinition = "geometry(Point, 4326)")
val geometryPoint: Point

// Use GEOGRAPHY for:
// - Accurate distance calculations over long distances
// - Global scale applications
// - When precision matters (航空, 해상 운송)
@Column(columnDefinition = "geography(Point, 4326)")
val geographyPoint: Point

// Example: City-level search vs Country-level search
interface LocationRepository : JpaRepository<Location, Long> {
    // City-level: Geometry is fine (faster)
    @Query("""
        SELECT * FROM locations 
        WHERE ST_DWithin(
            geometry_point, 
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
            :radiusDegrees
        )
    """, nativeQuery = true)
    fun findNearbyInCity(lng: Double, lat: Double, radiusDegrees: Double): List<Location>
    
    // Country/Global: Use Geography (accurate)
    @Query("""
        SELECT * FROM locations 
        WHERE ST_DWithin(
            geography_point, 
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
            :radiusMeters
        )
    """, nativeQuery = true)
    fun findNearbyGlobal(lng: Double, lat: Double, radiusMeters: Double): List<Location>
}
```

### Bounding Box Pre-filtering

```kotlin
// ⚠️ Consider: Expensive distance calculation on all rows
@Query("""
    SELECT * FROM locations 
    WHERE ST_Distance(point::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) < :radiusMeters
    ORDER BY ST_Distance(point::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography)
""", nativeQuery = true)
fun findNearby(lng: Double, lat: Double, radiusMeters: Double): List<Location>

// 💡 Alternative: Bounding box pre-filter with spatial index
@Query("""
    SELECT * FROM locations 
    WHERE ST_DWithin(
        point::geography, 
        ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, 
        :radiusMeters
    )
    ORDER BY point::geography <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
    LIMIT :limit
""", nativeQuery = true)
fun findNearby(
    lng: Double, 
    lat: Double, 
    radiusMeters: Double,
    limit: Int = 100
): List<Location>

// Even better: Explicit bounding box for very large datasets
@Query("""
    WITH bbox AS (
        SELECT ST_Expand(
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography::geometry,
            :degrees
        ) AS box
    )
    SELECT l.* FROM locations l, bbox
    WHERE l.point && bbox.box
    AND ST_DWithin(
        l.point::geography,
        ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
        :radiusMeters
    )
    ORDER BY l.point::geography <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
    LIMIT :limit
""", nativeQuery = true)
fun findNearbyOptimized(lng: Double, lat: Double, radiusMeters: Double, degrees: Double, limit: Int): List<Location>
```

## Coordinate System Management

### SRID Validation

```kotlin
// Value object for validated coordinates
@Embeddable
data class Coordinate(
    @Column(name = "longitude")
    val longitude: Double,
    
    @Column(name = "latitude")
    val latitude: Double
) {
    companion object {
        const val WGS84_SRID = 4326
        const val WEB_MERCATOR_SRID = 3857
    }
    
    init {
        require(longitude in -180.0..180.0) {
            "Longitude must be between -180 and 180, got: $longitude"
        }
        require(latitude in -90.0..90.0) {
            "Latitude must be between -90 and 90, got: $latitude"
        }
    }
    
    fun toPoint(srid: Int = WGS84_SRID): Point {
        val geometryFactory = GeometryFactory(PrecisionModel(), srid)
        return geometryFactory.createPoint(org.locationtech.jts.geom.Coordinate(longitude, latitude))
    }
    
    fun toWKT(): String = "POINT($longitude $latitude)"
}

// Usage in entity
@Entity
class Location(
    @Id val id: Long,
    
    @Embedded
    val coordinate: Coordinate,
    
    // Store as geometry for database operations
    @Column(columnDefinition = "geometry(Point, 4326)", nullable = false)
    val point: Point = coordinate.toPoint()
)
```

### SRID Transform Handling

```kotlin
// ⚠️ Consider: Implicit SRID assumption
fun calculateDistance(point1: Point, point2: Point): Double {
    return point1.distance(point2)  // Wrong! Assumes same SRID
}

// 💡 Alternative: Explicit SRID handling
class GeoSpatialService {
    companion object {
        const val WGS84_SRID = 4326
        const val WEB_MERCATOR_SRID = 3857
    }
    
    fun calculateDistanceMeters(point1: Point, point2: Point): Double {
        // Ensure both points are in WGS84 for accurate distance
        val p1 = ensureSRID(point1, WGS84_SRID)
        val p2 = ensureSRID(point2, WGS84_SRID)
        
        // Use geography type for accurate meter calculation
        return calculateGeographicDistance(p1, p2)
    }
    
    private fun ensureSRID(point: Point, targetSRID: Int): Point {
        if (point.srid == targetSRID) return point
        
        // Transform via database for accuracy
        return transformPointViaST(point, targetSRID)
    }
}
```

## GeoJSON Processing

### Streaming Large GeoJSON

```kotlin
// ⚠️ Consider: Load entire GeoJSON into memory
@Service
class GeoJsonImportService(
    private val locationRepository: LocationRepository
) {
    fun importGeoJson(file: MultipartFile) {
        val geoJson = objectMapper.readValue(
            file.inputStream, 
            FeatureCollection::class.java
        )  // OOM risk with large files
        
        geoJson.features.forEach { feature ->
            val location = feature.toLocation()
            locationRepository.save(location)
        }
    }
}

// 💡 Alternative: Stream processing
@Service
class GeoJsonImportService(
    private val locationRepository: LocationRepository,
    private val entityManager: EntityManager
) {
    companion object {
        const val BATCH_SIZE = 500
    }
    
    @Transactional
    fun importGeoJson(file: MultipartFile) {
        val parser = JsonFactory().createParser(file.inputStream)
        val locations = mutableListOf<Location>()
        var count = 0
        
        // Stream parse JSON
        while (parser.nextToken() != null) {
            if (parser.currentToken == JsonToken.START_OBJECT 
                && parser.currentName == "features") {
                
                val feature = objectMapper.readValue(parser, Feature::class.java)
                locations.add(feature.toLocation())
                
                // Batch insert
                if (locations.size >= BATCH_SIZE) {
                    batchInsert(locations)
                    locations.clear()
                    count += BATCH_SIZE
                    logger.info("Imported $count locations...")
                }
            }
        }
        
        // Insert remaining
        if (locations.isNotEmpty()) {
            batchInsert(locations)
        }
        
        parser.close()
    }
    
    private fun batchInsert(locations: List<Location>) {
        locations.forEach { entityManager.persist(it) }
        entityManager.flush()
        entityManager.clear()
    }
}
```

### GeoJSON Response Optimization

```kotlin
// ⚠️ Consider: Load all geometries into memory
@RestController
class LocationController(
    private val locationRepository: LocationRepository
) {
    @GetMapping("/locations")
    fun getLocations(): FeatureCollection {
        val locations = locationRepository.findAll()  // Potentially millions
        return locations.toFeatureCollection()
    }
}

// 💡 Alternative: Pagination + Simplification
@RestController
class LocationController(
    private val locationRepository: LocationRepository
) {
    @GetMapping("/locations")
    fun getLocations(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
        @RequestParam(required = false) simplify: Double?
    ): Page<LocationDto> {
        val pageable = PageRequest.of(page, size)
        
        return if (simplify != null) {
            // Simplify geometries for map display
            locationRepository.findAllSimplified(simplify, pageable)
        } else {
            locationRepository.findAll(pageable).map { it.toDto() }
        }
    }
}

interface LocationRepository : JpaRepository<Location, Long> {
    @Query("""
        SELECT 
            l.id as id,
            l.name as name,
            ST_AsGeoJSON(ST_Simplify(l.geometry, :tolerance)) as geometry
        FROM locations l
    """, nativeQuery = true)
    fun findAllSimplified(
        @Param("tolerance") tolerance: Double,
        pageable: Pageable
    ): Page<LocationDto>
}
```

## Spatial Relationship Queries

### Containment Checks

```kotlin
interface RegionRepository : JpaRepository<Region, Long> {
    // Check if point is within region (uses spatial index)
    @Query("""
        SELECT r.* FROM regions r
        WHERE ST_Contains(r.boundary, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
    """, nativeQuery = true)
    fun findRegionContaining(
        @Param("lng") longitude: Double,
        @Param("lat") latitude: Double
    ): Region?
    
    // Find all regions intersecting with given geometry
    @Query("""
        SELECT r.* FROM regions r
        WHERE ST_Intersects(r.boundary, :geometry)
    """, nativeQuery = true)
    fun findIntersecting(@Param("geometry") geometry: Geometry): List<Region>
}
```

### Route Optimization

```kotlin
// Find locations along a route (buffer around line)
interface LocationRepository : JpaRepository<Location, Long> {
    @Query("""
        WITH route_buffer AS (
            SELECT ST_Buffer(
                ST_MakeLine(ARRAY[
                    ST_SetSRID(ST_MakePoint(:startLng, :startLat), 4326)::geography,
                    ST_SetSRID(ST_MakePoint(:endLng, :endLat), 4326)::geography
                ]::geometry[]),
                :bufferMeters
            ) AS buffer
        )
        SELECT l.* FROM locations l, route_buffer rb
        WHERE ST_Intersects(l.point::geography::geometry, rb.buffer)
        ORDER BY ST_Distance(
            l.point::geography,
            ST_MakeLine(ARRAY[
                ST_SetSRID(ST_MakePoint(:startLng, :startLat), 4326)::geography,
                ST_SetSRID(ST_MakePoint(:endLng, :endLat), 4326)::geography
            ]::geometry[])::geography
        )
    """, nativeQuery = true)
    fun findAlongRoute(
        startLng: Double,
        startLat: Double,
        endLng: Double,
        endLat: Double,
        bufferMeters: Double
    ): List<Location>
}
```

## Performance Monitoring

### Spatial Query Logging

```kotlin
@Component
@Aspect
class SpatialQueryLogger {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val slowQueryThreshold = 1000L // 1 second
    
    @Around("execution(* *..*Repository+.find*(..))")
    fun logSpatialQuery(joinPoint: ProceedingJoinPoint): Any? {
        val start = System.currentTimeMillis()
        
        return try {
            joinPoint.proceed()
        } finally {
            val duration = System.currentTimeMillis() - start
            if (duration > slowQueryThreshold) {
                logger.warn(
                    "Slow spatial query detected: {} took {}ms with args: {}",
                    joinPoint.signature.name,
                    duration,
                    joinPoint.args.joinToString()
                )
            }
        }
    }
}
```

## Common Pitfalls

### 1. Forgetting to Create Spatial Index
```sql
-- Always create GIST index on geometry columns
CREATE INDEX idx_locations_point ON locations USING GIST(point);
CREATE INDEX idx_regions_boundary ON regions USING GIST(boundary);
```

### 2. Using Wrong Distance Function
```kotlin
// ⚠️ Consider: ST_Distance on geometry (degrees, not meters)
ST_Distance(point1, point2) < 0.01  // What is 0.01 degrees in km?

// 💡 Alternative: ST_Distance on geography (meters)
ST_Distance(point1::geography, point2::geography) < 1000  // 1km
```

### 3. Not Validating Input Coordinates
```kotlin
// Always validate before database operations
fun validateCoordinate(lng: Double, lat: Double) {
    require(lng in -180.0..180.0) { "Invalid longitude: $lng" }
    require(lat in -90.0..90.0) { "Invalid latitude: $lat" }
}
```

### 4. Mixing SRIDs
```kotlin
// ⚠️ Consider: Comparing different SRIDs
ST_Distance(point_4326, point_3857)  // Wrong!

// 💡 Alternative: Transform first
ST_Distance(ST_Transform(point_3857, 4326), point_4326)
```
