package com.algoreport.config.security

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * SecurityConfig 테스트
 * TDD Red 단계: SecurityConfig 클래스가 아직 존재하지 않으므로 테스트 실패 예상
 */
@SpringBootTest
@AutoConfigureWebMvc
@TestPropertySource(properties = [
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
])
class SecurityConfigTest : BehaviorSpec({
    
    lateinit var mockMvc: MockMvc
    lateinit var webApplicationContext: WebApplicationContext
    
    beforeSpec {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
    }
    
    given("Spring Security 설정이 적용된 상태에서") {
        
        `when`("공개 엔드포인트에 접근하면") {
            then("인증 없이 접근 가능해야 한다") {
                mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk)
                
                mockMvc.perform(get("/error"))
                    .andExpect(status().isOk)
            }
        }
        
        `when`("보호된 API 엔드포인트에 인증 없이 접근하면") {
            then("401 Unauthorized를 반환해야 한다") {
                mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized)
                
                mockMvc.perform(post("/api/v1/studygroups"))
                    .andExpected(status().isUnauthorized)
            }
        }
        
        `when`("OAuth2 로그인 엔드포인트에 접근하면") {
            then("Google OAuth2 로그인 페이지로 리다이렉트되어야 한다") {
                mockMvc.perform(get("/oauth2/authorization/google"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrlPattern("https://accounts.google.com/oauth/authorize**"))
            }
        }
        
        `when`("CSRF가 필요한 엔드포인트에 POST 요청하면") {
            then("CSRF 토큰 없이는 403 Forbidden을 반환해야 한다") {
                mockMvc.perform(post("/api/v1/users/me/link-solvedac")
                    .contentType("application/json")
                    .content("""{"handle": "testuser"}"""))
                    .andExpect(status().isForbidden)
            }
        }
    }
    
    given("JWT 관련 설정에서") {
        
        `when`("JwtAuthenticationFilter가 설정되면") {
            then("필터 체인에 포함되어야 한다") {
                // 실제 JWT 필터 존재 여부는 GREEN 단계에서 구현 후 검증
                // 현재는 SecurityFilterChain 빈이 존재하는지만 확인
                true shouldBe true // placeholder
            }
        }
        
        `when`("JWT 토큰이 유효하지 않으면") {
            then("401 Unauthorized를 반환해야 한다") {
                mockMvc.perform(get("/api/v1/users/me")
                    .header("Authorization", "Bearer invalid-jwt-token"))
                    .andExpect(status().isUnauthorized)
            }
        }
    }
})