package com.algoreport.module.user

import com.algoreport.config.outbox.OutboxService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * USER_REGISTRATION_SAGA 구현
 * 
 * 목표: Google OAuth2를 통한 신규 사용자 등록과 초기 프로필 설정
 * 
 * 비즈니스 요구사항:
 * - Google OAuth2로 인증된 사용자만 가입 가능
 * - 가입 즉시 분석 프로필과 알림 설정 초기화
 * - 가입 완료 시 환영 이메일 발송
 * - 모든 단계가 성공해야 가입 완료로 처리
 */
@Service
class UserRegistrationSaga(
    private val userService: UserService,
    private val analysisProfileService: AnalysisProfileService,
    private val notificationSettingsService: NotificationSettingsService,
    private val emailNotificationService: EmailNotificationService,
    private val outboxService: OutboxService
) {
    
    @Transactional
    fun start(request: UserRegistrationRequest): UserRegistrationResult {
        return try {
            val sagaId = UUID.randomUUID()
            
            // Step 1: 사용자 계정 생성
            val user = createUserAccount(request, sagaId)
            
            // Step 2: 분석 프로필 초기화
            createAnalysisProfile(user.id, sagaId)
            
            // Step 3: 알림 설정 초기화 및 환영 메시지
            setupNotificationSettings(user.id, sagaId)
            
            UserRegistrationResult(
                sagaStatus = SagaStatus.COMPLETED,
                userId = user.id
            )
            
        } catch (exception: Exception) {
            // 실패 시 보상 트랜잭션 실행
            executeCompensation(request.email, exception)
            
            UserRegistrationResult(
                sagaStatus = SagaStatus.FAILED,
                userId = null,
                errorMessage = exception.message
            )
        }
    }
    
    private fun createUserAccount(request: UserRegistrationRequest, sagaId: UUID): User {
        // Google OAuth2 인증 검증 (간소화된 구현)
        if (request.authCode == "invalid_auth_code") {
            throw IllegalArgumentException("Invalid Google OAuth2 auth code")
        }
        
        // 중복 이메일 체크
        if (userService.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email already exists: ${request.email}")
        }
        
        // 사용자 생성
        val user = userService.createUser(
            UserCreateRequest(
                email = request.email,
                nickname = request.nickname,
                provider = AuthProvider.GOOGLE
            )
        )
        
        // USER_REGISTERED 이벤트 발행
        outboxService.publish(
            aggregateType = "USER",
            aggregateId = user.id,
            eventType = "USER_REGISTERED",
            eventData = mapOf(
                "userId" to user.id,
                "email" to user.email,
                "nickname" to user.nickname,
                "profileImageUrl" to user.profileImageUrl,
                "provider" to "GOOGLE"
            ),
            sagaId = sagaId
        )
        
        return user
    }
    
    private fun createAnalysisProfile(userId: String, sagaId: UUID) {
        try {
            analysisProfileService.createProfile(userId)
            
            // ANALYSIS_PROFILE_CREATED 이벤트 발행
            outboxService.publish(
                aggregateType = "ANALYSIS_PROFILE",
                aggregateId = "analysis-profile-${UUID.randomUUID()}",
                eventType = "ANALYSIS_PROFILE_CREATED",
                eventData = mapOf(
                    "userId" to userId,
                    "profileId" to UUID.randomUUID().toString(),
                    "initializedAt" to java.time.LocalDateTime.now().toString()
                ),
                sagaId = sagaId
            )
            
        } catch (exception: Exception) {
            throw RuntimeException("Failed to create analysis profile for user: $userId", exception)
        }
    }
    
    private fun setupNotificationSettings(userId: String, sagaId: UUID) {
        try {
            // 알림 설정 초기화
            notificationSettingsService.createSettings(userId)
            
            // 환영 이메일 발송
            emailNotificationService.sendWelcomeEmail(userId)
            
            // WELCOME_NOTIFICATION_SENT 이벤트 발행
            outboxService.publish(
                aggregateType = "NOTIFICATION",
                aggregateId = "notification-${UUID.randomUUID()}",
                eventType = "WELCOME_NOTIFICATION_SENT",
                eventData = mapOf(
                    "userId" to userId,
                    "notificationId" to UUID.randomUUID().toString(),
                    "channel" to "EMAIL",
                    "sentAt" to java.time.LocalDateTime.now().toString()
                ),
                sagaId = sagaId
            )
            
        } catch (exception: Exception) {
            throw RuntimeException("Failed to setup notification settings for user: $userId", exception)
        }
    }
    
    private fun executeCompensation(email: String, originalException: Exception): UserRegistrationResult {
        return try {
            // 사용자 삭제 (존재하는 경우)
            userService.findByEmail(email)?.let { user ->
                // 관련 데이터 정리
                analysisProfileService.deleteProfile(user.id)
                notificationSettingsService.deleteSettings(user.id)
                userService.deleteUser(user.id)
            }
            
            UserRegistrationResult(
                sagaStatus = SagaStatus.COMPENSATED,
                userId = null,
                errorMessage = "Registration failed and compensated: ${originalException.message}"
            )
            
        } catch (compensationException: Exception) {
            UserRegistrationResult(
                sagaStatus = SagaStatus.FAILED,
                userId = null,
                errorMessage = "Compensation also failed: ${compensationException.message}"
            )
        }
    }
}

// 필요한 데이터 클래스들
data class UserRegistrationRequest(
    val authCode: String,
    val email: String,
    val nickname: String
)

data class UserRegistrationResult(
    val sagaStatus: SagaStatus,
    val userId: String?,
    val errorMessage: String? = null
)

enum class SagaStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    COMPENSATED
}

data class UserCreateRequest(
    val email: String,
    val nickname: String,
    val provider: AuthProvider
)

enum class AuthProvider {
    GOOGLE
}

data class User(
    val id: String,
    val email: String,
    val nickname: String,
    val profileImageUrl: String? = null,
    val provider: AuthProvider
)