package com.algoreport.module.user

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.config.outbox.OutboxService
import com.algoreport.module.analysis.AnalysisProfileService
import com.algoreport.module.notification.EmailNotificationService
import com.algoreport.module.notification.NotificationSettingsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * USER_REGISTRATION_SAGA 구현
 *
 * 목표: Google OAuth2를 통한 신규 사용자 등록과 초기 프로필 설정
 */
@Service
class UserRegistrationSaga(
    private val userService: UserService,
    private val analysisProfileService: AnalysisProfileService,
    private val notificationSettingsService: NotificationSettingsService,
    private val emailNotificationService: EmailNotificationService,
    private val outboxService: OutboxService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(UserRegistrationSaga::class.java)
    }

    @Transactional
    fun start(request: UserRegistrationRequest): UserRegistrationResult {
        val sagaId = UUID.randomUUID()
        logger.info("Starting USER_REGISTRATION_SAGA - sagaId: {}, email: {}", sagaId, request.email)

        return try {
            // Step 1: 사용자 계정 생성
            val user = createUserAccount(request, sagaId)
            val userId = requireNotNull(user.id) { "Generated user ID must not be null" }

            // Step 2: 분석 프로필 초기화
            createAnalysisProfile(userId, sagaId)

            // Step 3: 알림 설정 초기화 및 환영 메시지
            setupNotificationSettings(userId, sagaId)

            logger.info("SAGA COMPLETED - sagaId: {}, userId: {}", sagaId, userId)
            UserRegistrationResult(
                sagaStatus = SagaStatus.COMPLETED,
                userId = userId
            )

        } catch (e: Exception) {
            // @Transactional에 의해 DB 변경사항은 자동으로 롤백됩니다.
            // 여기서는 실패 로깅 및 결과 반환만 처리합니다.
            logger.error("SAGA FAILED - sagaId: {}. Error: {}", sagaId, e.message, e)

            UserRegistrationResult(
                sagaStatus = SagaStatus.FAILED,
                userId = null,
                errorMessage = e.message
            )
        }
    }

    private fun createUserAccount(request: UserRegistrationRequest, sagaId: UUID): User {
        // TODO: 현재는 단순 문자열 체크. 실제 OAuth2 인증 코드(authCode)를 검증하는 로직 구현 필요.
        if (!request.authCode.startsWith("oauth2_")) {
            throw CustomException(Error.INVALID_OAUTH_CODE)
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
        val userId = requireNotNull(user.id) { "Generated user ID must not be null" }

        outboxService.publishEventWithSaga(
            aggregateType = "USER",
            aggregateId = userId.toString(),
            eventType = "USER_REGISTERED",
            eventData = mapOf(
                "userId" to userId.toString(),
                "email" to user.email,
                "nickname" to user.nickname,
                "provider" to user.provider.name
            ),
            sagaId = sagaId,
            sagaType = "USER_REGISTRATION_SAGA"
        )

        return user
    }

    private fun createAnalysisProfile(userId: UUID, sagaId: UUID) {
        try {
            analysisProfileService.createProfile(userId)

            // ANALYSIS_PROFILE_CREATED 이벤트 발행
            outboxService.publishEventWithSaga(
                aggregateType = "ANALYSIS_PROFILE",
                aggregateId = "analysis-profile-${UUID.randomUUID()}",
                eventType = "ANALYSIS_PROFILE_CREATED",
                eventData = mapOf(
                    "userId" to userId.toString(),
                    "profileId" to UUID.randomUUID().toString(),
                    "initializedAt" to java.time.LocalDateTime.now().toString()
                ),
                sagaId = sagaId,
                sagaType = "USER_REGISTRATION_SAGA"
            )
        } catch (e: Exception) {
            // 예외를 다시 던져서 SAGA의 롤백을 트리거합니다.
            throw RuntimeException("Failed to create analysis profile for user: $userId", e)
        }
    }

    private fun setupNotificationSettings(userId: UUID, sagaId: UUID) {
        try {
            // 알림 설정 초기화
            notificationSettingsService.createSettings(userId)

            // 환영 이메일 발송 (DB 트랜잭션과 분리되어야 함)
            // emailNotificationService.sendWelcomeEmail(userId)

            // WELCOME_NOTIFICATION_SENT 이벤트 발행
            outboxService.publishEventWithSaga(
                aggregateType = "NOTIFICATION",
                aggregateId = "notification-${UUID.randomUUID()}",
                eventType = "WELCOME_NOTIFICATION_SENT",
                eventData = mapOf(
                    "userId" to userId.toString(),
                    "notificationId" to UUID.randomUUID().toString(),
                    "channel" to "EMAIL",
                    "sentAt" to java.time.LocalDateTime.now().toString()
                ),
                sagaId = sagaId,
                sagaType = "USER_REGISTRATION_SAGA"
            )
        } catch (e: Exception) {
            // 예외를 다시 던져서 SAGA의 롤백을 트리거합니다.
            throw RuntimeException("Failed to setup notification settings for user: $userId", e)
        }
    }
}
