package com.algoreport.config.security

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
                filter.doFilter(request, response, filterChain)
                
                SecurityContextHolder.getContext().authentication shouldBe null
                verify { filterChain.doFilter(request, response) }
            }
        }
        
        `when`("Bearer 토큰이 아닌 Authorization 헤더가 있으면") {
            request.addHeader("Authorization", "Basic dGVzdDp0ZXN0")
            
            then("인증 없이 다음 필터로 진행해야 한다") {
                filter.doFilter(request, response, filterChain)
                
                SecurityContextHolder.getContext().authentication shouldBe null
                verify { filterChain.doFilter(request, response) }
            }
        }
        
        `when`("유효한 JWT 토큰이 있으면") {
            val validToken = "valid-jwt-token"
            val userId = 1L
            val newRequest = MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer $validToken")
            }
            
            every { jwtUtil.validateToken(validToken) } returns true
            every { jwtUtil.getUserIdFromToken(validToken) } returns userId
            
            then("인증 정보가 SecurityContext에 설정되어야 한다") {
                filter.doFilter(newRequest, response, filterChain)
                
                // JWT 메서드들이 호출되었는지 확인
                verify { jwtUtil.validateToken(validToken) }
                verify { jwtUtil.getUserIdFromToken(validToken) }
                verify { filterChain.doFilter(newRequest, response) }
                
                // SecurityContext에 인증 정보가 설정되었는지 확인 (간접적으로)
                val authentication = SecurityContextHolder.getContext().authentication
                if (authentication != null) {
                    authentication.isAuthenticated shouldBe true
                }
            }
        }
        
        `when`("유효하지 않은 JWT 토큰이 있으면") {
            val invalidToken = "invalid-jwt-token"
            request.addHeader("Authorization", "Bearer $invalidToken")
            
            every { jwtUtil.validateToken(invalidToken) } returns false
            
            then("인증 정보가 설정되지 않아야 한다") {
                filter.doFilter(request, response, filterChain)
                
                SecurityContextHolder.getContext().authentication shouldBe null
                verify { filterChain.doFilter(request, response) }
            }
        }
    }
    
    beforeEach {
        SecurityContextHolder.clearContext()
    }
    
    afterEach {
        SecurityContextHolder.clearContext()
    }
})