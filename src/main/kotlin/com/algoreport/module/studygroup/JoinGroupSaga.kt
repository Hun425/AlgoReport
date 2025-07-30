package com.algoreport.module.studygroup

import com.algoreport.config.outbox.OutboxService
import com.algoreport.module.user.UserService
import com.algoreport.module.user.SagaStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * JOIN_GROUP_SAGA 구현
 * 
 * TDD RED 단계: "Fake It" 방식으로 가장 간단한 기본값만 반환
 * 모든 테스트가 실패하도록 의도적으로 잘못된 기본값 반환
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
     * TDD RED 단계: 컴파일 오류만 해결, 가장 간단한 가짜 값 반환
     * 실제 로직 없이 기본값만 반환하여 모든 테스트 실패 유도
     */
    fun start(request: JoinGroupRequest): JoinGroupResult {
        logger.info("Starting JOIN_GROUP_SAGA for user: {}, group: {}", request.userId, request.groupId)
        
        // RED 단계: 의도적으로 잘못된 기본값 반환 (모든 테스트 실패)
        return JoinGroupResult(
            sagaStatus = SagaStatus.PENDING,  // 기본값 (테스트는 COMPLETED 기대)
            groupId = null,                   // 기본값 (테스트는 실제 groupId 기대)
            userId = null,                    // 기본값 (테스트는 실제 userId 기대)
            errorMessage = null               // 기본값 (실패 테스트는 에러 메시지 기대)
        )
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