package kr.movements.dtxiotv3.exception

import jakarta.servlet.http.HttpServletRequest
import kr.movements.dtxiotv3.exception.base.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

/**
 * Global Exception Handler
 * 
 * Responsibilities:
 * - Exception logging with detailed context
 * - Standardized error response formatting
 * - Client-friendly error messages
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    /**
     * Business Exception 처리
     * 
     * 여기서 상세 로깅 수행:
     * - ErrorCode
     * - Context 정보
     * - 요청 정보
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        
        // 상세 로깅 (서버용)
        logger.error(
            "Business Exception - ErrorCode: {}, Message: {}, Context: {}, Path: {}, Method: {}",
            ex.errorCode.code,
            ex.message,
            ex.context.entries.joinToString(", ") { "${it.key}=${it.value}" },
            request.requestURI,
            request.method,
            ex  // 스택 트레이스 포함
        )
        
        // 클라이언트 응답 (간단하게)
        val problem = ProblemDetail.forStatusAndDetail(
            ex.errorCode.httpStatus,
            ex.message ?: "Business error occurred"
        )
        
        problem.title = ex.errorCode.code
        problem.instance = request.requestURI.toURI()
        problem.setProperty("errorCode", ex.errorCode.code)
        problem.setProperty("timestamp", LocalDateTime.now())
        
        return ResponseEntity
            .status(ex.errorCode.httpStatus)
            .body(problem)
    }
    
    /**
     * Validation Exception 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        
        val errors = ex.bindingResult.allErrors.associate { error ->
            val fieldName = (error as? FieldError)?.field ?: error.objectName
            val message = error.defaultMessage ?: "Invalid value"
            fieldName to message
        }
        
        logger.warn(
            "Validation Failed - Path: {}, Method: {}, Errors: {}",
            request.requestURI,
            request.method,
            errors
        )
        
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
        )
        
        problem.title = "VALIDATION_ERROR"
        problem.instance = request.requestURI.toURI()
        problem.setProperty("errorCode", "CMN-001")
        problem.setProperty("timestamp", LocalDateTime.now())
        problem.setProperty("errors", errors)
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(problem)
    }
    
    /**
     * 일반 Exception 처리
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        
        logger.error(
            "Unexpected Exception - Path: {}, Method: {}, ExceptionType: {}",
            request.requestURI,
            request.method,
            ex::class.simpleName,
            ex
        )
        
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred"
        )
        
        problem.title = "INTERNAL_SERVER_ERROR"
        problem.instance = request.requestURI.toURI()
        problem.setProperty("errorCode", "CMN-500")
        problem.setProperty("timestamp", LocalDateTime.now())
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(problem)
    }
}

private fun String.toURI(): java.net.URI {
    return java.net.URI.create(this)
}
