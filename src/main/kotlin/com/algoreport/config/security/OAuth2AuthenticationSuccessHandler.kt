package com.algoreport.config.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

/**
 * OAuth2 로그인 성공 시 처리 핸들러
 * JWT 토큰 생성 및 클라이언트 리다이렉트 처리
 */
@Component
class OAuth2AuthenticationSuccessHandler : SimpleUrlAuthenticationSuccessHandler() {
    
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        // TODO: Phase 2에서 실제 사용자 처리 로직 구현
        // 1. OAuth2User에서 사용자 정보 추출
        // 2. 우리 DB에 사용자 등록/조회
        // 3. JWT 토큰 생성
        // 4. 프론트엔드로 리다이렉트 (토큰 포함)
        
        // 현재는 기본 성공 처리만 수행
        super.onAuthenticationSuccess(request, response, authentication)
    }
}