package com.algoreport.config.exception

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

/**
 * 전역 예외 처리 핸들러
 * 모든 컨트롤러에서 발생하는 예외를 일관된 형태로 처리
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    
    /**
     * CustomException 처리
     */
    @ExceptionHandler(CustomException::class)
    fun handleCustomException(exception: CustomException): ResponseEntity<ErrorResponse> {
        logger.warn("CustomException occurred: ${exception.errorCode} - ${exception.message}", exception)
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = exception.httpStatus.value(),
            error = exception.httpStatus.reasonPhrase,
            code = exception.errorCode,
            message = exception.message ?: exception.error.message,
            path = null // 추후 HttpServletRequest를 통해 path 정보 추가 가능
        )
        
        return ResponseEntity.status(exception.httpStatus).body(errorResponse)
    }
    
    /**
     * Validation 예외 처리 (@Valid 어노테이션 관련)
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(exception: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        logger.warn("Validation exception occurred: ${exception.message}")
        
        val fieldErrors = exception.bindingResult.fieldErrors
            .map { "${it.field}: ${it.defaultMessage}" }
            .joinToString(", ")
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = 400,
            error = "Bad Request",
            code = "E40001",
            message = "입력값 검증 실패: $fieldErrors",
            path = null
        )
        
        return ResponseEntity.badRequest().body(errorResponse)
    }
    
    /**
     * 예상하지 못한 예외 처리
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(exception: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected exception occurred", exception)
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = 500,
            error = "Internal Server Error",
            code = "E50001",
            message = "서버 내부 오류가 발생했습니다.",
            path = null
        )
        
        return ResponseEntity.internalServerError().body(errorResponse)
    }
}

/**
 * 에러 응답 DTO
 */
data class ErrorResponse(
    val timestamp: LocalDateTime,
    val status: Int,
    val error: String,
    val code: String,
    val message: String,
    val path: String?
)