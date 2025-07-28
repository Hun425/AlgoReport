package com.algoreport.module.user

import com.algoreport.collector.SolvedacApiClient
import com.algoreport.collector.dto.UserInfo
import com.algoreport.config.outbox.OutboxService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * SOLVEDAC_LINK_SAGA 테스트
 * TDD Red 단계: SOLVEDAC_LINK_SAGA 관련 클래스들이 존재하지 않으므로 컴파일 실패 예상
 * 
 * 비즈니스 요구사항:
 * - 로그인한 사용자만 solved.ac 계정 연동 가능
 * - solved.ac API를 통해 핸들 유효성 검증
 * - 연동 성공 시 사용자 프로필에 solved.ac 정보 업데이트
 * - 연동 실패 시 기존 연동 정보는 그대로 유지
 * - 이미 다른 사용자가 연동한 핸들은 연동 불가
 */
@SpringBootTest(classes = [com.algoreport.AlgoReportApplication::class, com.algoreport.config.TestConfiguration::class])
@ActiveProfiles("test")
@Transactional
class SolvedacLinkSagaTest(
    private val solvedacLinkSaga: SolvedacLinkSaga,
    private val userService: UserService,
    private val solvedacApiClient: SolvedacApiClient,
    private val outboxService: OutboxService
) : BehaviorSpec() {
    
    override fun extensions() = listOf(SpringExtension)
    
    init {
        beforeEach {
            // 각 테스트 전에 상태 초기화
            userService.clear()
        }
        
        given("SOLVEDAC_LINK_SAGA가 실행될 때") {
            val userId = "test-user-123"
            val solvedacHandle = "test_handle"
            val validUserEmail = "test@example.com"
            
            // 테스트용 사용자 생성
            beforeContainer {
                userService.createUser(
                    UserCreateRequest(
                        email = validUserEmail,
                        nickname = "테스트사용자",
                        provider = AuthProvider.GOOGLE
                    )
                )
            }
            
            `when`("유효한 solved.ac 핸들이 제공되면") {
                then("solved.ac API에서 사용자 정보를 성공적으로 조회해야 한다") {
                    val request = SolvedacLinkRequest(
                        userId = userId,
                        solvedacHandle = solvedacHandle
                    )
                    
                    val result = solvedacLinkSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.linkedHandle shouldBe solvedacHandle
                    result.errorMessage shouldBe null
                }
                
                then("사용자 프로필에 solved.ac 정보가 업데이트되어야 한다") {
                    val request = SolvedacLinkRequest(
                        userId = userId,
                        solvedacHandle = solvedacHandle
                    )
                    
                    val result = solvedacLinkSaga.start(request)
                    
                    // 사용자 정보에서 solved.ac 정보 확인
                    val user = userService.findById(userId)
                    user shouldNotBe null
                    user?.solvedacHandle shouldBe solvedacHandle
                    user?.solvedacTier shouldNotBe null
                    user?.solvedacSolvedCount shouldNotBe null
                }
                
                then("SOLVEDAC_LINKED 이벤트가 발행되어야 한다") {
                    val request = SolvedacLinkRequest(
                        userId = userId,
                        solvedacHandle = solvedacHandle
                    )
                    
                    val result = solvedacLinkSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    // TODO: OutboxService 이벤트 발행 확인 (실제 구현 후 추가)
                }
            }
            
            `when`("존재하지 않는 solved.ac 핸들이 제공되면") {
                val invalidHandle = "non_existent_handle_12345"
                
                then("Saga가 실패하고 사용자 정보는 변경되지 않아야 한다") {
                    val request = SolvedacLinkRequest(
                        userId = userId,
                        solvedacHandle = invalidHandle
                    )
                    
                    val result = solvedacLinkSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldNotBe null
                    
                    // 사용자 정보가 변경되지 않았는지 확인
                    val user = userService.findById(userId)
                    user?.solvedacHandle shouldBe null
                }
            }
            
            `when`("이미 다른 사용자가 연동한 핸들이 제공되면") {
                val duplicateHandle = "already_linked_handle"
                val anotherUserId = "another-user-456"
                
                then("중복 연동 오류가 발생해야 한다") {
                    // 다른 사용자 생성 및 핸들 연동
                    userService.createUser(
                        UserCreateRequest(
                            email = "another@example.com",
                            nickname = "다른사용자",
                            provider = AuthProvider.GOOGLE
                        )
                    )
                    
                    // 첫 번째 사용자가 핸들 연동 성공
                    val firstRequest = SolvedacLinkRequest(
                        userId = anotherUserId,
                        solvedacHandle = duplicateHandle
                    )
                    val firstResult = solvedacLinkSaga.start(firstRequest)
                    firstResult.sagaStatus shouldBe SagaStatus.COMPLETED
                    
                    // 두 번째 사용자가 같은 핸들 연동 시도 (실패해야 함)
                    val secondRequest = SolvedacLinkRequest(
                        userId = userId,
                        solvedacHandle = duplicateHandle
                    )
                    val secondResult = solvedacLinkSaga.start(secondRequest)
                    
                    secondResult.sagaStatus shouldBe SagaStatus.FAILED
                    secondResult.errorMessage shouldNotBe null
                }
            }
        }
        
        given("SOLVEDAC_LINK_SAGA에서 중간 단계가 실패할 때") {
            val userId = "test-user-789"
            val solvedacHandle = "test_handle_failure"
            
            `when`("사용자 프로필 업데이트가 실패하면") {
                then("보상 트랜잭션이 실행되어 원래 상태로 롤백되어야 한다") {
                    // UserService가 실패하도록 설정하는 메서드가 필요
                    // 현재는 기본 실패 시나리오만 테스트
                    
                    val request = SolvedacLinkRequest(
                        userId = "non_existent_user",
                        solvedacHandle = solvedacHandle
                    )
                    
                    val result = solvedacLinkSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldNotBe null
                }
            }
        }
        
        given("SOLVEDAC_LINK_SAGA의 이벤트 발행을 확인할 때") {
            val userId = "event-test-user"
            val solvedacHandle = "event_test_handle"
            
            `when`("연동이 성공하면") {
                then("적절한 이벤트가 발행되어야 한다") {
                    val request = SolvedacLinkRequest(
                        userId = userId,
                        solvedacHandle = solvedacHandle
                    )
                    
                    val result = solvedacLinkSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    
                    // 발행된 이벤트들 확인은 실제 OutboxService 구현 후 추가 예정
                    // 현재는 기본 성공 검증만 수행
                    result.linkedHandle shouldBe solvedacHandle
                }
            }
        }
    }
}