package com.algoreport.module.studygroup

import com.algoreport.config.outbox.OutboxService
import com.algoreport.module.user.UserService
import com.algoreport.module.user.SagaStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * CREATE_GROUP_SAGA 구현
 * 
 * 스터디 그룹을 생성하는 SAGA 패턴 구현
 * - 보상 트랜잭션을 통한 데이터 일관성 보장
 * - 단계별 실패 시 자동 롤백
 * - 구조화된 예외 처리
 * 
 * TODO: [GREEN] 실제 SAGA 로직 구현 필요
 * TODO: [GREEN] 4단계 SAGA 구현 (사용자 검증, 그룹명 중복체크, 그룹 생성, 그룹장 멤버 추가)
 * TODO: [GREEN] OutboxService를 통한 GROUP_CREATED 이벤트 발행 구현 필요
 * TODO: [REFACTOR] 보상 트랜잭션 및 예외 처리 강화 필요
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
            throw IllegalArgumentException("사용자를 찾을 수 없습니다: $ownerId")
        }
        logger.debug("User validation passed for ownerId: {}", ownerId)
    }
    
    private fun validateGroupName(groupName: String) {
        if (studyGroupService.existsByName(groupName)) {
            throw IllegalArgumentException("이미 존재하는 그룹명입니다: $groupName")
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
            throw IllegalStateException("Failed to add owner as member to group: $groupId")
        }
        logger.debug("Owner added as member to group: {}", groupId)
    }
    
    private fun publishGroupCreatedEvent(groupId: String, ownerId: String) {
        // TODO: OutboxService를 통한 GROUP_CREATED 이벤트 발행 구현
        logger.debug("GROUP_CREATED event published for group: {}, owner: {}", groupId, ownerId)
    }
    
    private fun executeCompensation(request: CreateGroupRequest) {
        try {
            // 생성된 그룹이 있다면 삭제
            if (studyGroupService.existsByName(request.name)) {
                // 그룹명으로 그룹 ID를 찾아서 삭제
                val groupId = studyGroupService.findByName(request.name)?.id
                if (groupId != null) {
                    studyGroupService.deleteGroup(groupId)
                    logger.warn("Compensation: Rolled back group creation for name: {}, id: {}", request.name, groupId)
                }
            }
        } catch (e: Exception) {
            logger.error("Compensation failed for group: {}, error: {}", request.name, e.message, e)
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