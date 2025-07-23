package com.algoreport.config.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * JWT 토큰을 검증하고 Spring Security Context에 인증 정보를 설정하는 필터
 */
@Component
class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil
) : OncePerRequestFilter() {
    
    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val token = getTokenFromRequest(request)
            
            if (token != null) {
                authenticateWithToken(token)
            }
        } catch (e: Exception) {
            // JWT 처리 중 오류 발생 시 로그 기록 후 계속 진행
            // SecurityContext는 비워둠으로써 인증되지 않은 상태로 유지
            logger.warn("JWT authentication failed for token: ${e.message}")
            if (logger.isDebugEnabled) {
                logger.debug("JWT authentication error details", e)
            }
        }
        
        filterChain.doFilter(request, response)
    }
    
    /**
     * JWT 토큰으로 인증 처리
     */
    private fun authenticateWithToken(token: String) {
        if (jwtUtil.validateToken(token)) {
            val userId = jwtUtil.getUserIdFromToken(token)
            
            // 간단한 인증 객체 생성 (실제로는 UserDetails를 사용할 수 있음)
            val authentication = UsernamePasswordAuthenticationToken(
                userId,
                null,
                emptyList() // authorities는 추후 구현
            )
            
            SecurityContextHolder.getContext().authentication = authentication
        }
    }
    
    /**
     * HTTP 요청에서 JWT 토큰 추출
     */
    private fun getTokenFromRequest(request: HttpServletRequest): String? {
        val authorizationHeader = request.getHeader(AUTHORIZATION_HEADER) ?: return null
        
        return if (authorizationHeader.startsWith(BEARER_PREFIX)) {
            val token = authorizationHeader.substring(BEARER_PREFIX.length).trim()
            if (token.isNotEmpty()) token else null
        } else {
            null
        }
    }
}