package com.algoreport.module.user

import com.algoreport.module.analysis.AnalysisProfileRepository
import com.algoreport.module.notification.NotificationSettingsRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * USER_REGISTRATION_SAGA 통합 테스트
 * - TDD Refactor 단계: 실제 DB와 연동하여 테스트하도록 리팩토링
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional // 각 테스트 후 DB 롤백을 통해 테스트 격리성 보장
class UserRegistrationSagaTest(
    private val userRegistrationSaga: UserRegistrationSaga,
    // DB 상태를 직접 검증하기 위해 Repository를 주입받습니다.
    private val userRepository: UserRepository,
    private val analysisProfileRepository: AnalysisProfileRepository,
    private val notificationSettingsRepository: NotificationSettingsRepository
) : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        // `beforeEach` 블록은 @Transactional에 의해 더 이상 필요하지 않습니다.

        given("USER_REGISTRATION_SAGA가 실행될 때") {
            val mockAuthCode = "oauth2_mock_google_auth_code"
            val expectedEmail = "testuser@gmail.com"
            val expectedNickname = "테스트사용자"

            `when`("모든 서비스가 정상적으로 동작하면 (Happy Path)") {
                val request = UserRegistrationRequest(
                    authCode = mockAuthCode,
                    email = expectedEmail,
                    nickname = expectedNickname
                )

                // SAGA 실행
                val result = userRegistrationSaga.start(request)

                then("SAGA는 완료(COMPLETED) 상태여야 한다") {
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.userId shouldNotBe null
                }

                then("사용자 계정이 DB에 실제로 생성되어야 한다") {
                    val user = userRepository.findByEmail(expectedEmail)
                    user shouldNotBe null
                    user?.id shouldBe result.userId
                    user?.nickname shouldBe expectedNickname
                }

                then("분석 프로필이 DB에 실제로 생성되어야 한다") {
                    val userId = result.userId!!
                    val hasProfile = analysisProfileRepository.existsByUserId(userId)
                    hasProfile shouldBe true
                }

                then("알림 설정이 DB에 실제로 생성되어야 한다") {
                    val userId = result.userId!!
                    val hasSettings = notificationSettingsRepository.existsByUserId(userId)
                    hasSettings shouldBe true
                }
            }

            `when`("유효하지 않은 인증 코드가 제공되면") {
                val invalidAuthCode = "invalid_auth_code"
                val request = UserRegistrationRequest(
                    authCode = invalidAuthCode,
                    email = "invalid@test.com",
                    nickname = "실패테스트"
                )

                val result = userRegistrationSaga.start(request)

                then("SAGA는 실패(FAILED)하고 사용자는 생성되지 않아야 한다") {
                    result.sagaStatus shouldBe SagaStatus.FAILED

                    // 사용자가 DB에 생성되지 않았는지 확인
                    val user = userRepository.findByEmail("invalid@test.com")
                    user shouldBe null
                }
            }
        }

        // TODO: 아래 "중간 단계 실패" 테스트 케이스들은 MockK와 @MockkBean을 사용하여
        //   특정 서비스(예: AnalysisProfileService)에 의도적으로 예외를 발생시키도록 리팩토링해야 합니다.
        //   현재는 Happy Path와 명백한 입력 오류에 대한 테스트만 검증합니다.
        /*
        given("USER_REGISTRATION_SAGA에서 중간 단계가 실패할 때") {
            `when`("분석 프로필 생성이 실패하면") {
                then("전체 트랜잭션이 롤백되어 아무 데이터도 생성되지 않아야 한다") {
                    // 1. @MockkBean으로 AnalysisProfileService를 Mocking
                    // 2. every { analysisProfileService.createProfile(any()) } throws Exception() 설정
                    // 3. userRegistrationSaga.start() 실행
                    // 4. 결과가 FAILED인지 확인
                    // 5. userRepository.findByEmail()로 사용자가 null인지 확인 (롤백 검증)
                }
            }
        }
        */
    }
}
