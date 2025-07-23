package com.algoreport.config.exception

import org.springframework.http.HttpStatus

/**
 * 시스템 전역에서 사용하는 커스텀 예외 클래스
 * Error enum을 통해 일관된 에러 처리를 제공
 * 
 * @param error 에러 타입 (Error enum)
 * @param additionalMessage 추가 메시지 (선택사항)
 * @param cause 원인 예외 (선택사항)
 */
class CustomException(
    val error: Error,
    additionalMessage: String? = null,
    cause: Throwable? = null
) : RuntimeException(buildMessage(error, additionalMessage), cause) {
    
    /**
     * HTTP 상태 코드
     */
    val httpStatus: HttpStatus = error.status
    
    /**
     * 에러 코드
     */
    val errorCode: String = error.code
    
    companion object {
        /**
         * 에러 메시지를 조합하여 생성
         */
        private fun buildMessage(error: Error, additionalMessage: String?): String {
            return if (additionalMessage.isNullOrBlank()) {
                error.message
            } else {
                "${error.message} $additionalMessage"
            }
        }
    }
}