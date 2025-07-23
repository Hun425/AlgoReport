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
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {
    
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
                    .requestMatchers(
                        "/actuator/health",
                        "/error",
                        "/oauth2/**",
                        "/login/**"
                    ).permitAll()
                    
                    // API 엔드포인트는 인증 필요
                    .requestMatchers("/api/**").authenticated()
                    
                    // 나머지 요청은 모두 허용 (개발 단계)
                    .anyRequest().permitAll()
            }
            
            // OAuth2 로그인 설정
            .oauth2Login { oauth2 ->
                oauth2
                    .authorizationEndpoint { 
                        it.baseUri("/oauth2/authorization") 
                    }
                    .redirectionEndpoint { 
                        it.baseUri("/oauth2/callback/*") 
                    }
                    .successHandler(OAuth2AuthenticationSuccessHandler())
                    .failureHandler(OAuth2AuthenticationFailureHandler())
            }
            
            // JWT 인증 필터 추가
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            
            .build()
    }
}