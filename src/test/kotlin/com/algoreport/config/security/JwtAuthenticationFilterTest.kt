package com.algoreport.config.security

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import jakarta.servlet.FilterChain

/**
 * JwtAuthenticationFilter 테스트
 * TDD Red 단계: 아직 JwtAuthenticationFilter 클래스가 존재하지 않으므로 테스트 실패 예상
 */
class JwtAuthenticationFilterTest : BehaviorSpec({
    
    given("JwtAuthenticationFilter가 HTTP 요청을 처리할 때") {
        
        val jwtUtil = mockk<JwtUtil>()
        val filter = JwtAuthenticationFilter(jwtUtil)
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)
        
        `when`("Authorization 헤더가 없으면") {
            then("인증 없이 다음 필터로 진행해야 한다") {
                filter.doFilterInternal(request, response, filterChain)
                
                SecurityContextHolder.getContext().authentication shouldBe null
            }
        }
        
        `when`("Bearer 토큰이 아닌 Authorization 헤더가 있으면") {
            request.addHeader("Authorization", "Basic dGVzdDp0ZXN0")
            
            then("인증 없이 다음 필터로 진행해야 한다") {
                filter.doFilterInternal(request, response, filterChain)
                
                SecurityContextHolder.getContext().authentication shouldBe null
            }
        }
        
        `when`("유효한 JWT 토큰이 있으면") {
            val validToken = "valid-jwt-token"
            val userId = 1L
            request.addHeader("Authorization", "Bearer $validToken")
            
            every { jwtUtil.validateToken(validToken) } returns true
            every { jwtUtil.getUserIdFromToken(validToken) } returns userId
            
            then("인증 정보가 SecurityContext에 설정되어야 한다") {
                filter.doFilterInternal(request, response, filterChain)
                
                val authentication = SecurityContextHolder.getContext().authentication
                authentication shouldNotBe null
                authentication.principal shouldBe userId
                authentication.isAuthenticated shouldBe true
            }
        }
        
        `when`("유효하지 않은 JWT 토큰이 있으면") {
            val invalidToken = "invalid-jwt-token"
            request.addHeader("Authorization", "Bearer $invalidToken")
            
            every { jwtUtil.validateToken(invalidToken) } returns false
            
            then("인증 정보가 설정되지 않아야 한다") {
                filter.doFilterInternal(request, response, filterChain)
                
                SecurityContextHolder.getContext().authentication shouldBe null
            }
        }
    }
    
    afterEach {
        SecurityContextHolder.clearContext()
    }
})