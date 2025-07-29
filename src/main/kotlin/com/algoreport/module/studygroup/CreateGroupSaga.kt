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
        // TODO: [GREEN] 실제 SAGA 로직 구현
        // RED 단계: 컴파일 오류만 해결, 가장 간단한 가짜 값 반환 (정통 TDD "Fake It")
        return CreateGroupResult(
            sagaStatus = SagaStatus.PENDING,  // 기본값 (모든 테스트 실패)
            groupId = null,                   // 기본값 (모든 테스트 실패)  
            errorMessage = null               // 기본값 (모든 테스트 실패)
        )
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