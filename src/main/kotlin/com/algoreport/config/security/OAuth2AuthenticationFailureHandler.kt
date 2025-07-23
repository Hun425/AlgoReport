package com.algoreport.config.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component

/**
 * OAuth2 로그인 실패 시 처리 핸들러
 */
@Component
class OAuth2AuthenticationFailureHandler : SimpleUrlAuthenticationFailureHandler() {
    
    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException
    ) {
        // TODO: Phase 2에서 실제 오류 처리 로직 구현
        // 1. 로그 기록
        // 2. 에러 페이지로 리다이렉트
        // 3. 클라이언트에 적절한 오류 메시지 전달
        
        // 현재는 기본 실패 처리만 수행
        super.onAuthenticationFailure(request, response, exception)
    }
}