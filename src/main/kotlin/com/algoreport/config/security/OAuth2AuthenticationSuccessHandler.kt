package com.algoreport.config.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * OAuth2 로그인 성공 시 처리 핸들러
 * JWT 토큰 생성 및 클라이언트 리다이렉트 처리
 */
@Component
class OAuth2AuthenticationSuccessHandler(
    private val jwtUtil: JwtUtil
) : SimpleUrlAuthenticationSuccessHandler() {
    
    companion object {
        private val logger = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler::class.java)
        private const val FRONTEND_REDIRECT_URL = "http://localhost:3000/auth/callback"
    }
    
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        try {
            val oauth2User = authentication.principal as OAuth2User
            val email = oauth2User.attributes["email"] as? String
            val userId = oauth2User.attributes["userId"] as? String
            
            logger.info("OAuth2 authentication successful for user: $email")
            
            if (email == null || userId == null) {
                logger.error("Required user information missing from OAuth2 authentication")
                redirectToError(response, "Missing user information")
                return
            }
            
            // JWT 토큰 생성
            val accessToken = jwtUtil.generateAccessToken(userId, email)
            val refreshToken = jwtUtil.generateRefreshToken(userId)
            
            logger.info("Generated JWT tokens for user: $email")
            
            // 프론트엔드로 리다이렉트 (토큰을 쿼리 파라미터로 전달)
            val redirectUrl = UriComponentsBuilder.fromUriString(FRONTEND_REDIRECT_URL)
                .queryParam("token", URLEncoder.encode(accessToken, StandardCharsets.UTF_8))
                .queryParam("refreshToken", URLEncoder.encode(refreshToken, StandardCharsets.UTF_8))
                .build()
                .toUriString()
            
            redirectStrategy.sendRedirect(request, response, redirectUrl)
            
        } catch (ex: Exception) {
            logger.error("Failed to process OAuth2 authentication success", ex)
            redirectToError(response, "Authentication processing failed")
        }
    }
    
    private fun redirectToError(response: HttpServletResponse, error: String) {
        val errorUrl = UriComponentsBuilder.fromUriString("$FRONTEND_REDIRECT_URL")
            .queryParam("error", URLEncoder.encode(error, StandardCharsets.UTF_8))
            .build()
            .toUriString()
        
        try {
            response.sendRedirect(errorUrl)
        } catch (ex: Exception) {
            logger.error("Failed to redirect to error page", ex)
        }
    }
}