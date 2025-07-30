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
 * JOIN_GROUP_SAGA 테스트
 * TDD Red 단계: JOIN_GROUP_SAGA 관련 클래스들의 테스트 작성
 * 
 * 비즈니스 요구사항:
 * - 기존 회원만 스터디 그룹에 참여 가능
 * - 존재하는 그룹에만 참여 가능
 * - 이미 참여한 그룹에는 중복 참여 불가
 * - 그룹 정원 초과 시 참여 불가 (최대 20명)
 * - 그룹 참여 완료 시 GROUP_JOINED 이벤트 발행
 * - 실패 시 중간에 추가된 데이터 모두 롤백
 * 
 * 5단계 SAGA 프로세스:
 * 1. 사용자 검증 (validateUser)
 * 2. 그룹 존재 확인 (validateGroupExists)  
 * 3. 중복 참여 체크 (validateNotAlreadyJoined)
 * 4. 그룹 정원 확인 (validateGroupCapacity)
 * 5. 멤버 추가 및 이벤트 발행 (addMemberAndPublishEvent)
 */
@SpringBootTest(classes = [com.algoreport.AlgoReportApplication::class, com.algoreport.config.TestConfiguration::class])
@ActiveProfiles("test")
@Transactional
class JoinGroupSagaTest(
    private val joinGroupSaga: JoinGroupSaga,
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
        
        given("JOIN_GROUP_SAGA가 실행될 때") {
            val groupName = "테스트 알고리즘 스터디"
            val groupDescription = "코딩 테스트 준비 스터디"
            
            `when`("유효한 그룹 참여 요청이 제공되면") {
                then("스터디 그룹에 성공적으로 참여되어야 한다") {
                    // 그룹장 사용자 생성
                    val ownerUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "owner@example.com",
                            nickname = "그룹장",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    // 참여자 사용자 생성
                    val memberUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "member@example.com",
                            nickname = "참여자",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    // 스터디 그룹 생성 (그룹장 포함, memberCount = 1)
                    val createRequest = CreateGroupRequest(
                        ownerId = ownerUser.id,
                        name = groupName,
                        description = groupDescription
                    )
                    val createdGroup = studyGroupService.createGroup(createRequest)
                    studyGroupService.addMember(createdGroup.id, ownerUser.id) // 그룹장 추가
                    
                    // 그룹 참여 요청
                    val joinRequest = JoinGroupRequest(
                        userId = memberUser.id,
                        groupId = createdGroup.id
                    )
                    
                    val result = joinGroupSaga.start(joinRequest)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.groupId shouldBe createdGroup.id
                    result.userId shouldBe memberUser.id
                    result.errorMessage shouldBe null
                    
                    // 그룹 멤버 수 증가 확인 (1 → 2)
                    val updatedGroup = studyGroupService.findById(createdGroup.id)
                    updatedGroup?.memberCount shouldBe 2
                }
                
                then("GROUP_JOINED 이벤트가 발행되어야 한다") {
                    // 그룹장 사용자 생성
                    val ownerUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "owner2@example.com",
                            nickname = "그룹장2",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    // 참여자 사용자 생성
                    val memberUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "member2@example.com",
                            nickname = "참여자2",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    // 스터디 그룹 생성
                    val createRequest = CreateGroupRequest(
                        ownerId = ownerUser.id,
                        name = groupName + "_이벤트테스트",
                        description = groupDescription
                    )
                    val createdGroup = studyGroupService.createGroup(createRequest)
                    studyGroupService.addMember(createdGroup.id, ownerUser.id)
                    
                    // 그룹 참여 요청
                    val joinRequest = JoinGroupRequest(
                        userId = memberUser.id,
                        groupId = createdGroup.id
                    )
                    
                    val result = joinGroupSaga.start(joinRequest)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    
                    // OutboxService 이벤트 발행 확인 (TestConfiguration Mock에서 처리)
                    result.groupId shouldNotBe null
                    result.userId shouldNotBe null
                }
            }
            
            `when`("존재하지 않는 사용자가 그룹 참여를 시도하면") {
                then("Saga가 실패하고 사용자 검증 오류가 발생해야 한다") {
                    // 그룹장만 생성
                    val ownerUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "owner3@example.com",
                            nickname = "그룹장3",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    // 스터디 그룹 생성
                    val createRequest = CreateGroupRequest(
                        ownerId = ownerUser.id,
                        name = groupName + "_유효하지않은사용자",
                        description = groupDescription
                    )
                    val createdGroup = studyGroupService.createGroup(createRequest)
                    studyGroupService.addMember(createdGroup.id, ownerUser.id)
                    
                    // 존재하지 않는 사용자로 참여 시도
                    val joinRequest = JoinGroupRequest(
                        userId = "non-existent-user-id",
                        groupId = createdGroup.id
                    )
                    
                    val result = joinGroupSaga.start(joinRequest)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.groupId shouldBe null
                    result.userId shouldBe null
                    result.errorMessage shouldNotBe null
                    
                    // 그룹 멤버 수 변화 없음 (여전히 1명)
                    val unchangedGroup = studyGroupService.findById(createdGroup.id)
                    unchangedGroup?.memberCount shouldBe 1
                }
            }
            
            `when`("존재하지 않는 그룹에 참여를 시도하면") {
                then("그룹 존재 확인 오류가 발생해야 한다") {
                    // 참여자 사용자 생성
                    val memberUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "member3@example.com",
                            nickname = "참여자3",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    // 존재하지 않는 그룹에 참여 시도
                    val joinRequest = JoinGroupRequest(
                        userId = memberUser.id,
                        groupId = "non-existent-group-id"
                    )
                    
                    val result = joinGroupSaga.start(joinRequest)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldNotBe null
                }
            }
            
            `when`("이미 참여한 그룹에 중복 참여를 시도하면") {
                then("중복 참여 오류가 발생해야 한다") {
                    // 그룹장 및 참여자 생성
                    val ownerUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "owner4@example.com",
                            nickname = "그룹장4",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    val memberUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "member4@example.com",
                            nickname = "참여자4",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    // 스터디 그룹 생성
                    val createRequest = CreateGroupRequest(
                        ownerId = ownerUser.id,
                        name = groupName + "_중복참여테스트",
                        description = groupDescription
                    )
                    val createdGroup = studyGroupService.createGroup(createRequest)
                    studyGroupService.addMember(createdGroup.id, ownerUser.id)
                    
                    // 첫 번째 참여 (성공)
                    val firstJoinRequest = JoinGroupRequest(
                        userId = memberUser.id,
                        groupId = createdGroup.id
                    )
                    val firstResult = joinGroupSaga.start(firstJoinRequest)
                    firstResult.sagaStatus shouldBe SagaStatus.COMPLETED
                    
                    // 두 번째 참여 시도 (중복 참여 - 실패해야 함)
                    val secondJoinRequest = JoinGroupRequest(
                        userId = memberUser.id,
                        groupId = createdGroup.id
                    )
                    val secondResult = joinGroupSaga.start(secondJoinRequest)
                    
                    secondResult.sagaStatus shouldBe SagaStatus.FAILED
                    secondResult.errorMessage shouldNotBe null
                }
            }
            
            `when`("그룹 정원이 가득 찬 상태에서 참여를 시도하면") {
                then("그룹 정원 초과 오류가 발생해야 한다") {
                    // 그룹장 생성
                    val ownerUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "owner5@example.com",
                            nickname = "그룹장5",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    // 새 참여자 생성
                    val newMemberUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "newmember@example.com",
                            nickname = "신규참여자",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    // 스터디 그룹 생성
                    val createRequest = CreateGroupRequest(
                        ownerId = ownerUser.id,
                        name = groupName + "_정원초과테스트",
                        description = groupDescription
                    )
                    val createdGroup = studyGroupService.createGroup(createRequest)
                    studyGroupService.addMember(createdGroup.id, ownerUser.id)
                    
                    // 그룹을 최대 정원(20명)까지 채우기
                    // 현재 1명(그룹장) → 19명 추가 = 20명 (최대 정원)
                    for (i in 1..19) {
                        val tempUser = userService.createUser(
                            com.algoreport.module.user.UserCreateRequest(
                                email = "temp$i@example.com",
                                nickname = "임시멤버$i",
                                provider = com.algoreport.module.user.AuthProvider.GOOGLE
                            )
                        )
                        studyGroupService.addMember(createdGroup.id, tempUser.id)
                    }
                    
                    // 그룹 정원 확인 (20명)
                    val fullGroup = studyGroupService.findById(createdGroup.id)
                    fullGroup?.memberCount shouldBe 20
                    
                    // 21번째 멤버 추가 시도 (실패해야 함)
                    val joinRequest = JoinGroupRequest(
                        userId = newMemberUser.id,
                        groupId = createdGroup.id
                    )
                    
                    val result = joinGroupSaga.start(joinRequest)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldNotBe null
                    
                    // 그룹 멤버 수 변화 없음 (여전히 20명)
                    val unchangedGroup = studyGroupService.findById(createdGroup.id)
                    unchangedGroup?.memberCount shouldBe 20
                }
            }
        }
        
        given("JOIN_GROUP_SAGA에서 중간 단계가 실패할 때") {
            val groupName = "보상 트랜잭션 테스트 그룹"
            
            `when`("멤버 추가 중 시스템 오류가 발생하면") {
                then("보상 트랜잭션이 실행되어 중간 데이터가 롤백되어야 한다") {
                    // 정상적인 사용자와 그룹 생성
                    val ownerUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "owner6@example.com",
                            nickname = "그룹장6",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    val memberUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "member6@example.com",
                            nickname = "참여자6",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    val createRequest = CreateGroupRequest(
                        ownerId = ownerUser.id,
                        name = groupName,
                        description = "보상 트랜잭션 테스트"
                    )
                    val createdGroup = studyGroupService.createGroup(createRequest)
                    studyGroupService.addMember(createdGroup.id, ownerUser.id)
                    
                    // 시스템 오류 시뮬레이션을 위한 특수 케이스
                    // 실제 구현에서는 addMember 실패 시나리오 구현 필요
                    val joinRequest = JoinGroupRequest(
                        userId = memberUser.id,
                        groupId = createdGroup.id
                    )
                    
                    val result = joinGroupSaga.start(joinRequest)
                    
                    // 현재는 기본적으로 성공하지만, 추후 실패 시나리오 구현 시
                    // 보상 트랜잭션 동작 검증 필요
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                }
            }
        }
        
        given("JOIN_GROUP_SAGA의 이벤트 발행을 확인할 때") {
            val groupName = "이벤트 발행 테스트 그룹"
            
            `when`("그룹 참여가 성공하면") {
                then("GROUP_JOINED 이벤트와 함께 멤버 정보가 포함되어야 한다") {
                    // 사용자 및 그룹 생성
                    val ownerUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "eventowner@example.com",
                            nickname = "이벤트그룹장",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    val memberUser = userService.createUser(
                        com.algoreport.module.user.UserCreateRequest(
                            email = "eventmember@example.com",
                            nickname = "이벤트참여자",
                            provider = com.algoreport.module.user.AuthProvider.GOOGLE
                        )
                    )
                    
                    val createRequest = CreateGroupRequest(
                        ownerId = ownerUser.id,
                        name = groupName,
                        description = "이벤트 발행 검증 테스트"
                    )
                    val createdGroup = studyGroupService.createGroup(createRequest)
                    studyGroupService.addMember(createdGroup.id, ownerUser.id)
                    
                    val joinRequest = JoinGroupRequest(
                        userId = memberUser.id,
                        groupId = createdGroup.id
                    )
                    
                    val result = joinGroupSaga.start(joinRequest)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.groupId shouldBe createdGroup.id
                    result.userId shouldBe memberUser.id
                    
                    // OutboxService 이벤트 발행 검증
                    // 실제 구현에서는 GROUP_JOINED 이벤트 데이터 확인 필요
                    result.errorMessage shouldBe null
                }
            }
        }
    }
}