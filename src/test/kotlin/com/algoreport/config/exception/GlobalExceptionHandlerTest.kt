package com.algoreport.config.exception

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.http.HttpStatus

/**
 * GlobalExceptionHandler 테스트
 */
class GlobalExceptionHandlerTest : BehaviorSpec({
    
    val handler = GlobalExceptionHandler()
    
    given("GlobalExceptionHandler가 CustomException을 처리할 때") {
        
        `when`("기본 CustomException이 발생하면") {
            val customException = CustomException(Error.USER_NOT_FOUND)
            val response = handler.handleCustomException(customException)
            
            then("올바른 ErrorResponse를 반환해야 한다") {
                response.statusCode shouldBe HttpStatus.NOT_FOUND
                response.body?.status shouldBe 404
                response.body?.code shouldBe "E40401"
                response.body?.message shouldBe "해당 사용자를 찾을 수 없습니다."
                response.body?.error shouldBe "Not Found"
                response.body?.timestamp shouldNotBe null
            }
        }
        
        `when`("추가 메시지가 있는 CustomException이 발생하면") {
            val customException = CustomException(Error.INVALID_INPUT, "사용자 ID가 누락되었습니다.")
            val response = handler.handleCustomException(customException)
            
            then("추가 메시지가 포함된 ErrorResponse를 반환해야 한다") {
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.body?.status shouldBe 400
                response.body?.code shouldBe "E40001"
                response.body?.message shouldBe "입력값이 올바르지 않습니다. 사용자 ID가 누락되었습니다."
            }
        }
    }
    
    given("GlobalExceptionHandler가 일반 Exception을 처리할 때") {
        
        `when`("예상하지 못한 Exception이 발생하면") {
            val genericException = RuntimeException("예상하지 못한 오류")
            val response = handler.handleGenericException(genericException)
            
            then("내부 서버 오류 응답을 반환해야 한다") {
                response.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                response.body?.status shouldBe 500
                response.body?.code shouldBe "E50001"
                response.body?.message shouldBe "서버 내부 오류가 발생했습니다."
                response.body?.error shouldBe "Internal Server Error"
            }
        }
    }
})