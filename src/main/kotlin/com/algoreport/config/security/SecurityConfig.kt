package com.algoreport.config.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Spring Security 설정
 * OAuth2 + JWT 기반 인증/인가 시스템 구성
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val oauth2SuccessHandler: OAuth2AuthenticationSuccessHandler,
    private val oauth2FailureHandler: OAuth2AuthenticationFailureHandler
) {
    
    companion object {
        // 공개 엔드포인트 URL 패턴
        private val PUBLIC_ENDPOINTS = arrayOf(
            "/actuator/health",
            "/error",
            "/oauth2/**",
            "/login/**"
        )
        
        // OAuth2 설정 상수
        private const val OAUTH2_AUTHORIZATION_BASE_URI = "/oauth2/authorization"
        private const val OAUTH2_REDIRECTION_BASE_URI = "/oauth2/callback/*"
        
        // API 엔드포인트 패턴
        private const val API_ENDPOINTS = "/api/**"
    }
    
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            // CSRF 비활성화 (JWT 사용으로 인해)
            .csrf { it.disable() }
            
            // 세션 정책: STATELESS (JWT 기반)
            .sessionManagement { 
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) 
            }
            
            // URL별 인증/인가 설정
            .authorizeHttpRequests { auth ->
                auth
                    // 공개 엔드포인트
                    .requestMatchers(*PUBLIC_ENDPOINTS).permitAll()
                    
                    // API 엔드포인트는 인증 필요
                    .requestMatchers(API_ENDPOINTS).authenticated()
                    
                    // 나머지 요청은 모두 허용 (개발 단계)
                    .anyRequest().permitAll()
            }
            
            // OAuth2 로그인 설정
            .oauth2Login { oauth2 ->
                oauth2
                    .authorizationEndpoint { 
                        it.baseUri(OAUTH2_AUTHORIZATION_BASE_URI) 
                    }
                    .redirectionEndpoint { 
                        it.baseUri(OAUTH2_REDIRECTION_BASE_URI) 
                    }
                    .successHandler(oauth2SuccessHandler)
                    .failureHandler(oauth2FailureHandler)
            }
            
            // JWT 인증 필터 추가
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            
            .build()
    }
}