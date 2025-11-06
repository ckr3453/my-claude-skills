package TODO.domain.base  // TODO: Replace with actual package

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * Base Entity with JPA Auditing
 * 
 * All domain entities should extend this class to automatically track:
 * - Creation date and user
 * - Last modification date and user
 * 
 * Requires @EnableJpaAuditing in configuration class
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdDate: LocalDateTime = LocalDateTime.now()
        protected set

    @LastModifiedDate
    @Column(nullable = false)
    var lastModifiedDate: LocalDateTime = LocalDateTime.now()
        protected set

    @CreatedBy
    @Column(length = 50, updatable = false)
    var createdBy: String? = null
        protected set

    @LastModifiedBy
    @Column(length = 50)
    var lastModifiedBy: String? = null
        protected set
}
