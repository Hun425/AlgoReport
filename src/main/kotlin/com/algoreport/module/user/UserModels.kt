package com.algoreport.module.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * 사용자 엔티티
 */
@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    val nickname: String,

    val profileImageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: AuthProvider,

    val solvedacHandle: String? = null,
    val solvedacTier: Int? = null,
    val solvedacSolvedCount: Int? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
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
    val userId: UUID? = null,
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
    val userId: UUID,
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
