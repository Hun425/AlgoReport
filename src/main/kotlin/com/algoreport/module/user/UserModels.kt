package com.algoreport.module.user

import java.time.LocalDateTime

/**
 * 사용자 엔티티
 */
data class User(
    val id: String,
    val email: String,
    val nickname: String,
    val profileImageUrl: String? = null,
    val provider: AuthProvider,
    val solvedacHandle: String? = null,
    val solvedacTier: Int? = null,
    val solvedacSolvedCount: Int? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 사용자 생성 요청
 */
data class UserCreateRequest(
    val email: String,
    val nickname: String,
    val provider: AuthProvider
)

/**
 * 사용자 등록 요청
 */
data class UserRegistrationRequest(
    val authCode: String,
    val email: String,
    val nickname: String,
    val provider: AuthProvider = AuthProvider.GOOGLE,
    val profileImageUrl: String? = null
)

/**
 * 사용자 등록 결과
 */
data class UserRegistrationResult(
    val sagaStatus: SagaStatus,
    val userId: String? = null,
    val errorMessage: String? = null
)

/**
 * OAuth2 인증 제공자
 */
enum class AuthProvider {
    GOOGLE,
    GITHUB,
    KAKAO
}

/**
 * SAGA 상태
 */
enum class SagaStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    PARTIAL_SUCCESS, // 일부 단계 실패했지만 핵심 기능은 성공
    FAILED,
    COMPENSATED
}

/**
 * solved.ac 연동 요청
 */
data class SolvedacLinkRequest(
    val userId: String,
    val solvedacHandle: String
)

/**
 * solved.ac 연동 결과
 */
data class SolvedacLinkResult(
    val sagaStatus: SagaStatus,
    val linkedHandle: String?,
    val errorMessage: String? = null
)