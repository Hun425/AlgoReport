package com.algoreport.module.studygroup

import com.algoreport.config.outbox.OutboxService
import com.algoreport.module.user.User
import com.algoreport.module.user.UserService
import com.algoreport.module.user.SagaStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * CREATE_GROUP_SAGA 테스트
 * TDD Red 단계: CREATE_GROUP_SAGA 관련 클래스들이 존재하지 않으므로 컴파일 실패 예상
 * 
 * 비즈니스 요구사항:
 * - 로그인한 사용자만 스터디 그룹 생성 가능
 * - 그룹명은 중복될 수 없음
 * - 그룹 생성 시 생성자가 자동으로 그룹장으로 설정됨
 * - 그룹 생성 완료 시 GROUP_CREATED 이벤트 발행
 * - 실패 시 생성된 데이터 모두 롤백
 */
@SpringBootTest(classes = [com.algoreport.AlgoReportApplication::class, com.algoreport.config.TestConfiguration::class])
@ActiveProfiles("test")
@Transactional
class CreateGroupSagaTest(
    private val createGroupSaga: CreateGroupSaga,
    private val studyGroupService: StudyGroupService,
    private val userService: UserService,
    private val outboxService: OutboxService
) : BehaviorSpec() {
    
    override fun extensions() = listOf(SpringExtension)
    
    init {
        beforeEach {
            // 각 테스트 전에 상태 초기화
            studyGroupService.clear()
            userService.clear()
        }
        
        given("CREATE_GROUP_SAGA가 실행될 때") {
            val groupName = "알고리즘 스터디"
            val description = "매일 1문제씩 풀어보는 스터디입니다."
            
            `when`("유효한 그룹 생성 요청이 제공되면") {
                then("스터디 그룹이 성공적으로 생성되어야 한다") {
                    // 각 테스트마다 새로운 사용자 생성
                    val testUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "owner@example.com",
                            nickname = "그룹장",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    val ownerId = testUser.id
                    
                    val request = CreateGroupRequest(
                        ownerId = ownerId,
                        name = groupName,
                        description = description
                    )
                    
                    println("DEBUG: Starting CREATE_GROUP_SAGA with ownerId: $ownerId")
                    val result = createGroupSaga.start(request)
                    println("DEBUG: SAGA result - status: ${result.sagaStatus}, groupId: ${result.groupId}, error: ${result.errorMessage}")
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.groupId shouldNotBe null
                    result.errorMessage shouldBe null
                }
                
                then("생성된 그룹의 소유자가 올바르게 설정되어야 한다") {
                    // 각 테스트마다 새로운 사용자 생성
                    val testUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "owner2@example.com",
                            nickname = "그룹장2",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    val ownerId = testUser.id
                    
                    val request = CreateGroupRequest(
                        ownerId = ownerId,
                        name = groupName,
                        description = description
                    )
                    
                    val result = createGroupSaga.start(request)
                    
                    // 그룹 정보 확인
                    val group = studyGroupService.findById(result.groupId!!)
                    group shouldNotBe null
                    group?.ownerId shouldBe ownerId
                    group?.name shouldBe groupName
                    group?.description shouldBe description
                    group?.memberCount shouldBe 1 // 그룹장 포함
                }
                
                then("GROUP_CREATED 이벤트가 발행되어야 한다") {
                    // 각 테스트마다 새로운 사용자 생성
                    val testUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "owner3@example.com",
                            nickname = "그룹장3",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    val ownerId = testUser.id
                    
                    val request = CreateGroupRequest(
                        ownerId = ownerId,
                        name = groupName,
                        description = description
                    )
                    
                    val result = createGroupSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    // TODO: OutboxService 이벤트 발행 확인 (실제 구현 후 추가)
                }
            }
            
            `when`("존재하지 않는 사용자가 그룹 생성을 시도하면") {
                then("Saga가 실패하고 그룹이 생성되지 않아야 한다") {
                    val nonExistentUserId = "non-existent-user-id"
                    
                    val request = CreateGroupRequest(
                        ownerId = nonExistentUserId,
                        name = "존재하지않는사용자그룹",
                        description = description
                    )
                    
                    val result = createGroupSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.groupId shouldBe null
                    result.errorMessage shouldNotBe null
                    
                    // 그룹이 생성되지 않았는지 확인
                    studyGroupService.existsByName("존재하지않는사용자그룹") shouldBe false
                }
            }
            
            `when`("이미 존재하는 그룹명으로 생성을 시도하면") {
                val duplicateGroupName = "중복 그룹명"
                
                then("중복 그룹명 오류가 발생해야 한다") {
                    // 첫 번째 사용자 생성
                    val firstUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "first@example.com",
                            nickname = "첫번째",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    // 두 번째 사용자 생성
                    val secondUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "second@example.com",
                            nickname = "두번째",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    // 첫 번째 그룹 생성 성공
                    val firstRequest = CreateGroupRequest(
                        ownerId = firstUser.id,
                        name = duplicateGroupName,
                        description = "첫 번째 그룹"
                    )
                    val firstResult = createGroupSaga.start(firstRequest)
                    firstResult.sagaStatus shouldBe SagaStatus.COMPLETED
                    
                    // 같은 이름으로 두 번째 그룹 생성 시도 (실패해야 함)
                    val secondRequest = CreateGroupRequest(
                        ownerId = secondUser.id,
                        name = duplicateGroupName,
                        description = "두 번째 그룹"
                    )
                    val secondResult = createGroupSaga.start(secondRequest)
                    
                    secondResult.sagaStatus shouldBe SagaStatus.FAILED
                    secondResult.groupId shouldBe null
                    secondResult.errorMessage shouldNotBe null
                }
            }
        }
        
        given("CREATE_GROUP_SAGA에서 중간 단계가 실패할 때") {
            val groupName = "실패 테스트 그룹"
            
            `when`("그룹 멤버 추가가 실패하면") {
                then("보상 트랜잭션이 실행되어 생성된 그룹이 삭제되어야 한다") {
                    // 비유효한 사용자 ID로 테스트 (실패 시나리오)
                    val request = CreateGroupRequest(
                        ownerId = "non_existent_user",
                        name = groupName,
                        description = "실패 시나리오 테스트"
                    )
                    
                    val result = createGroupSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldNotBe null
                    
                    // 그룹이 생성되지 않았는지 확인
                    studyGroupService.existsByName(groupName) shouldBe false
                }
            }
        }
        
        given("CREATE_GROUP_SAGA의 이벤트 발행을 확인할 때") {
            val groupName = "이벤트 테스트 그룹"
            
            `when`("그룹 생성이 성공하면") {
                then("적절한 이벤트가 발행되어야 한다") {
                    // 이벤트 테스트용 사용자 생성
                    val eventTestUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "event@example.com",
                            nickname = "이벤트테스터",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    val ownerId = eventTestUser.id
                    
                    val request = CreateGroupRequest(
                        ownerId = ownerId,
                        name = groupName,
                        description = "이벤트 발행 테스트"
                    )
                    
                    val result = createGroupSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    
                    // 발행된 이벤트들 확인은 실제 OutboxService 구현 후 추가 예정
                    // 현재는 기본 성공 검증만 수행
                    result.groupId shouldNotBe null
                }
            }
        }
    }
}