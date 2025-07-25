package com.algoreport.module.user

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * USER_REGISTRATION_SAGA 테스트
 * TDD Red 단계: USER_REGISTRATION_SAGA 관련 클래스들이 존재하지 않으므로 컴파일 실패 예상
 * 
 * 비즈니스 요구사항:
 * - Google OAuth2로 인증된 사용자만 가입 가능
 * - 가입 즉시 분석 프로필과 알림 설정 초기화
 * - 가입 완료 시 환영 이메일 발송
 * - 모든 단계가 성공해야 가입 완료로 처리
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRegistrationSagaTest : BehaviorSpec() {
    
    override fun extensions() = listOf(SpringExtension)
    
    init {
        given("USER_REGISTRATION_SAGA가 실행될 때") {
            val mockAuthCode = "mock_google_auth_code"
            val expectedEmail = "testuser@gmail.com"
            val expectedNickname = "테스트사용자"
            
            `when`("유효한 Google OAuth2 인증 코드가 제공되면") {
                // Step 1: 사용자 계정 생성 테스트
                then("사용자 계정이 성공적으로 생성되어야 한다") {
                    // UserRegistrationSaga가 존재하지 않으므로 컴파일 실패 예상
                    val saga = UserRegistrationSaga()
                    val request = UserRegistrationRequest(
                        authCode = mockAuthCode,
                        email = expectedEmail,
                        nickname = expectedNickname
                    )
                    
                    val result = saga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.userId shouldNotBe null
                }
                
                then("분석 프로필이 초기화되어야 한다") {
                    // AnalysisProfileService가 존재하지 않으므로 컴파일 실패 예상
                    val analysisService = AnalysisProfileService()
                    val userId = "test-user-id"
                    
                    val hasProfile = analysisService.hasProfile(userId)
                    hasProfile shouldBe true
                }
                
                then("알림 설정이 초기화되어야 한다") {
                    // NotificationSettingsService가 존재하지 않으므로 컴파일 실패 예상
                    val notificationService = NotificationSettingsService()
                    val userId = "test-user-id"
                    
                    val hasSettings = notificationService.hasSettings(userId)
                    hasSettings shouldBe true
                }
                
                then("환영 이메일이 발송되어야 한다") {
                    // EmailNotificationService가 존재하지 않으므로 컴파일 실패 예상
                    val emailService = EmailNotificationService()
                    val userId = "test-user-id"
                    
                    val welcomeEmailSent = emailService.wasWelcomeEmailSent(userId)
                    welcomeEmailSent shouldBe true
                }
            }
            
            `when`("Google OAuth2 인증이 실패하면") {
                val invalidAuthCode = "invalid_auth_code"
                
                then("Saga가 실패하고 사용자가 생성되지 않아야 한다") {
                    val saga = UserRegistrationSaga()
                    val request = UserRegistrationRequest(
                        authCode = invalidAuthCode,
                        email = "invalid@test.com",
                        nickname = "실패테스트"
                    )
                    
                    val result = saga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    
                    // 사용자가 생성되지 않았는지 확인
                    val userRepository = UserRepository()
                    val user = userRepository.findByEmail("invalid@test.com")
                    user shouldBe null
                }
            }
        }
        
        given("USER_REGISTRATION_SAGA에서 중간 단계가 실패할 때") {
            val authCode = "valid_auth_code"
            val email = "test@example.com"
            val nickname = "보상테스트"
            
            `when`("분석 프로필 생성이 실패하면") {
                then("보상 트랜잭션이 실행되어 사용자가 삭제되어야 한다") {
                    // 분석 프로필 생성 실패 시뮬레이션
                    val saga = UserRegistrationSaga()
                    val request = UserRegistrationRequest(
                        authCode = authCode,
                        email = email,
                        nickname = nickname
                    )
                    
                    // AnalysisProfileService가 실패하도록 설정 (Mock)
                    val analysisService = AnalysisProfileService()
                    analysisService.simulateFailure = true
                    
                    val result = saga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPENSATED
                    
                    // 보상 트랜잭션으로 사용자가 삭제되었는지 확인
                    val userRepository = UserRepository()
                    val user = userRepository.findByEmail(email)
                    user shouldBe null
                }
            }
            
            `when`("알림 설정 초기화가 실패하면") {
                then("보상 트랜잭션이 실행되어 사용자와 분석 프로필이 삭제되어야 한다") {
                    val saga = UserRegistrationSaga()
                    val request = UserRegistrationRequest(
                        authCode = authCode,
                        email = email,
                        nickname = nickname
                    )
                    
                    // NotificationSettingsService가 실패하도록 설정
                    val notificationService = NotificationSettingsService()
                    notificationService.simulateFailure = true
                    
                    val result = saga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPENSATED
                    
                    // 모든 관련 데이터가 정리되었는지 확인
                    val userRepository = UserRepository()
                    val analysisService = AnalysisProfileService()
                    
                    val user = userRepository.findByEmail(email)
                    val hasProfile = analysisService.hasProfile("non-existent-user")
                    
                    user shouldBe null
                    hasProfile shouldBe false
                }
            }
        }
        
        given("USER_REGISTRATION_SAGA의 이벤트 발행을 확인할 때") {
            val authCode = "event_test_code"
            val email = "event@test.com"
            val nickname = "이벤트테스트"
            
            `when`("회원가입이 성공하면") {
                then("각 단계별로 적절한 이벤트가 발행되어야 한다") {
                    val saga = UserRegistrationSaga()
                    val request = UserRegistrationRequest(
                        authCode = authCode,
                        email = email,
                        nickname = nickname
                    )
                    
                    val result = saga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    
                    // 발행된 이벤트들 확인
                    val eventPublisher = OutboxEventPublisher()
                    val publishedEvents = eventPublisher.getPublishedEvents()
                    
                    // USER_REGISTERED 이벤트 확인
                    val userRegisteredEvent = publishedEvents.find { it.eventType == "USER_REGISTERED" }
                    userRegisteredEvent shouldNotBe null
                    userRegisteredEvent?.aggregateId shouldBe "user-${result.userId}"
                    
                    // ANALYSIS_PROFILE_CREATED 이벤트 확인
                    val profileCreatedEvent = publishedEvents.find { it.eventType == "ANALYSIS_PROFILE_CREATED" }
                    profileCreatedEvent shouldNotBe null
                    
                    // WELCOME_NOTIFICATION_SENT 이벤트 확인
                    val welcomeNotificationEvent = publishedEvents.find { it.eventType == "WELCOME_NOTIFICATION_SENT" }
                    welcomeNotificationEvent shouldNotBe null
                }
            }
        }
    }
}

// TDD Red 단계에서 컴파일 실패를 위한 필요 클래스들 (아직 구현되지 않음)

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

class UserRegistrationSaga {
    fun start(request: UserRegistrationRequest): UserRegistrationResult {
        // 아직 구현되지 않음 - Green 단계에서 구현 예정
        throw NotImplementedError("USER_REGISTRATION_SAGA not implemented yet")
    }
}

class AnalysisProfileService {
    var simulateFailure = false
    
    fun hasProfile(userId: String): Boolean {
        throw NotImplementedError("AnalysisProfileService not implemented yet")
    }
}

class NotificationSettingsService {
    var simulateFailure = false
    
    fun hasSettings(userId: String): Boolean {
        throw NotImplementedError("NotificationSettingsService not implemented yet")
    }
}

class EmailNotificationService {
    fun wasWelcomeEmailSent(userId: String): Boolean {
        throw NotImplementedError("EmailNotificationService not implemented yet")
    }
}

class UserRepository {
    fun findByEmail(email: String): User? {
        throw NotImplementedError("UserRepository not implemented yet")
    }
}

class OutboxEventPublisher {
    fun getPublishedEvents(): List<OutboxEvent> {
        throw NotImplementedError("OutboxEventPublisher not implemented yet")
    }
}

data class User(
    val id: String,
    val email: String,
    val nickname: String
)

data class OutboxEvent(
    val eventType: String,
    val aggregateId: String,
    val eventData: String
)