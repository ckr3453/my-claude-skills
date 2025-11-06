package kr.movements.dtxiotv3.aop

import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.*
import java.util.stream.Collectors

/**
 * Logging Aspect for standardized log format
 * 
 * Features:
 * - Controller: Request/Response logging with MDC
 * - Service: Method execution logging (before/after)
 * - Unified log format across the application
 * 
 * Note: Exception handling is done in GlobalExceptionHandler
 */

inline fun <reified T> T.logger() = LoggerFactory.getLogger(T::class.java)!!

@Component
@Aspect
class LoggerAspect {
    
    private val logger = logger()

    private fun paramMapToString(paramMap: Map<String, Array<String>>): String {
        return paramMap.entries.stream()
            .map { String.format("%s -> (%s)", it.key, it.value.joinToString()) }
            .collect(Collectors.joining(", "))
    }

    /**
     * Controller 요청/응답 로깅
     */
    @Pointcut("within(kr.movements.dtxiotv3.controller..*)")
    fun onRequest() {}

    @Around("kr.movements.dtxiotv3.aop.LoggerAspect.onRequest()")
    @Throws(Throwable::class)
    fun doLogging(pjp: ProceedingJoinPoint): Any? {
        val request: HttpServletRequest = 
            (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        val paramMap: Map<String, Array<String>> = request.parameterMap
        
        // MDC 설정 - 모든 로그에 requestId 포함
        val requestId = UUID.randomUUID().toString()
        MDC.put("requestId", requestId)
        MDC.put("uri", request.requestURI)
        MDC.put("method", request.method)
        
        var params = ""
        if (paramMap.isNotEmpty()) {
            params = " [" + paramMapToString(paramMap) + "]"
        }
        
        val start = System.currentTimeMillis()
        
        return try {
            pjp.proceed(pjp.args)
        } finally {
            val end = System.currentTimeMillis()
            
            // 통일된 로그 포맷
            logger.info(
                "Request: {} {}{} < {} ({}ms)",
                request.method,
                request.requestURI,
                params,
                request.getClientIpAddress(),
                end - start
            )
            
            // MDC 정리
            MDC.clear()
        }
    }

    /**
     * Service 메서드 실행 전/후 로깅
     * 
     * @Service 어노테이션이 붙은 클래스의 모든 public 메서드를 대상으로 함
     */
    @Pointcut("within(@org.springframework.stereotype.Service *)")
    fun onService() {}

    @Around("kr.movements.dtxiotv3.aop.LoggerAspect.onService()")
    @Throws(Throwable::class)
    fun doServiceLogging(pjp: ProceedingJoinPoint): Any? {
        // DEBUG 레벨이 아니면 실행하지 않음 (성능 최적화)
        if (!logger.isDebugEnabled) {
            return pjp.proceed(pjp.args)
        }
        
        val className = pjp.signature.declaringTypeName.substringAfterLast('.')
        val methodName = pjp.signature.name
        val fullMethodName = "$className.$methodName"
        val args = pjp.args
        
        val start = System.currentTimeMillis()
        
        // 실행 전 로그
        logger.debug(
            ">>> START: {} with args: {}",
            fullMethodName,
            formatArgs(args)
        )
        
        return try {
            val result = pjp.proceed(args)
            val end = System.currentTimeMillis()
            
            // 성공 로그
            logger.debug(
                "<<< END: {} - SUCCESS ({}ms)",
                fullMethodName,
                end - start
            )
            
            result
        } catch (e: Exception) {
            val end = System.currentTimeMillis()
            
            // 실패 로그 (간단하게 - 상세한 건 GlobalExceptionHandler에서)
            logger.debug(
                "<<< END: {} - FAILED: {} ({}ms)",
                fullMethodName,
                e.javaClass.simpleName,
                end - start
            )
            
            throw e
        }
    }

    /**
     * Arguments를 로그 출력용으로 포맷팅
     * 
     * 민감정보 마스킹 처리 포함
     */
    private fun formatArgs(args: Array<Any?>): String {
        if (args.isEmpty()) return "[]"
        
        return args.joinToString(", ", "[", "]") { arg ->
            when {
                arg == null -> "null"
                arg is String && arg.length > 100 -> "${arg.take(100)}..."
                arg is ByteArray -> "ByteArray[${arg.size}]"
                // 비밀번호 등 민감정보 마스킹
                arg.toString().contains("password", ignoreCase = true) -> "***MASKED***"
                arg.toString().contains("token", ignoreCase = true) -> "***MASKED***"
                else -> arg.toString()
            }
        }
    }

    companion object {
        private val logger = logger()
    }
}

/**
 * HttpServletRequest 확장 함수
 */
fun HttpServletRequest.getClientIpAddress(): String {
    val xForwardedForHeader = getHeader("X-Forwarded-For")
    return if (xForwardedForHeader == null || xForwardedForHeader.isEmpty()) {
        remoteAddr
    } else {
        xForwardedForHeader.split(",")[0].trim()
    }
}
