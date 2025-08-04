package com.algoreport.module.analysis

import com.algoreport.config.outbox.OutboxService
import com.algoreport.module.user.SagaStatus
import com.algoreport.module.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 개인 통계 갱신 SAGA
 * TDD Red 단계: 기본 빈 구현체로 모든 테스트 실패 유도
 * 
 * 비즈니스 로직 (구현 예정):
 * 1. 사용자 존재 여부 검증
 * 2. 캐시된 데이터 확인 (forceRefresh가 false인 경우)
 * 3. solved.ac API에서 최신 제출 데이터 수집
 * 4. 개인 통계 분석 및 계산
 * 5. Elasticsearch 인덱싱
 * 6. Redis 캐시 업데이트
 * 7. PERSONAL_STATS_REFRESHED 이벤트 발행
 * 
 * ANALYSIS_UPDATE_SAGA와의 차이점:
 * - 특정 사용자 대상 온디맨드 처리
 * - 캐시 우선 활용 (성능 최적화)
 * - 실시간 사용자 요청 대응
 */
@Component
class PersonalStatsRefreshSaga(
    private val userRepository: UserRepository,
    private val analysisService: AnalysisService,
    private val analysisCacheService: AnalysisCacheService,
    private val outboxService: OutboxService
) {
    
    private val logger = LoggerFactory.getLogger(PersonalStatsRefreshSaga::class.java)
    
    /**
     * 개인 통계 갱신 SAGA 시작
     * TDD Red 단계: 가장 간단한 가짜 값 반환으로 모든 테스트 실패 유도
     */
    fun start(request: PersonalStatsRefreshRequest): PersonalStatsRefreshResult {
        logger.info("Starting PERSONAL_STATS_REFRESH_SAGA for user: {}, forceRefresh: {}, requestedBy: {}", 
            request.userId, request.forceRefresh, request.requestedBy)
        
        // TDD Red 단계: 의도적으로 실패 상태를 반환하여 모든 테스트 실패 유도
        return PersonalStatsRefreshResult(
            sagaStatus = SagaStatus.FAILED, // 모든 테스트에서 COMPLETED를 기대하므로 실패
            userId = request.userId,
            dataCollectionCompleted = false, // 모든 테스트에서 true를 기대하므로 실패
            elasticsearchIndexingCompleted = false, // 모든 테스트에서 true를 기대하므로 실패
            cacheUpdateCompleted = false, // 모든 테스트에서 true를 기대하므로 실패
            eventPublished = false, // 모든 테스트에서 true를 기대하므로 실패
            usedCachedData = false,
            compensationExecuted = false,
            errorMessage = "Not implemented yet", // 실패 이유 명시
            processingTimeMs = 0L
        )
    }
}