package com.algoreport.config.security

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.RedirectStrategy
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * OAuth2AuthenticationSuccessHandler 테스트
 *
 * 커버리지 향상을 위한 OAuth2 인증 성공 핸들러 테스트
 */
class OAuth2AuthenticationSuccessHandlerTest : BehaviorSpec({
    
    given("OAuth2AuthenticationSuccessHandler") {
        val mockJwtUtil = mockk<JwtUtil>()
        val handler = OAuth2AuthenticationSuccessHandler(mockJwtUtil)
        
        // redirectStrategy를 Mock으로 대체
        val mockRedirectStrategy = mockk<RedirectStrategy>(relaxed = true)
        handler.setRedirectStrategy(mockRedirectStrategy)
        
        `when`("OAuth2 인증이 성공했을 때") {
            `and`("유효한 사용자 정보가 제공되면") {
                val mockJwtUtil = mockk<JwtUtil>()
                val handler = OAuth2AuthenticationSuccessHandler(mockJwtUtil)
                val mockRedirectStrategy = mockk<RedirectStrategy>(relaxed = true)
                handler.setRedirectStrategy(mockRedirectStrategy)
                val email = "test@example.com"
                val userId = "user123"
                val accessToken = "access_token_123"
                val refreshToken = "refresh_token_456"
                
                val mockRequest = mockk<HttpServletRequest>()
                val mockResponse = mockk<HttpServletResponse>(relaxed = true)
                val mockAuthentication = mockk<Authentication>()
                val mockOAuth2User = mockk<OAuth2User>()
                
                every { mockAuthentication.principal } returns mockOAuth2User
                every { mockOAuth2User.attributes } returns mapOf(
                    "email" to email,
                    "userId" to userId
                )
                every { mockJwtUtil.generateAccessToken(userId, email) } returns accessToken
                every { mockJwtUtil.generateRefreshToken(userId) } returns refreshToken
                
                then("JWT 토큰을 생성하고 성공 URL로 리다이렉트해야 한다") {
                    handler.onAuthenticationSuccess(mockRequest, mockResponse, mockAuthentication)
                    
                    verify(exactly = 1) { mockJwtUtil.generateAccessToken(userId, email) }
                    verify(exactly = 1) { mockJwtUtil.generateRefreshToken(userId) }
                    
                    // 성공 URL로 리다이렉트 검증
                    val expectedUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/auth/callback")
                        .queryParam("token", URLEncoder.encode(accessToken, StandardCharsets.UTF_8))
                        .queryParam("refreshToken", URLEncoder.encode(refreshToken, StandardCharsets.UTF_8))
                        .build()
                        .toUriString()
                    
                    verify(exactly = 1) { mockRedirectStrategy.sendRedirect(mockRequest, mockResponse, expectedUrl) }
                }
            }
            
            `and`("이메일이 누락되면") {
                val mockJwtUtil = mockk<JwtUtil>()
                val handler = OAuth2AuthenticationSuccessHandler(mockJwtUtil)
                val mockRedirectStrategy = mockk<RedirectStrategy>(relaxed = true)
                handler.setRedirectStrategy(mockRedirectStrategy)
                val userId = "user123"
                
                val mockRequest = mockk<HttpServletRequest>()
                val mockResponse = mockk<HttpServletResponse>(relaxed = true)
                val mockAuthentication = mockk<Authentication>()
                val mockOAuth2User = mockk<OAuth2User>()
                
                every { mockAuthentication.principal } returns mockOAuth2User
                every { mockOAuth2User.attributes } returns mapOf(
                    "userId" to userId
                    // email이 누락됨
                )
                
                then("에러 페이지로 리다이렉트해야 한다") {
                    handler.onAuthenticationSuccess(mockRequest, mockResponse, mockAuthentication)
                    
                    // JWT 토큰 생성이 호출되지 않아야 함
                    verify(exactly = 0) { mockJwtUtil.generateAccessToken(any(), any()) }
                    verify(exactly = 0) { mockJwtUtil.generateRefreshToken(any()) }
                    
                    // 에러 리다이렉트 URL 검증 (redirectToError에서 response.sendRedirect 직접 호출)
                    val expectedErrorUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/auth/callback")
                        .queryParam("error", URLEncoder.encode("Missing user information", StandardCharsets.UTF_8))
                        .build()
                        .toUriString()
                    
                    verify(exactly = 1) { mockResponse.sendRedirect(expectedErrorUrl) }
                }
            }
            
            `and`("userId가 누락되면") {
                val mockJwtUtil = mockk<JwtUtil>()
                val handler = OAuth2AuthenticationSuccessHandler(mockJwtUtil)
                val mockRedirectStrategy = mockk<RedirectStrategy>(relaxed = true)
                handler.setRedirectStrategy(mockRedirectStrategy)
                val email = "test@example.com"
                
                val mockRequest = mockk<HttpServletRequest>()
                val mockResponse = mockk<HttpServletResponse>(relaxed = true)
                val mockAuthentication = mockk<Authentication>()
                val mockOAuth2User = mockk<OAuth2User>()
                
                every { mockAuthentication.principal } returns mockOAuth2User
                every { mockOAuth2User.attributes } returns mapOf(
                    "email" to email
                    // userId가 누락됨
                )
                
                then("에러 페이지로 리다이렉트해야 한다") {
                    handler.onAuthenticationSuccess(mockRequest, mockResponse, mockAuthentication)
                    
                    // JWT 토큰 생성이 호출되지 않아야 함
                    verify(exactly = 0) { mockJwtUtil.generateAccessToken(any(), any()) }
                    verify(exactly = 0) { mockJwtUtil.generateRefreshToken(any()) }
                    
                    // 에러 리다이렉트 URL 검증
                    val expectedErrorUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/auth/callback")
                        .queryParam("error", URLEncoder.encode("Missing user information", StandardCharsets.UTF_8))
                        .build()
                        .toUriString()
                    
                    verify(exactly = 1) { mockResponse.sendRedirect(expectedErrorUrl) }
                }
            }
            
            `and`("JWT 토큰 생성에 실패하면") {
                val mockJwtUtil = mockk<JwtUtil>()
                val handler = OAuth2AuthenticationSuccessHandler(mockJwtUtil)
                val mockRedirectStrategy = mockk<RedirectStrategy>(relaxed = true)
                handler.setRedirectStrategy(mockRedirectStrategy)
                val email = "test@example.com"
                val userId = "user123"
                
                val mockRequest = mockk<HttpServletRequest>()
                val mockResponse = mockk<HttpServletResponse>(relaxed = true)
                val mockAuthentication = mockk<Authentication>()
                val mockOAuth2User = mockk<OAuth2User>()
                
                every { mockAuthentication.principal } returns mockOAuth2User
                every { mockOAuth2User.attributes } returns mapOf(
                    "email" to email,
                    "userId" to userId
                )
                every { mockJwtUtil.generateAccessToken(userId, email) } throws RuntimeException("JWT generation failed")
                
                then("예외를 처리하고 에러 페이지로 리다이렉트해야 한다") {
                    handler.onAuthenticationSuccess(mockRequest, mockResponse, mockAuthentication)
                    
                    verify(exactly = 1) { mockJwtUtil.generateAccessToken(userId, email) }
                    
                    // 에러 리다이렉트 URL 검증
                    val expectedErrorUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/auth/callback")
                        .queryParam("error", URLEncoder.encode("Authentication processing failed", StandardCharsets.UTF_8))
                        .build()
                        .toUriString()
                    
                    verify(exactly = 1) { mockResponse.sendRedirect(expectedErrorUrl) }
                }
            }
            
            `and`("리다이렉트 중 예외가 발생하면") {
                val mockJwtUtil = mockk<JwtUtil>()
                val handler = OAuth2AuthenticationSuccessHandler(mockJwtUtil)
                val mockRedirectStrategy = mockk<RedirectStrategy>(relaxed = true)
                handler.setRedirectStrategy(mockRedirectStrategy)
                val email = "test@example.com"
                val userId = "user123"
                val accessToken = "access_token_123"
                val refreshToken = "refresh_token_456"
                
                val mockRequest = mockk<HttpServletRequest>()
                val mockResponse = mockk<HttpServletResponse>(relaxed = true)
                val mockAuthentication = mockk<Authentication>()
                val mockOAuth2User = mockk<OAuth2User>()
                
                every { mockAuthentication.principal } returns mockOAuth2User
                every { mockOAuth2User.attributes } returns mapOf(
                    "email" to email,
                    "userId" to userId
                )
                every { mockJwtUtil.generateAccessToken(userId, email) } returns accessToken
                every { mockJwtUtil.generateRefreshToken(userId) } returns refreshToken
                every { mockRedirectStrategy.sendRedirect(any(), any(), any()) } throws RuntimeException("Redirect failed")
                
                then("예외를 처리해야 한다") {
                    // 예외가 발생해도 핸들러는 정상적으로 완료되어야 함
                    handler.onAuthenticationSuccess(mockRequest, mockResponse, mockAuthentication)
                    
                    // redirectStrategy.sendRedirect가 호출되지만 예외로 인해 에러 처리로 넘어감
                    verify(exactly = 1) { mockRedirectStrategy.sendRedirect(any(), any(), any()) }
                    
                    // 에러 처리에서 response.sendRedirect 호출
                    val expectedErrorUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/auth/callback")
                        .queryParam("error", URLEncoder.encode("Authentication processing failed", StandardCharsets.UTF_8))
                        .build()
                        .toUriString()
                    
                    verify(exactly = 1) { mockResponse.sendRedirect(expectedErrorUrl) }
                }
            }
        }
        
        `when`("URL 인코딩을 테스트할 때") {
            `and`("특수 문자가 포함된 토큰이면") {
                val mockJwtUtil = mockk<JwtUtil>()
                val handler = OAuth2AuthenticationSuccessHandler(mockJwtUtil)
                val mockRedirectStrategy = mockk<RedirectStrategy>(relaxed = true)
                handler.setRedirectStrategy(mockRedirectStrategy)
                val email = "test+user@example.com"
                val userId = "user 123"
                val accessToken = "token with spaces & special chars"
                val refreshToken = "refresh+token=value"
                
                val mockRequest = mockk<HttpServletRequest>()
                val mockResponse = mockk<HttpServletResponse>(relaxed = true)
                val mockAuthentication = mockk<Authentication>()
                val mockOAuth2User = mockk<OAuth2User>()
                
                every { mockAuthentication.principal } returns mockOAuth2User
                every { mockOAuth2User.attributes } returns mapOf(
                    "email" to email,
                    "userId" to userId
                )
                every { mockJwtUtil.generateAccessToken(userId, email) } returns accessToken
                every { mockJwtUtil.generateRefreshToken(userId) } returns refreshToken
                
                then("토큰이 올바르게 URL 인코딩되어야 한다") {
                    handler.onAuthenticationSuccess(mockRequest, mockResponse, mockAuthentication)
                    
                    val expectedUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/auth/callback")
                        .queryParam("token", URLEncoder.encode(accessToken, StandardCharsets.UTF_8))
                        .queryParam("refreshToken", URLEncoder.encode(refreshToken, StandardCharsets.UTF_8))
                        .build()
                        .toUriString()
                    
                    // URL에 인코딩된 특수 문자가 포함되어야 함
                    expectedUrl shouldContain "token+with+spaces"
                    expectedUrl shouldContain "refresh%2Btoken%3Dvalue"
                    
                    verify(exactly = 1) { mockRedirectStrategy.sendRedirect(mockRequest, mockResponse, expectedUrl) }
                }
            }
        }
        
        `when`("에러 리다이렉트를 테스트할 때") {
            `and`("에러 메시지에 특수 문자가 포함되면") {
                val mockJwtUtil = mockk<JwtUtil>()
                val handler = OAuth2AuthenticationSuccessHandler(mockJwtUtil)
                val mockRedirectStrategy = mockk<RedirectStrategy>(relaxed = true)
                handler.setRedirectStrategy(mockRedirectStrategy)
                val mockRequest = mockk<HttpServletRequest>()
                val mockResponse = mockk<HttpServletResponse>(relaxed = true)
                val mockAuthentication = mockk<Authentication>()
                val mockOAuth2User = mockk<OAuth2User>()
                
                every { mockAuthentication.principal } returns mockOAuth2User
                every { mockOAuth2User.attributes } returns mapOf<String, Any>() // 빈 attributes
                
                then("에러 메시지가 올바르게 URL 인코딩되어야 한다") {
                    handler.onAuthenticationSuccess(mockRequest, mockResponse, mockAuthentication)
                    
                    val expectedErrorUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/auth/callback")
                        .queryParam("error", URLEncoder.encode("Missing user information", StandardCharsets.UTF_8))
                        .build()
                        .toUriString()
                    
                    // 에러 메시지가 URL 인코딩되어야 함
                    expectedErrorUrl shouldContain "Missing+user+information"
                    
                    verify(exactly = 1) { mockResponse.sendRedirect(expectedErrorUrl) }
                }
            }
        }
    }
})