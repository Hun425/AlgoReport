package com.algoreport.module.user

import com.algoreport.collector.SolvedacApiClient
import com.algoreport.collector.dto.UserInfo
import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.config.outbox.OutboxService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.extensions.spring.SpringExtension
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.*

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
        given("SOLVEDAC_LINK_SAGA가 실행될 때") {
            val solvedacHandle = "test_handle"
            val validUserEmail = "test@example.com"
            lateinit var userId: UUID  // 실제 생성된 사용자 ID를 저장
            
            // 각 테스트 전에 상태 초기화 및 사용자 생성
            beforeEach {
                // userService.clear() // JPA Repository 사용으로 불필요
                val createdUser = userService.createUser(
                    UserCreateRequest(
                        email = validUserEmail,
                        nickname = "테스트사용자",
                        provider = AuthProvider.GOOGLE
                    )
                )
                userId = createdUser.id  // 실제 생성된 ID 사용
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
                
                then("중복 연동 오류가 발생해야 한다") {
                    // 다른 사용자 생성 및 핸들 연동
                    val anotherUser = userService.createUser(
                        UserCreateRequest(
                            email = "another@example.com",
                            nickname = "다른사용자",
                            provider = AuthProvider.GOOGLE
                        )
                    )
                    
                    // 첫 번째 사용자가 핸들 연동 성공
                    val firstRequest = SolvedacLinkRequest(
                        userId = anotherUser.id,  // 실제 생성된 ID 사용
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
            val solvedacHandle = "test_handle_failure"
            
            beforeEach {
                // userService.clear() // JPA Repository 사용으로 불필요
            }
            
            `when`("사용자 프로필 업데이트가 실패하면") {
                then("보상 트랜잭션이 실행되어 원래 상태로 롤백되어야 한다") {
                    // UserService가 실패하도록 설정하는 메서드가 필요
                    // 현재는 기본 실패 시나리오만 테스트
                    
                    val request = SolvedacLinkRequest(
                        userId = UUID.randomUUID(),  // 존재하지 않는 UUID
                        solvedacHandle = solvedacHandle
                    )
                    
                    val result = solvedacLinkSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldNotBe null
                }
            }
        }
        
        given("SOLVEDAC_LINK_SAGA Mock을 사용한 실패 시나리오 테스트") {
            val mockUserService = mockk<UserService>()
            val mockSolvedacApiClient = mockk<SolvedacApiClient>()
            val mockOutboxService = mockk<OutboxService>()
            
            val saga = SolvedacLinkSaga(
                userService = mockUserService,
                solvedacApiClient = mockSolvedacApiClient,
                outboxService = mockOutboxService
            )
            
            `when`("사용자가 존재하지 않을 때") {
                then("USER_NOT_FOUND 에러가 발생해야 한다") {
                    val nonexistentUserId = UUID.randomUUID()
                    every { mockUserService.findById(nonexistentUserId) } throws CustomException(Error.USER_NOT_FOUND)
                    
                    val request = SolvedacLinkRequest(
                        userId = nonexistentUserId,
                        solvedacHandle = "testhandle"
                    )
                    
                    val result = saga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldBe Error.USER_NOT_FOUND.message
                    
                    verify(exactly = 1) { mockUserService.findById(nonexistentUserId) }
                    verify(exactly = 0) { mockSolvedacApiClient.getUserInfo(any()) }
                }
            }
            
            `when`("핸들이 이미 다른 사용자에 의해 연동되었을 때") {
                then("ALREADY_LINKED_SOLVEDAC_HANDLE 에러가 발생해야 한다") {
                    val user1Id = UUID.randomUUID()
                    val testUser = User(
                        id = user1Id,
                        email = "test@example.com",
                        nickname = "테스트사용자",
                        provider = AuthProvider.GOOGLE
                    )
                    
                    every { mockUserService.findById(user1Id) } returns testUser
                    every { mockUserService.existsBySolvedacHandle("duplicatehandle") } returns true
                    
                    val request = SolvedacLinkRequest(
                        userId = user1Id,
                        solvedacHandle = "duplicatehandle"
                    )
                    
                    val result = saga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldBe Error.ALREADY_LINKED_SOLVEDAC_HANDLE.message
                    
                    verify(exactly = 1) { mockUserService.findById(user1Id) }
                    verify(exactly = 1) { mockUserService.existsBySolvedacHandle("duplicatehandle") }
                    verify(exactly = 0) { mockSolvedacApiClient.getUserInfo(any()) }
                }
            }
            
            `when`("solved.ac API에서 사용자를 찾을 수 없을 때") {
                then("SOLVEDAC_USER_NOT_FOUND 에러가 발생해야 한다") {
                    val user1Id = UUID.randomUUID()
                    val testUser = User(
                        id = user1Id,
                        email = "test@example.com",
                        nickname = "테스트사용자",
                        provider = AuthProvider.GOOGLE
                    )
                    
                    every { mockUserService.findById(user1Id) } returns testUser
                    every { mockUserService.existsBySolvedacHandle("invalidhandle") } returns false
                    every { mockSolvedacApiClient.getUserInfo("invalidhandle") } throws CustomException(Error.SOLVEDAC_USER_NOT_FOUND)
                    
                    val request = SolvedacLinkRequest(
                        userId = user1Id,
                        solvedacHandle = "invalidhandle"
                    )
                    
                    val result = saga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldBe Error.SOLVEDAC_USER_NOT_FOUND.message
                    
                    verify(exactly = 1) { mockSolvedacApiClient.getUserInfo("invalidhandle") }
                    verify(exactly = 0) { mockUserService.updateSolvedacInfo(any(), any(), any(), any()) }
                }
            }
            
            `when`("사용자 프로필 업데이트가 실패할 때") {
                then("보상 트랜잭션이 실행되어야 한다") {
                    val user1Id = UUID.randomUUID()
                    val originalUser = User(
                        id = user1Id,
                        email = "test@example.com",
                        nickname = "테스트사용자",
                        provider = AuthProvider.GOOGLE,
                        solvedacHandle = "originalhandle",
                        solvedacTier = 15,
                        solvedacSolvedCount = 100
                    )
                    
                    val userInfo = UserInfo(
                        handle = "newhandle",
                        tier = 20,
                        solvedCount = 200
                    )
                    
                    every { mockUserService.findById(user1Id) } returns originalUser
                    every { mockUserService.existsBySolvedacHandle("newhandle") } returns false
                    every { mockSolvedacApiClient.getUserInfo("newhandle") } returns userInfo
                    every { mockUserService.updateSolvedacInfo(user1Id, "newhandle", 20, 200) } returns null
                    every { mockUserService.updateSolvedacInfo(user1Id, "originalhandle", 15, 100) } returns originalUser
                    
                    val request = SolvedacLinkRequest(
                        userId = user1Id,
                        solvedacHandle = "newhandle"
                    )
                    
                    val result = saga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldBe Error.USER_UPDATE_FAILED.message
                    
                    // 보상 트랜잭션 확인: 원본 정보로 롤백 시도
                    verify(exactly = 1) { mockUserService.updateSolvedacInfo(user1Id, "originalhandle", 15, 100) }
                }
            }
            
            `when`("이벤트 발행이 실패할 때") {
                then("EVENT_PUBLISH_FAILED 에러가 발생해야 한다") {
                    val testUser = User(
                        id = "user1",
                        email = "test@example.com",
                        nickname = "테스트사용자",
                        provider = AuthProvider.GOOGLE
                    )
                    
                    val userInfo = UserInfo(
                        handle = "testhandle",
                        tier = 15,
                        solvedCount = 100
                    )
                    
                    every { mockUserService.findById("user1") } returns testUser
                    every { mockUserService.existsBySolvedacHandle("testhandle") } returns false
                    every { mockSolvedacApiClient.getUserInfo("testhandle") } returns userInfo
                    every { mockUserService.updateSolvedacInfo("user1", "testhandle", 15, 100) } returns testUser.copy(
                        solvedacHandle = "testhandle",
                        solvedacTier = 15,
                        solvedacSolvedCount = 100
                    )
                    every { mockOutboxService.publishEvent(any(), any(), any(), any()) } throws RuntimeException("Event publish failed")
                    every { mockUserService.updateSolvedacInfo("user1", "", 0, 0) } returns testUser
                    
                    val request = SolvedacLinkRequest(
                        userId = "user1",
                        solvedacHandle = "testhandle"
                    )
                    
                    val result = saga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldBe Error.EVENT_PUBLISH_FAILED.message
                    
                    verify(exactly = 1) { mockOutboxService.publishEvent(any(), any(), any(), any()) }
                    // 보상 트랜잭션 확인
                    verify(exactly = 1) { mockUserService.updateSolvedacInfo("user1", "", 0, 0) }
                }
            }
        }
        
        given("SOLVEDAC_LINK_SAGA의 이벤트 발행을 확인할 때") {
            val solvedacHandle = "event_test_handle"
            val eventUserEmail = "event@example.com"
            lateinit var eventUserId: String
            
            beforeEach {
                // userService.clear() // JPA Repository 사용으로 불필요
                val eventTestUser = userService.createUser(
                    UserCreateRequest(
                        email = eventUserEmail,
                        nickname = "이벤트테스트사용자",
                        provider = AuthProvider.GOOGLE
                    )
                )
                eventUserId = eventTestUser.id
            }
            
            `when`("연동이 성공하면") {
                then("적절한 이벤트가 발행되어야 한다") {
                    val request = SolvedacLinkRequest(
                        userId = eventUserId,  // 실제 생성된 ID 사용
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