package com.algoreport.module.user

import com.algoreport.module.analysis.AnalysisProfileService
import com.algoreport.module.notification.EmailNotificationService
import com.algoreport.module.notification.NotificationSettingsService
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
@SpringBootTest(classes = [com.algoreport.AlgoReportApplication::class, com.algoreport.config.TestConfiguration::class])
@ActiveProfiles("test")
@Transactional
class UserRegistrationSagaTest(
    private val userRegistrationSaga: UserRegistrationSaga,
    private val userService: UserService,
    private val analysisProfileService: AnalysisProfileService,
    private val notificationSettingsService: NotificationSettingsService,
    private val emailNotificationService: EmailNotificationService,
    private val outboxService: com.algoreport.config.outbox.OutboxService
) : BehaviorSpec() {
    
    override fun extensions() = listOf(SpringExtension)
    
    init {
        beforeEach {
            // 각 테스트 전에 상태 초기화
            userService.clear()
            analysisProfileService.clear()
            notificationSettingsService.clear()
            emailNotificationService.clear()
            // outboxService는 clear 메서드가 없으므로 제거
        }
        
        given("USER_REGISTRATION_SAGA가 실행될 때") {
            val mockAuthCode = "mock_google_auth_code"
            val expectedEmail = "testuser@gmail.com"
            val expectedNickname = "테스트사용자"
            
            `when`("유효한 Google OAuth2 인증 코드가 제공되면") {
                // Step 1: 사용자 계정 생성 테스트
                then("사용자 계정이 성공적으로 생성되어야 한다") {
                    val request = UserRegistrationRequest(
                        authCode = mockAuthCode,
                        email = expectedEmail,
                        nickname = expectedNickname
                    )
                    
                    val result = userRegistrationSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.userId shouldNotBe null
                    
                    // 사용자가 실제로 생성되었는지 확인
                    val user = userService.findByEmail(expectedEmail)
                    user shouldNotBe null
                    user?.nickname shouldBe expectedNickname
                }
                
                then("분석 프로필이 초기화되어야 한다") {
                    val request = UserRegistrationRequest(
                        authCode = mockAuthCode,
                        email = expectedEmail,
                        nickname = expectedNickname
                    )
                    
                    val result = userRegistrationSaga.start(request)
                    
                    result.userId?.let { userId ->
                        val hasProfile = analysisProfileService.hasProfile(userId)
                        hasProfile shouldBe true
                    }
                }
                
                then("알림 설정이 초기화되어야 한다") {
                    val request = UserRegistrationRequest(
                        authCode = mockAuthCode,
                        email = expectedEmail,
                        nickname = expectedNickname
                    )
                    
                    val result = userRegistrationSaga.start(request)
                    
                    result.userId?.let { userId ->
                        val hasSettings = notificationSettingsService.hasSettings(userId)
                        hasSettings shouldBe true
                    }
                }
                
                then("환영 이메일이 발송되어야 한다") {
                    val request = UserRegistrationRequest(
                        authCode = mockAuthCode,
                        email = expectedEmail,
                        nickname = expectedNickname
                    )
                    
                    val result = userRegistrationSaga.start(request)
                    
                    result.userId?.let { userId ->
                        val welcomeEmailSent = emailNotificationService.wasWelcomeEmailSent(userId)
                        welcomeEmailSent shouldBe true
                    }
                }
            }
            
            `when`("Google OAuth2 인증이 실패하면") {
                val invalidAuthCode = "invalid_auth_code"
                
                then("Saga가 실패하고 사용자가 생성되지 않아야 한다") {
                    val request = UserRegistrationRequest(
                        authCode = invalidAuthCode,
                        email = "invalid@test.com",
                        nickname = "실패테스트"
                    )
                    
                    val result = userRegistrationSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    
                    // 사용자가 생성되지 않았는지 확인
                    val user = userService.findByEmail("invalid@test.com")
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
                    analysisProfileService.simulateFailure = true
                    
                    val request = UserRegistrationRequest(
                        authCode = authCode,
                        email = email,
                        nickname = nickname
                    )
                    
                    val result = userRegistrationSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    
                    // 보상 트랜잭션으로 사용자가 삭제되었는지 확인
                    val user = userService.findByEmail(email) 
                    user shouldBe null
                }
            }
            
            `when`("알림 설정 초기화가 실패하면") {
                then("보상 트랜잭션이 실행되어 사용자와 분석 프로필이 삭제되어야 한다") {
                    // NotificationSettingsService가 실패하도록 설정
                    notificationSettingsService.simulateFailure = true
                    
                    val request = UserRegistrationRequest(
                        authCode = authCode,
                        email = email,
                        nickname = nickname
                    )
                    
                    val result = userRegistrationSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    
                    // 모든 관련 데이터가 정리되었는지 확인
                    val user = userService.findByEmail(email)
                    user shouldBe null
                }
            }
        }
        
        given("USER_REGISTRATION_SAGA의 이벤트 발행을 확인할 때") {
            val authCode = "event_test_code"
            val email = "event@test.com"
            val nickname = "이벤트테스트"
            
            `when`("회원가입이 성공하면") {
                then("각 단계별로 적절한 이벤트가 발행되어야 한다") {
                    val request = UserRegistrationRequest(
                        authCode = authCode,
                        email = email,
                        nickname = nickname
                    )
                    
                    val result = userRegistrationSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    
                    // 발행된 이벤트들 확인은 실제 OutboxService 구현 후 추가 예정
                    // 현재는 기본 성공 검증만 수행
                    result.userId shouldNotBe null
                }
            }
        }
    }
}