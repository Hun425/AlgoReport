package com.algoreport.config.security

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * JwtUtil 테스트
 * TDD Red 단계: 아직 JwtUtil 클래스가 존재하지 않으므로 테스트 실패 예상
 */
class JwtUtilTest : BehaviorSpec({
    
    given("JwtUtil이 JWT 토큰을 생성할 때") {
        
        val jwtUtil = JwtUtil(
            secretKey = "test-secret-key-for-jwt-token-generation-minimum-256-bits",
            expirationMs = 3600000L // 1시간
        )
        
        `when`("유효한 사용자 ID로 토큰을 생성하면") {
            val userId = 123L
            val token = jwtUtil.generateToken(userId)
            
            then("JWT 토큰이 생성되어야 한다") {
                token shouldNotBe null
                token.split(".").size shouldBe 3 // Header.Payload.Signature
            }
            
            then("토큰에서 사용자 ID를 추출할 수 있어야 한다") {
                val extractedUserId = jwtUtil.getUserIdFromToken(token)
                extractedUserId shouldBe userId
            }
            
            then("생성된 토큰은 유효해야 한다") {
                val isValid = jwtUtil.validateToken(token)
                isValid shouldBe true
            }
        }
        
        `when`("추가 클레임과 함께 토큰을 생성하면") {
            val userId = 456L
            val email = "test@example.com"
            val role = "USER"
            
            val token = jwtUtil.generateToken(userId, mapOf(
                "email" to email,
                "role" to role
            ))
            
            then("토큰에서 추가 클레임을 추출할 수 있어야 한다") {
                val extractedEmail = jwtUtil.getClaimFromToken(token, "email")
                val extractedRole = jwtUtil.getClaimFromToken(token, "role")
                
                extractedEmail shouldBe email
                extractedRole shouldBe role
            }
        }
    }
    
    given("JwtUtil이 JWT 토큰을 검증할 때") {
        
        val jwtUtil = JwtUtil(
            secretKey = "test-secret-key-for-jwt-token-generation-minimum-256-bits",
            expirationMs = 1000L // 1초 (만료 테스트용)
        )
        
        `when`("유효한 토큰을 검증하면") {
            val token = jwtUtil.generateToken(789L)
            val isValid = jwtUtil.validateToken(token)
            
            then("true를 반환해야 한다") {
                isValid shouldBe true
            }
        }
        
        `when`("만료된 토큰을 검증하면") {
            val token = jwtUtil.generateToken(999L)
            Thread.sleep(1100) // 1.1초 대기하여 토큰 만료
            
            val isValid = jwtUtil.validateToken(token)
            
            then("false를 반환해야 한다") {
                isValid shouldBe false
            }
        }
        
        `when`("잘못된 형식의 토큰을 검증하면") {
            val invalidToken = "invalid.token.format"
            val isValid = jwtUtil.validateToken(invalidToken)
            
            then("false를 반환해야 한다") {
                isValid shouldBe false
            }
        }
        
        `when`("빈 토큰을 검증하면") {
            val isValid = jwtUtil.validateToken("")
            
            then("false를 반환해야 한다") {
                isValid shouldBe false
            }
        }
        
        `when`("null 토큰을 검증하면") {
            val isValid = jwtUtil.validateToken(null)
            
            then("false를 반환해야 한다") {
                isValid shouldBe false
            }
        }
    }
    
    given("JwtUtil이 토큰에서 정보를 추출할 때") {
        
        val jwtUtil = JwtUtil(
            secretKey = "test-secret-key-for-jwt-token-generation-minimum-256-bits",
            expirationMs = 3600000L
        )
        
        `when`("유효한 토큰에서 만료 시간을 추출하면") {
            val token = jwtUtil.generateToken(111L)
            val expirationDate = jwtUtil.getExpirationDateFromToken(token)
            
            then("현재 시간보다 미래 시점이어야 한다") {
                expirationDate.after(Date()) shouldBe true
            }
        }
        
        `when`("잘못된 토큰에서 사용자 ID를 추출하려 하면") {
            val invalidToken = "invalid.token"
            
            then("예외가 발생해야 한다") {
                try {
                    jwtUtil.getUserIdFromToken(invalidToken)
                    false shouldBe true // 이 라인에 도달하면 안됨
                } catch (e: Exception) {
                    e.message shouldContain "JWT"
                }
            }
        }
    }
})