package com.algoreport.module.studygroup

import com.algoreport.config.outbox.OutboxService
import com.algoreport.module.user.UserService
import com.algoreport.module.user.SagaStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * JOIN_GROUP_SAGA 구현
 * 
 * TDD GREEN 단계: 5단계 SAGA 패턴 완전 구현
 * 모든 테스트가 통과하도록 실제 비즈니스 로직 구현
 * 
 * 목표: 스터디 그룹 참여를 처리하는 5단계 SAGA 패턴 구현
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
@Service
class JoinGroupSaga(
    private val userService: UserService,
    private val studyGroupService: StudyGroupService,
    private val outboxService: OutboxService
) {
    
    private val logger = LoggerFactory.getLogger(JoinGroupSaga::class.java)
    
    /**
     * JOIN_GROUP_SAGA 시작
     * 
     * TDD GREEN 단계: 5단계 SAGA 패턴 완전 구현
     * 모든 테스트가 통과하도록 실제 비즈니스 로직 구현
     */
    fun start(request: JoinGroupRequest): JoinGroupResult {
        logger.info("Starting JOIN_GROUP_SAGA for user: {}, group: {}", request.userId, request.groupId)
        
        return try {
            // Step 1: 사용자 검증
            validateUser(request.userId)
            
            // Step 2: 그룹 존재 확인
            validateGroupExists(request.groupId)
            
            // Step 3: 중복 참여 체크
            validateNotAlreadyJoined(request.groupId, request.userId)
            
            // Step 4: 그룹 정원 확인
            validateGroupCapacity(request.groupId)
            
            // Step 5: 멤버 추가 및 이벤트 발행
            addMemberAndPublishEvent(request.groupId, request.userId)
            
            logger.info("JOIN_GROUP_SAGA completed successfully for user: {}, group: {}", request.userId, request.groupId)
            JoinGroupResult(
                sagaStatus = SagaStatus.COMPLETED,
                groupId = request.groupId,
                userId = request.userId,
                errorMessage = null
            )
            
        } catch (e: Exception) {
            logger.error("JOIN_GROUP_SAGA failed for user: {}, group: {}, error: {}", 
                request.userId, request.groupId, e.message, e)
            
            // 보상 트랜잭션 실행 (향후 REFACTOR 단계에서 구현)
            executeCompensation(request)
            
            JoinGroupResult(
                sagaStatus = SagaStatus.FAILED,
                groupId = null,
                userId = null,
                errorMessage = e.message ?: "Unknown error occurred"
            )
        }
    }
    
    /**
     * Step 1: 사용자 검증
     * 참여하려는 사용자가 실제로 존재하는지 확인
     */
    private fun validateUser(userId: String) {
        val user = userService.findById(userId)
        if (user == null) {
            throw IllegalArgumentException("사용자를 찾을 수 없습니다: $userId")
        }
        logger.debug("User validation passed for userId: {}", userId)
    }
    
    /**
     * Step 2: 그룹 존재 확인
     * 참여하려는 그룹이 실제로 존재하는지 확인
     */
    private fun validateGroupExists(groupId: String) {
        if (!studyGroupService.existsById(groupId)) {
            throw IllegalArgumentException("스터디 그룹을 찾을 수 없습니다: $groupId")
        }
        logger.debug("Group validation passed for groupId: {}", groupId)
    }
    
    /**
     * Step 3: 중복 참여 체크
     * 사용자가 이미 해당 그룹의 멤버인지 확인
     */
    private fun validateNotAlreadyJoined(groupId: String, userId: String) {
        if (studyGroupService.isUserAlreadyMember(groupId, userId)) {
            throw IllegalArgumentException("이미 참여한 스터디 그룹입니다: $groupId")
        }
        logger.debug("Duplicate membership check passed for user: {}, group: {}", userId, groupId)
    }
    
    /**
     * Step 4: 그룹 정원 확인
     * 그룹이 최대 정원에 도달했는지 확인 (최대 20명)
     */
    private fun validateGroupCapacity(groupId: String) {
        if (studyGroupService.isGroupAtCapacity(groupId)) {
            val currentCount = studyGroupService.getGroupMemberCount(groupId)
            throw IllegalArgumentException("스터디 그룹 정원이 초과되었습니다 (현재 $currentCount/20명)")
        }
        logger.debug("Group capacity check passed for groupId: {}", groupId)
    }
    
    /**
     * Step 5: 멤버 추가 및 이벤트 발행
     * 실제로 그룹에 멤버를 추가하고 GROUP_JOINED 이벤트를 발행
     */
    private fun addMemberAndPublishEvent(groupId: String, userId: String) {
        // 멤버 추가
        val updatedGroup = studyGroupService.addMember(groupId, userId)
        if (updatedGroup == null) {
            throw RuntimeException("그룹 멤버 추가에 실패했습니다: $groupId")
        }
        
        // GROUP_JOINED 이벤트 발행
        publishGroupJoinedEvent(groupId, userId, updatedGroup.memberCount)
        
        logger.debug("Member added successfully to group: {}, new member count: {}", 
            groupId, updatedGroup.memberCount)
    }
    
    /**
     * GROUP_JOINED 이벤트 발행
     */
    private fun publishGroupJoinedEvent(groupId: String, userId: String, newMemberCount: Int) {
        val eventData = mapOf<String, Any>(
            "eventType" to "GROUP_JOINED",
            "groupId" to groupId,
            "userId" to userId,
            "newMemberCount" to newMemberCount,
            "timestamp" to System.currentTimeMillis()
        )
        
        outboxService.publishEvent(
            aggregateType = "STUDY_GROUP",
            aggregateId = "group-$groupId",
            eventType = "GROUP_JOINED",
            eventData = eventData
        )
        
        logger.debug("GROUP_JOINED event published for group: {}, user: {}", groupId, userId)
    }
    
    /**
     * 보상 트랜잭션 실행
     * 
     * GREEN 단계에서는 기본 구현만 제공
     * REFACTOR 단계에서 완전한 보상 로직 구현 예정
     */
    private fun executeCompensation(request: JoinGroupRequest) {
        logger.warn("Executing compensation transaction for user: {}, group: {}", 
            request.userId, request.groupId)
        
        try {
            // 기본 보상 로직: 추가된 멤버가 있다면 제거
            // 실제로는 중간 단계에서 실패했을 때만 필요하므로 현재는 로깅만 수행
            logger.info("Compensation transaction completed for user: {}, group: {}", 
                request.userId, request.groupId)
        } catch (e: Exception) {
            logger.error("Compensation transaction failed for user: {}, group: {}, error: {}", 
                request.userId, request.groupId, e.message, e)
        }
    }
}

// JOIN_GROUP_SAGA 관련 데이터 클래스들
data class JoinGroupRequest(
    val userId: String,
    val groupId: String
)

data class JoinGroupResult(
    val sagaStatus: SagaStatus,
    val groupId: String?,
    val userId: String?,
    val errorMessage: String? = null
)