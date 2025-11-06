package TODO.dto  // TODO: Replace with actual package

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.http.HttpStatus

/**
 * Standard API Response Wrapper
 * 
 * All API endpoints should return this response format for consistency
 * 
 * @param T Type of response data (nullable)
 * @property status HTTP status
 * @property message Response message
 * @property path Request path
 * @property data Response payload (optional)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Payload<T>(
    val status: HttpStatus,
    val message: String,
    val path: String,
    val data: T? = null
) {
    companion object {
        fun <T> ok(path: String, data: T? = null): Payload<T> {
            return Payload(
                status = HttpStatus.OK,
                message = "Success",
                path = path,
                data = data
            )
        }

        fun <T> created(path: String, data: T? = null): Payload<T> {
            return Payload(
                status = HttpStatus.CREATED,
                message = "Created",
                path = path,
                data = data
            )
        }

        fun <T> noContent(path: String): Payload<T> {
            return Payload(
                status = HttpStatus.NO_CONTENT,
                message = "No Content",
                path = path,
                data = null
            )
        }
    }
}
