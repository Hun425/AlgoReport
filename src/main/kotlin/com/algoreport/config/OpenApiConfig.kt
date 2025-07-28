package com.algoreport.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.servers.Server
import org.springframework.context.annotation.Configuration

/**
 * SpringDoc OpenAPI 3 설정
 * 
 * API 문서화를 위한 Swagger 설정을 담당합니다.
 * - JWT 인증 방식 문서화
 * - OAuth2 플로우 문서화  
 * - 모듈별 API 그룹화
 */
@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "AlgoReport API",
        version = "1.0.0",
        description = """
            ## 📋 알고리포트 (Algo-Report) API 문서
            
            **solved.ac** 사용자 및 스터디 그룹의 문제 해결 이력을 분석하여 학습 패턴 시각화, 강점/약점 분석, 맞춤 문제 추천 및 스터디 자동 관리를 제공하는 플랫폼입니다.
            
            ### 🔐 인증 방식
            - **Google OAuth2**: 사용자 로그인 및 회원가입
            - **JWT Bearer Token**: API 호출 시 인증
            
            ### 📡 주요 모듈
            - **User Module**: 사용자 관리 및 인증
            - **Study Group Module**: 스터디 그룹 관리
            - **Analysis Module**: 데이터 분석 및 추천
            - **Notification Module**: 알림 시스템
            
            ### 🚀 시작하기
            1. Google OAuth2 로그인: `/oauth2/authorization/google`
            2. JWT 토큰 획득 후 API 호출
            3. Bearer 토큰을 Authorization 헤더에 포함
        """.trimIndent(),
        contact = Contact(
            name = "채기훈",
            email = "dev@algoreport.com"
        )
    ),
    servers = [
        Server(url = "http://localhost:8080", description = "Local Development Server"),
        Server(url = "https://api.algoreport.com", description = "Production Server")
    ]
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT 토큰을 사용한 인증. Google OAuth2 로그인 후 발급받은 JWT 토큰을 입력하세요."
)
class OpenApiConfig