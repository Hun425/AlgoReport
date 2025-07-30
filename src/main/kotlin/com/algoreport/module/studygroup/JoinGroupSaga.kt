package com.algoreport.module.studygroup

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.config.outbox.OutboxService
import com.algoreport.module.user.UserService
import com.algoreport.module.user.SagaStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * JOIN_GROUP_SAGA 구현
 * 
 * 스터디 그룹 참여를 처리하는 5단계 SAGA 패턴 구현
 * 
 * 특징:
 * - CustomException 기반 구조화된 예외 처리
 * - 복합 보상 트랜잭션을 통한 데이터 일관성 보장
 * - 단계별 실패 시 자동 롤백 및 이벤트 발행
 * - CDC 기반 OutboxService를 통한 이벤트 발행
 * 
 * 비즈니스 요구사항:
 * - 기존 회원만 스터디 그룹에 참여 가능
 * - 존재하는 그룹에만 참여 가능
 * - 이미 참여한 그룹에는 중복 참여 불가
 * - 그룹 정원 초과 시 참여 불가 (최대 20명)
 * - 그룹 참여 완료 시 GROUP_JOINED 이벤트 발행
 * - 실패 시 중간에 추가된 데이터 모두 롤백
 * 
 * SAGA 프로세스:
 * 1. 사용자 검증 (validateUser)
 * 2. 그룹 존재 확인 (validateGroupExists)  
 * 3. 중복 참여 체크 (validateNotAlreadyJoined)
 * 4. 그룹 정원 확인 (validateGroupCapacity)
 * 5. 멤버 추가 및 이벤트 발행 (addMemberAndPublishEvent)
 * 
 * @property userService 사용자 관리 서비스
 * @property studyGroupService 스터디 그룹 관리 서비스  
 * @property outboxService CDC 기반 이벤트 발행 서비스
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
     * 5단계 SAGA 패턴을 통해 스터디 그룹 참여를 처리합니다.
     * 모든 검증 단계를 통과한 후 멤버를 그룹에 추가하고 이벤트를 발행합니다.
     * 실패 시 보상 트랜잭션을 통해 중간에 추가된 데이터를 롤백합니다.
     * 
     * @param request 그룹 참여 요청 정보 (사용자 ID, 그룹 ID)
     * @return 그룹 참여 결과 (상태, 그룹 ID, 사용자 ID, 에러 메시지)
     * @throws CustomException 검증 실패 시 해당하는 에러 코드와 함께 발생
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
     * 
     * 참여하려는 사용자가 실제로 존재하는지 확인합니다.
     * 
     * @param userId 검증할 사용자 ID
     * @throws CustomException USER_NOT_FOUND - 사용자를 찾을 수 없는 경우
     */
    private fun validateUser(userId: String) {
        val user = userService.findById(userId)
        if (user == null) {
            throw CustomException(Error.USER_NOT_FOUND)
        }
        logger.debug("User validation passed for userId: {}", userId)
    }
    
    /**
     * Step 2: 그룹 존재 확인
     * 
     * 참여하려는 그룹이 실제로 존재하는지 확인합니다.
     * 
     * @param groupId 검증할 그룹 ID
     * @throws CustomException STUDY_GROUP_NOT_FOUND - 그룹을 찾을 수 없는 경우
     */
    private fun validateGroupExists(groupId: String) {
        if (!studyGroupService.existsById(groupId)) {
            throw CustomException(Error.STUDY_GROUP_NOT_FOUND)
        }
        logger.debug("Group validation passed for groupId: {}", groupId)
    }
    
    /**
     * Step 3: 중복 참여 체크
     * 
     * 사용자가 이미 해당 그룹의 멤버인지 확인합니다.
     * 
     * @param groupId 확인할 그룹 ID
     * @param userId 확인할 사용자 ID
     * @throws CustomException ALREADY_JOINED_STUDY - 이미 참여한 그룹인 경우
     */
    private fun validateNotAlreadyJoined(groupId: String, userId: String) {
        if (studyGroupService.isUserAlreadyMember(groupId, userId)) {
            throw CustomException(Error.ALREADY_JOINED_STUDY)
        }
        logger.debug("Duplicate membership check passed for user: {}, group: {}", userId, groupId)
    }
    
    /**
     * Step 4: 그룹 정원 확인
     * 
     * 그룹이 최대 정원에 도달했는지 확인합니다 (최대 20명).
     * 
     * @param groupId 확인할 그룹 ID
     * @throws CustomException STUDY_GROUP_CAPACITY_EXCEEDED - 그룹 정원이 초과된 경우
     */
    private fun validateGroupCapacity(groupId: String) {
        if (studyGroupService.isGroupAtCapacity(groupId)) {
            throw CustomException(Error.STUDY_GROUP_CAPACITY_EXCEEDED)
        }
        logger.debug("Group capacity check passed for groupId: {}", groupId)
    }
    
    /**
     * Step 5: 멤버 추가 및 이벤트 발행
     * 
     * 실제로 그룹에 멤버를 추가하고 GROUP_JOINED 이벤트를 발행합니다.
     * 
     * @param groupId 멤버를 추가할 그룹 ID
     * @param userId 추가할 사용자 ID
     * @throws CustomException GROUP_MEMBER_ADD_FAILED - 멤버 추가에 실패한 경우
     */
    private fun addMemberAndPublishEvent(groupId: String, userId: String) {
        // 멤버 추가
        val updatedGroup = studyGroupService.addMember(groupId, userId)
        if (updatedGroup == null) {
            throw CustomException(Error.GROUP_MEMBER_ADD_FAILED)
        }
        
        // GROUP_JOINED 이벤트 발행
        publishGroupJoinedEvent(groupId, userId, updatedGroup.memberCount)
        
        logger.debug("Member added successfully to group: {}, new member count: {}", 
            groupId, updatedGroup.memberCount)
    }
    
    /**
     * GROUP_JOINED 이벤트 발행
     * 
     * 그룹 참여 완료 시 GROUP_JOINED 이벤트를 OutboxService를 통해 발행합니다.
     * 
     * @param groupId 참여된 그룹 ID
     * @param userId 참여한 사용자 ID  
     * @param newMemberCount 새로운 멤버 수
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
     * REFACTOR 단계: 복합 보상 트랜잭션 완성
     * SAGA 실패 시 중간에 추가된 데이터를 모두 롤백합니다.
     * 멱등성을 보장하여 여러 번 실행되어도 안전합니다.
     * 
     * @param request 원본 그룹 참여 요청
     */
    private fun executeCompensation(request: JoinGroupRequest) {
        logger.warn("Starting compensation transaction for user: {}, group: {}", 
            request.userId, request.groupId)
        
        try {
            // Step 5에서 실패한 경우: 추가된 멤버 제거 (멱등성 보장)
            if (studyGroupService.isUserAlreadyMember(request.groupId, request.userId)) {
                val removedGroup = studyGroupService.removeMember(request.groupId, request.userId)
                if (removedGroup != null) {
                    logger.warn("Compensation: Successfully removed member - user: {}, group: {}, remaining count: {}", 
                        request.userId, request.groupId, removedGroup.memberCount)
                    
                    // 보상 트랜잭션 이벤트 발행
                    publishCompensationEvent(request.groupId, request.userId, "GROUP_JOIN_COMPENSATED")
                } else {
                    logger.warn("Compensation: Failed to remove member - user: {}, group: {}", 
                        request.userId, request.groupId)
                    publishCompensationEvent(request.groupId, request.userId, "GROUP_JOIN_COMPENSATION_FAILED")
                }
            } else {
                logger.debug("Compensation: No member found to remove for user: {}, group: {}", 
                    request.userId, request.groupId)
            }
            
        } catch (e: Exception) {
            logger.error("Compensation transaction failed for user: {}, group: {}, error: {}", 
                request.userId, request.groupId, e.message, e)
            
            // 보상 실패 이벤트 발행
            publishCompensationEvent(request.groupId, request.userId, "GROUP_JOIN_COMPENSATION_FAILED")
        }
    }
    
    /**
     * 보상 트랜잭션 관련 이벤트 발행
     * 
     * 보상 트랜잭션 실행 결과를 이벤트로 발행합니다.
     * 
     * @param groupId 보상 트랜잭션이 실행된 그룹 ID
     * @param userId 보상 트랜잭션이 실행된 사용자 ID
     * @param eventType 보상 이벤트 타입 (GROUP_JOIN_COMPENSATED, GROUP_JOIN_COMPENSATION_FAILED)
     */
    private fun publishCompensationEvent(groupId: String, userId: String, eventType: String) {
        try {
            val eventData = mapOf<String, Any>(
                "eventType" to eventType,
                "groupId" to groupId,
                "userId" to userId,
                "timestamp" to System.currentTimeMillis(),
                "compensationReason" to "JOIN_GROUP_SAGA_FAILURE"
            )
            
            outboxService.publishEvent(
                aggregateType = "STUDY_GROUP_COMPENSATION",
                aggregateId = "compensation-${System.currentTimeMillis()}",
                eventType = eventType,
                eventData = eventData
            )
            
            logger.debug("Compensation event published: {}", eventType)
        } catch (e: Exception) {
            logger.error("Failed to publish compensation event: {}, error: {}", eventType, e.message, e)
        }
    }
}

/**
 * JOIN_GROUP_SAGA 요청 데이터
 * 
 * @property userId 그룹에 참여하려는 사용자 ID
 * @property groupId 참여하려는 그룹 ID
 */
data class JoinGroupRequest(
    val userId: String,
    val groupId: String
)

/**
 * JOIN_GROUP_SAGA 실행 결과
 * 
 * @property sagaStatus SAGA 실행 상태 (COMPLETED, FAILED)
 * @property groupId 참여된 그룹 ID (성공 시)
 * @property userId 참여한 사용자 ID (성공 시)
 * @property errorMessage 오류 메시지 (실패 시)
 */
data class JoinGroupResult(
    val sagaStatus: SagaStatus,
    val groupId: String?,
    val userId: String?,
    val errorMessage: String? = null
)