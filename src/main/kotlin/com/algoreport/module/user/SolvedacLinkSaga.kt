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
        // RED 단계: 아무것도 구현하지 않음 (테스트 실패하도록)
        TODO("Not yet implemented")
    }
}