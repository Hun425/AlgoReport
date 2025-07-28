package com.algoreport.module.user

import com.algoreport.collector.SolvedacApiClient
import com.algoreport.config.outbox.OutboxService
import org.springframework.stereotype.Service

/**
 * SOLVEDAC_LINK_SAGA 구현
 * TDD RED 단계: 컴파일 성공을 위한 빈 구현체
 */
@Service
class SolvedacLinkSaga(
    private val userService: UserService,
    private val solvedacApiClient: SolvedacApiClient,
    private val outboxService: OutboxService
) {
    
    fun start(request: SolvedacLinkRequest): SolvedacLinkResult {
        // RED 단계: 테스트가 실패하도록 잘못된 값 반환
        return SolvedacLinkResult(
            sagaStatus = SagaStatus.FAILED,  // 테스트는 COMPLETED를 기대함 → 실패
            linkedHandle = null,             // 테스트는 handle을 기대함 → 실패
            errorMessage = "Not implemented yet"
        )
    }
}