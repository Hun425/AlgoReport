package com.algoreport.module.studygroup

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.config.outbox.OutboxService
import com.algoreport.module.user.UserService
import com.algoreport.module.user.SagaStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * CREATE_GROUP_SAGA 구현
 * 
 * 스터디 그룹을 생성하는 5단계 SAGA 패턴 구현:
 * 1. 사용자 검증 (validateUser)
 * 2. 그룹명 중복 체크 (validateGroupName)  
 * 3. 스터디 그룹 생성 (createStudyGroup)
 * 4. 그룹장을 멤버로 추가 (addOwnerAsMember)
 * 5. GROUP_CREATED 이벤트 발행 (publishGroupCreatedEvent)
 * 
 * 특징:
 * - 보상 트랜잭션을 통한 데이터 일관성 보장
 * - 단계별 실패 시 자동 롤백
 * - 구조화된 CustomException 기반 예외 처리
 * - CDC 기반 OutboxService를 통한 이벤트 발행
 */
@Service
class CreateGroupSaga(
    private val userService: UserService,
    private val studyGroupService: StudyGroupService,
    private val outboxService: OutboxService
) {
    
    private val logger = LoggerFactory.getLogger(CreateGroupSaga::class.java)
    
    fun start(request: CreateGroupRequest): CreateGroupResult {
        logger.info("Starting CREATE_GROUP_SAGA for owner: {}, groupName: {}", request.ownerId, request.name)
        
        return try {
            // Step 1: 사용자 검증
            validateUser(request.ownerId)
            
            // Step 2: 그룹명 중복 체크
            validateGroupName(request.name)
            
            // Step 3: 스터디 그룹 생성
            val group = createStudyGroup(request)
            
            // Step 4: 그룹장을 멤버로 추가
            addOwnerAsMember(group.id, request.ownerId)
            
            // Step 5: 이벤트 발행
            publishGroupCreatedEvent(group.id, request.ownerId)
            
            logger.info("CREATE_GROUP_SAGA completed successfully for groupId: {}", group.id)
            CreateGroupResult(
                sagaStatus = SagaStatus.COMPLETED,
                groupId = group.id,
                errorMessage = null
            )
            
        } catch (e: Exception) {
            logger.error("CREATE_GROUP_SAGA failed for owner: {}, groupName: {}, error: {}", 
                request.ownerId, request.name, e.message, e)
            
            // 보상 트랜잭션 실행 (생성된 데이터 롤백)
            executeCompensation(request)
            
            CreateGroupResult(
                sagaStatus = SagaStatus.FAILED,
                groupId = null,
                errorMessage = e.message ?: "Unknown error occurred"
            )
        }
    }
    
    private fun validateUser(ownerId: String) {
        val user = userService.findById(ownerId)
        if (user == null) {
            throw CustomException(Error.USER_NOT_FOUND)
        }
        logger.debug("User validation passed for ownerId: {}", ownerId)
    }
    
    private fun validateGroupName(groupName: String) {
        if (studyGroupService.existsByName(groupName)) {
            throw CustomException(Error.DUPLICATE_GROUP_NAME)
        }
        logger.debug("Group name validation passed for name: {}", groupName)
    }
    
    private fun createStudyGroup(request: CreateGroupRequest): StudyGroup {
        val group = studyGroupService.createGroup(request)
        logger.debug("Study group created with id: {}", group.id)
        return group
    }
    
    private fun addOwnerAsMember(groupId: String, ownerId: String) {
        val updatedGroup = studyGroupService.addMember(groupId, ownerId)
        if (updatedGroup == null) {
            throw CustomException(Error.GROUP_MEMBER_ADD_FAILED)
        }
        logger.debug("Owner added as member to group: {}", groupId)
    }
    
    private fun publishGroupCreatedEvent(groupId: String, ownerId: String) {
        val eventData = mapOf<String, Any>(
            "eventType" to "GROUP_CREATED",
            "groupId" to groupId,
            "ownerId" to ownerId,
            "timestamp" to System.currentTimeMillis()
        )
        
        outboxService.publishEvent(
            aggregateType = "STUDY_GROUP",
            aggregateId = "group-$groupId",
            eventType = "GROUP_CREATED",
            eventData = eventData
        )
        
        logger.debug("GROUP_CREATED event published for group: {}, owner: {}", groupId, ownerId)
    }
    
    /**
     * 보상 트랜잭션 실행
     * 
     * SAGA 실패 시 생성된 데이터를 모두 롤백합니다.
     * 멱등성을 보장하여 여러 번 실행되어도 안전합니다.
     * 
     * @param request 원본 그룹 생성 요청
     */
    private fun executeCompensation(request: CreateGroupRequest) {
        logger.warn("Starting compensation transaction for group: {}, owner: {}", request.name, request.ownerId)
        
        try {
            // 생성된 그룹이 있다면 삭제 (멱등성 보장)
            val existingGroup = studyGroupService.findByName(request.name)
            if (existingGroup != null) {
                // 그룹 삭제 (멤버들도 함께 삭제됨)
                studyGroupService.deleteGroup(existingGroup.id)
                logger.warn("Compensation: Successfully rolled back group creation - name: {}, id: {}", 
                    request.name, existingGroup.id)
                
                // 보상 트랜잭션 이벤트 발행
                publishCompensationEvent(existingGroup.id, request.ownerId, "GROUP_CREATION_COMPENSATED")
            } else {
                logger.debug("Compensation: No group found to rollback for name: {}", request.name)
            }
            
        } catch (e: Exception) {
            logger.error("Compensation transaction failed for group: {}, owner: {}, error: {}", 
                request.name, request.ownerId, e.message, e)
            
            // 보상 실패 이벤트 발행
            publishCompensationEvent(null, request.ownerId, "GROUP_CREATION_COMPENSATION_FAILED")
        }
    }
    
    /**
     * 보상 트랜잭션 관련 이벤트 발행
     */
    private fun publishCompensationEvent(groupId: String?, ownerId: String, eventType: String) {
        try {
            val eventData = mapOf<String, Any>(
                "eventType" to eventType,
                "groupId" to (groupId ?: ""),
                "ownerId" to ownerId,
                "timestamp" to System.currentTimeMillis(),
                "compensationReason" to "CREATE_GROUP_SAGA_FAILURE"
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

// CREATE_GROUP_SAGA 관련 데이터 클래스들
data class CreateGroupRequest(
    val ownerId: String,
    val name: String,
    val description: String
)

data class CreateGroupResult(
    val sagaStatus: SagaStatus,
    val groupId: String?,
    val errorMessage: String? = null
)