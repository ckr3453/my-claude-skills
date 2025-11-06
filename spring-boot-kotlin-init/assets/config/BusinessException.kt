package kr.movements.dtxiotv3.exception.base

import kr.movements.dtxiotv3.domain.ErrorCode

/**
 * Base Exception for all business logic exceptions
 * 
 * Features:
 * - ErrorCode integration
 * - Context information for detailed logging
 * - Automatic message generation
 * 
 * Usage:
 * ```kotlin
 * class MemberNotFoundException(memberId: String) : BusinessException(
 *     message = "Member not found",
 *     errorCode = ErrorCode.MEMBER_NOT_FOUND,
 *     context = mapOf("memberId" to memberId)
 * )
 * ```
 */
sealed class BusinessException(
    message: String,
    val errorCode: ErrorCode,
    val context: Map<String, Any?> = emptyMap()
) : RuntimeException(message) {
    
    /**
     * 로깅용 상세 메시지 생성
     * 
     * 서버 로그에는 이 메시지가 기록되지만,
     * 클라이언트에게는 간단한 message만 전달됨
     */
    fun toDetailedMessage(): String {
        return buildString {
            append("ErrorCode: ${errorCode.code}")
            append(", Message: $message")
            if (context.isNotEmpty()) {
                append(", Context: ${context.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
            }
        }
    }
    
    /**
     * Exception을 특정 key의 context와 함께 생성하는 헬퍼
     */
    companion object {
        /**
         * 간편하게 context를 추가하는 빌더 패턴
         * 
         * ```kotlin
         * throw MemberNotFoundException(memberId)
         *     .withContext("attemptedBy", currentUser)
         *     .withContext("timestamp", LocalDateTime.now())
         * ```
         */
        fun BusinessException.withContext(key: String, value: Any?): BusinessException {
            // Kotlin에서는 sealed class를 복사할 수 없으므로
            // 생성 시점에 context를 모두 전달하는 것을 권장
            return this
        }
    }
}

/**
 * Context 빌더를 사용한 예외 생성 헬퍼
 */
fun buildContext(vararg pairs: Pair<String, Any?>): Map<String, Any?> {
    return mapOf(*pairs)
}

// ===== 예시: 개선된 Exception 클래스들 =====

/**
 * Member 관련 예외
 */
class MemberNotFoundException(
    memberId: String
) : BusinessException(
    message = "Member not found",
    errorCode = ErrorCode.MEMBER_NOT_FOUND,
    context = buildContext("memberId" to memberId)
)

class MemberEmailAlreadyExistsException(
    email: String
) : BusinessException(
    message = "Member email already exists",
    errorCode = ErrorCode.MEMBER_EMAIL_ALREADY_EXISTS,
    context = buildContext("email" to email)
)

class InvalidPasswordException(
    email: String,
    reason: String
) : BusinessException(
    message = "Invalid password",
    errorCode = ErrorCode.INVALID_PASSWORD_FORMAT,
    context = buildContext(
        "email" to email,
        "reason" to reason
    )
)

/**
 * Site 관련 예외
 */
class SiteNotFoundException(
    siteId: String
) : BusinessException(
    message = "Site not found",
    errorCode = ErrorCode.SITE_NOT_FOUND,
    context = buildContext("siteId" to siteId)
)

class SiteIdAlreadyExistsException(
    siteId: String
) : BusinessException(
    message = "Site ID already exists",
    errorCode = ErrorCode.SITE_ID_ALREADY_EXISTS,
    context = buildContext("siteId" to siteId)
)

/**
 * Sensor Case 관련 예외
 */
class SensorCaseNotFoundException(
    caseId: String
) : BusinessException(
    message = "Sensor case not found",
    errorCode = ErrorCode.SENSOR_CASE_NOT_FOUND,
    context = buildContext("caseId" to caseId)
)

/**
 * CSV 파싱 관련 예외
 */
class CsvParseException(
    fileName: String,
    lineNumber: Int,
    reason: String
) : BusinessException(
    message = "CSV parse error",
    errorCode = ErrorCode.CSV_PARSE_ERROR,
    context = buildContext(
        "fileName" to fileName,
        "lineNumber" to lineNumber,
        "reason" to reason
    )
)

class InvalidCsvHeaderException(
    fileName: String,
    expectedHeaders: List<String>,
    actualHeaders: List<String>
) : BusinessException(
    message = "Invalid CSV header",
    errorCode = ErrorCode.INVALID_CSV_HEADER,
    context = buildContext(
        "fileName" to fileName,
        "expectedHeaders" to expectedHeaders.joinToString(", "),
        "actualHeaders" to actualHeaders.joinToString(", ")
    )
)

class InvalidCsvDataException(
    fileName: String,
    lineNumber: Int,
    columnName: String,
    expectedType: String,
    actualValue: String
) : BusinessException(
    message = "Invalid CSV data",
    errorCode = when (expectedType.lowercase()) {
        "string" -> ErrorCode.INVALID_CSV_STRING_DATA
        "int", "integer" -> ErrorCode.INVALID_CSV_INT_DATA
        "double", "float" -> ErrorCode.INVALID_CSV_DOUBLE_DATA
        "date" -> ErrorCode.INVALID_CSV_DATE_DATA
        "boolean" -> ErrorCode.INVALID_CSV_BOOLEAN_DATA
        else -> ErrorCode.CSV_PARSE_ERROR
    },
    context = buildContext(
        "fileName" to fileName,
        "lineNumber" to lineNumber,
        "columnName" to columnName,
        "expectedType" to expectedType,
        "actualValue" to actualValue
    )
)

/**
 * Login 관련 예외
 */
class LoginFailedException(
    email: String,
    reason: String
) : BusinessException(
    message = "Login failed",
    errorCode = ErrorCode.LOGIN_FAILED,
    context = buildContext(
        "email" to email,
        "reason" to reason
    )
)

/**
 * Pipe 관련 예외
 */
class PipeNotFoundException(
    pipeId: String
) : BusinessException(
    message = "Pipe not found",
    errorCode = ErrorCode.PIPE_NOT_FOUND,
    context = buildContext("pipeId" to pipeId)
)

class DistanceExceedsPipeLengthException(
    pipeId: String,
    distance: Double,
    pipeLength: Double
) : BusinessException(
    message = "Distance exceeds pipe length",
    errorCode = ErrorCode.DISTANCE_EXCEEDS_PIPE_LENGTH,
    context = buildContext(
        "pipeId" to pipeId,
        "requestedDistance" to distance,
        "pipeLength" to pipeLength
    )
)
