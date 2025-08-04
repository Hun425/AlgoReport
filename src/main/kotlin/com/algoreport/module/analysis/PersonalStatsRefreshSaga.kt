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
     * TDD Green 단계: 실제 비즈니스 로직 구현
     */
    fun start(request: PersonalStatsRefreshRequest): PersonalStatsRefreshResult {
        logger.info("Starting PERSONAL_STATS_REFRESH_SAGA for user: {}, forceRefresh: {}, requestedBy: {}", 
            request.userId, request.forceRefresh, request.requestedBy)
        
        val startTime = System.currentTimeMillis()
        
        return try {
            // Step 1: 사용자 존재 여부 검증
            validateUser(request.userId)
            logger.debug("User validation completed for user: {}", request.userId)
            
            // Step 2: 캐시된 데이터 확인 (forceRefresh가 false인 경우)
            val usedCachedData = if (!request.forceRefresh) {
                checkAndUseCachedData(request.userId)
            } else {
                false
            }
            
            if (usedCachedData) {
                // 캐시 데이터 활용 시 빠른 완료
                val processingTime = System.currentTimeMillis() - startTime
                logger.info("PERSONAL_STATS_REFRESH_SAGA completed using cached data for user: {} in {}ms", 
                    request.userId, processingTime)
                
                return PersonalStatsRefreshResult(
                    sagaStatus = SagaStatus.COMPLETED,
                    userId = request.userId,
                    dataCollectionCompleted = true,
                    elasticsearchIndexingCompleted = true,
                    cacheUpdateCompleted = true,
                    eventPublished = true,
                    usedCachedData = true,
                    compensationExecuted = false,
                    errorMessage = null,
                    processingTimeMs = processingTime
                )
            }
            
            // Step 3: solved.ac API에서 최신 제출 데이터 수집
            collectLatestSubmissionData(request.userId, request.includeRecentSubmissions)
            logger.debug("Data collection completed for user: {}", request.userId)
            
            // Step 4: 개인 통계 분석 및 계산
            val analysis = analysisService.performPersonalAnalysis(request.userId)
            logger.debug("Personal analysis completed for user: {}", request.userId)
            
            // Step 5: Elasticsearch 인덱싱
            var elasticsearchSuccess = true
            try {
                indexToElasticsearch(request.userId, analysis)
                logger.debug("Elasticsearch indexing completed for user: {}", request.userId)
            } catch (e: Exception) {
                logger.error("Elasticsearch indexing failed for user {}: {}", request.userId, e.message, e)
                elasticsearchSuccess = false
            }
            
            // Step 6: Redis 캐시 업데이트
            analysisCacheService.cachePersonalAnalysis(request.userId, analysis)
            logger.debug("Cache update completed for user: {}", request.userId)
            
            // Step 7: PERSONAL_STATS_REFRESHED 이벤트 발행
            var eventPublished = true
            try {
                publishPersonalStatsRefreshedEvent(request.userId, request.requestedBy)
                logger.debug("Event published for user: {}", request.userId)
            } catch (e: Exception) {
                logger.error("Event publishing failed for user {}: {}", request.userId, e.message, e)
                eventPublished = false
                // 이벤트 발행 실패는 비즈니스 로직에 치명적이지 않으므로 계속 진행
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            val finalStatus = if (elasticsearchSuccess) SagaStatus.COMPLETED else SagaStatus.PARTIAL_SUCCESS
            
            logger.info("PERSONAL_STATS_REFRESH_SAGA completed for user: {} with status: {} in {}ms", 
                request.userId, finalStatus, processingTime)
            
            PersonalStatsRefreshResult(
                sagaStatus = finalStatus,
                userId = request.userId,
                dataCollectionCompleted = true,
                elasticsearchIndexingCompleted = elasticsearchSuccess,
                cacheUpdateCompleted = true,
                eventPublished = eventPublished,
                usedCachedData = false,
                compensationExecuted = false,
                errorMessage = null,
                processingTimeMs = processingTime
            )
            
        } catch (e: Exception) {
            logger.error("PERSONAL_STATS_REFRESH_SAGA failed for user {}: {}", request.userId, e.message, e)
            
            // 보상 트랜잭션 실행
            executeCompensation(request.userId)
            
            val processingTime = System.currentTimeMillis() - startTime
            PersonalStatsRefreshResult(
                sagaStatus = SagaStatus.FAILED,
                userId = request.userId,
                dataCollectionCompleted = false,
                elasticsearchIndexingCompleted = false,
                cacheUpdateCompleted = false,
                eventPublished = false,
                usedCachedData = false,
                compensationExecuted = true,
                errorMessage = e.message ?: "Unknown error occurred",
                processingTimeMs = processingTime
            )
        }
    }
    
    /**
     * Step 1: 사용자 존재 여부 검증
     */
    private fun validateUser(userId: String) {
        val userIds = userRepository.findAllActiveUserIds()
        if (!userIds.contains(userId)) {
            throw IllegalArgumentException("User not found: $userId")
        }
    }
    
    /**
     * Step 2: 캐시된 데이터 확인 및 활용
     * 최근 1시간 이내 캐시된 데이터가 있으면 활용
     */
    private fun checkAndUseCachedData(userId: String): Boolean {
        return try {
            val cachedAnalysis = analysisCacheService.getPersonalAnalysisFromCache(userId)
            if (cachedAnalysis != null) {
                val cacheAge = java.time.Duration.between(cachedAnalysis.analysisDate, java.time.LocalDateTime.now())
                val isFresh = cacheAge.toMinutes() < 60 // 1시간 이내 데이터
                
                if (isFresh) {
                    logger.debug("Using cached data for user: {}, cache age: {} minutes", userId, cacheAge.toMinutes())
                    // 캐시된 데이터를 AnalysisService에도 등록 (테스트 호환성)
                    analysisService.setPersonalAnalysis(userId, cachedAnalysis)
                    return true
                }
            }
            false
        } catch (e: Exception) {
            logger.error("Failed to check cached data for user {}: {}", userId, e.message, e)
            false
        }
    }
    
    /**
     * Step 3: solved.ac API에서 최신 제출 데이터 수집
     * 실제 구현에서는 SolvedacApiClient 사용
     */
    private fun collectLatestSubmissionData(userId: String, includeRecentSubmissions: Boolean) {
        // Green 단계: 기본적인 데이터 수집 시뮬레이션
        // 실제 구현에서는 SolvedacApiClient를 통해 solved.ac API 호출
        logger.debug("Collecting latest submission data for user: {}, includeRecent: {}", userId, includeRecentSubmissions)
        
        // TODO: 실제 solved.ac API 호출 로직 구현 (REFACTOR 단계에서)
        // solvedacApiClient.getUserSubmissions(userId, includeRecentSubmissions)
    }
    
    /**
     * Step 5: Elasticsearch 인덱싱
     * 실제 구현에서는 ElasticsearchRepository 사용
     */
    private fun indexToElasticsearch(userId: String, analysis: PersonalAnalysis) {
        // Green 단계: 기본적인 인덱싱 시뮬레이션
        // 실제 구현에서는 Elasticsearch 클라이언트 사용
        logger.debug("Indexing personal analysis to Elasticsearch for user: {}", userId)
        
        // TODO: 실제 Elasticsearch 인덱싱 로직 구현 (REFACTOR 단계에서)
        // elasticsearchRepository.indexPersonalAnalysis(analysis)
    }
    
    /**
     * Step 7: PERSONAL_STATS_REFRESHED 이벤트 발행
     */
    private fun publishPersonalStatsRefreshedEvent(userId: String, requestedBy: String) {
        try {
            val eventData = mapOf(
                "userId" to userId,
                "requestedBy" to requestedBy,
                "refreshedAt" to java.time.LocalDateTime.now().toString(),
                "sagaType" to "PERSONAL_STATS_REFRESH_SAGA",
                "timestamp" to System.currentTimeMillis()
            )
            
            outboxService.publishEvent(
                eventType = "PERSONAL_STATS_REFRESHED",
                aggregateId = userId,
                aggregateType = "USER",
                eventData = eventData
            )
            
            logger.info("Published PERSONAL_STATS_REFRESHED event for user: {}", userId)
        } catch (e: Exception) {
            logger.error("Failed to publish personal stats refreshed event for user {}: {}", userId, e.message, e)
            throw e
        }
    }
    
    /**
     * 보상 트랜잭션: 실패 시 생성된 분석 결과 및 캐시 롤백
     */
    private fun executeCompensation(userId: String) {
        logger.info("Executing compensation transaction for PERSONAL_STATS_REFRESH_SAGA, user: {}", userId)
        
        try {
            // 생성된 개인 분석 결과 삭제
            analysisService.deletePersonalAnalysis(userId)
            logger.debug("Deleted personal analysis for user: {}", userId)
            
            // Redis 캐시도 함께 삭제 (데이터 일관성 보장)
            analysisCacheService.evictPersonalAnalysis(userId)
            logger.debug("Evicted personal analysis cache for user: {}", userId)
            
            // 보상 이벤트 발행
            publishCompensationEvent(userId, "PERSONAL_STATS_REFRESH_COMPENSATED")
            
            logger.info("Compensation transaction completed successfully for user: {}", userId)
        } catch (e: Exception) {
            logger.error("Compensation transaction failed for user {}: {}", userId, e.message, e)
            publishCompensationEvent(userId, "PERSONAL_STATS_REFRESH_COMPENSATION_FAILED")
        }
    }
    
    /**
     * 보상 트랜잭션 이벤트 발행
     */
    private fun publishCompensationEvent(userId: String, eventType: String) {
        try {
            val eventData = mapOf(
                "userId" to userId,
                "sagaType" to "PERSONAL_STATS_REFRESH_SAGA",
                "compensationReason" to "SAGA execution failed",
                "timestamp" to System.currentTimeMillis()
            )
            
            outboxService.publishEvent(
                eventType = eventType,
                aggregateId = userId,
                aggregateType = "USER",
                eventData = eventData
            )
            
            logger.debug("Published compensation event: {} for user: {}", eventType, userId)
        } catch (e: Exception) {
            logger.error("Failed to publish compensation event: {} for user {}, error: {}", eventType, userId, e.message, e)
        }
    }
}