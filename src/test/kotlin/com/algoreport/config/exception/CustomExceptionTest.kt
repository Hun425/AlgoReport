package com.algoreport.config.exception

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.http.HttpStatus

/**
 * CustomException 클래스 테스트
 * TDD Red 단계: 아직 CustomException 클래스가 존재하지 않으므로 이 테스트는 실패할 것임
 */
class CustomExceptionTest : BehaviorSpec({
    
    given("CustomException을 생성할 때") {
        
        `when`("Error enum을 전달하면") {
            val error = Error.USER_NOT_FOUND
            val exception = CustomException(error)
            
            then("에러 정보가 올바르게 설정되어야 한다") {
                exception.error shouldBe error
                exception.message shouldBe error.message
                exception.httpStatus shouldBe HttpStatus.NOT_FOUND
                exception.errorCode shouldBe "E40401"
            }
        }
        
        `when`("Error enum과 추가 메시지를 전달하면") {
            val error = Error.INVALID_INPUT
            val additionalMessage = "사용자 ID는 필수값입니다."
            val exception = CustomException(error, additionalMessage)
            
            then("추가 메시지가 포함되어야 한다") {
                exception.error shouldBe error
                exception.message shouldBe "${error.message} $additionalMessage"
                exception.httpStatus shouldBe HttpStatus.BAD_REQUEST
                exception.errorCode shouldBe "E40001"
            }
        }
        
        `when`("Error enum과 cause를 전달하면") {
            val error = Error.DATABASE_ERROR
            val cause = RuntimeException("Connection timeout")
            val exception = CustomException(error, cause = cause)
            
            then("cause가 올바르게 설정되어야 한다") {
                exception.error shouldBe error
                exception.cause shouldBe cause
                exception.httpStatus shouldBe HttpStatus.INTERNAL_SERVER_ERROR
            }
        }
        
        `when`("모든 파라미터를 전달하면") {
            val error = Error.SOLVEDAC_API_ERROR
            val additionalMessage = "사용자 데이터 조회 실패"
            val cause = RuntimeException("Network error")
            val exception = CustomException(error, additionalMessage, cause)
            
            then("모든 정보가 올바르게 설정되어야 한다") {
                exception.error shouldBe error
                exception.message shouldBe "${error.message} $additionalMessage"
                exception.cause shouldBe cause
                exception.httpStatus shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                exception.errorCode shouldBe "E50002"
            }
        }
    }
    
    given("다른 CustomException과 비교할 때") {
        val error1 = Error.USER_NOT_FOUND
        val error2 = Error.STUDY_GROUP_NOT_FOUND
        
        `when`("같은 에러 타입이면") {
            val exception1 = CustomException(error1)
            val exception2 = CustomException(error1)
            
            then("같은 에러 코드를 가져야 한다") {
                exception1.errorCode shouldBe exception2.errorCode
                exception1.httpStatus shouldBe exception2.httpStatus
            }
        }
        
        `when`("다른 에러 타입이면") {
            val exception1 = CustomException(error1)
            val exception2 = CustomException(error2)
            
            then("다른 에러 코드를 가져야 한다") {
                exception1.errorCode shouldNotBe exception2.errorCode
                exception1.error shouldNotBe exception2.error
            }
        }
    }
})