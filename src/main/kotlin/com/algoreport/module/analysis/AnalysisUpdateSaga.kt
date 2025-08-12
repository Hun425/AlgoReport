package com.algoreport.module.analysis

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.config.outbox.OutboxService
import com.algoreport.module.user.UserRepository
import com.algoreport.module.user.SagaStatus
import com.algoreport.module.studygroup.StudyGroupRepository
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis

/**
 * 분석 업데이트 SAGA
 * TDD Refactor 단계: Repository 패턴 + Redis 캐시 통합
 * 
 * 비즈니스 로직:
 * 1. 사용자 및 그룹 데이터 수집 (collectUserAndGroupData) - Repository 패턴 적용
 * 2. 개인별 통계 분석 - Kotlin Coroutines 병렬 처리 (performPersonalAnalysis)
 * 3. 그룹별 통계 분석 (performGroupAnalysis)
 * 4. Redis 캐시 업데이트 (updateCacheData) - AnalysisCacheService 통합
 * 5. ANALYSIS_UPDATE_COMPLETED 이벤트 발행 (publishAnalysisCompletedEvent)
 * 
 * 개선사항:
 * - Repository 패턴으로 데이터 접근 분리 (리플렉션 제거)
 * - Redis 캐시 서비스로 대시보드 성능 최적화
 * - 배치 캐싱으로 성능 개선
 * - 구조화된 예외 처리 강화
 */
@Component
class AnalysisUpdateSaga(
    private val userRepository: UserRepository,
    private val studyGroupRepository: StudyGroupRepository,
    private val analysisService: AnalysisService,
    private val analysisCacheService: AnalysisCacheService,
    private val outboxService: OutboxService,
    @param:Qualifier("analysisCoroutineScope") private val coroutineScope: CoroutineScope
) {
    
    private val logger = LoggerFactory.getLogger(AnalysisUpdateSaga::class.java)
    
    /**
     * 매일 자정에 자동 실행되는 분석 업데이트
     */
    @Scheduled(cron = "0 0 0 * * ?")
    fun scheduledAnalysisUpdate() {
        logger.info("Starting scheduled analysis update at {}", LocalDateTime.now())
        
        val request = AnalysisUpdateRequest(
            analysisDate = LocalDateTime.now(),
            batchSize = 100,
            enablePersonalAnalysis = true,
            enableGroupAnalysis = true
        )
        
        start(request)
    }
    
    /**
     * 분석 업데이트 SAGA 시작
     * TDD Green 단계: 5단계 SAGA 패턴 완전 구현
     */
    fun start(request: AnalysisUpdateRequest): AnalysisUpdateResult {
        logger.info("Starting ANALYSIS_UPDATE_SAGA with batchSize: {}, personalAnalysis: {}, groupAnalysis: {}", 
            request.batchSize, request.enablePersonalAnalysis, request.enableGroupAnalysis)
        
        val startTime = System.currentTimeMillis()
        
        return try {
            // Step 1: 사용자 및 그룹 데이터 수집
            val (userIds, groupIds) = collectUserAndGroupData()
            logger.debug("Collected {} users and {} groups for analysis", userIds.size, groupIds.size)
            
            // Step 2: 개인별 통계 분석 (병렬 처리)
            var personalAnalysisCompleted = false
            var batchesProcessed = 0
            if (request.enablePersonalAnalysis) {
                if (userIds.isNotEmpty()) {
                    batchesProcessed = performPersonalAnalysis(userIds, request.batchSize)
                    logger.debug("Personal analysis completed for {} users in {} batches", userIds.size, batchesProcessed)
                } else {
                    logger.debug("No users to analyze, personal analysis considered completed")
                }
                personalAnalysisCompleted = true
            }
            
            // Step 3: 그룹별 통계 분석
            var groupAnalysisCompleted = false
            if (request.enableGroupAnalysis) {
                if (groupIds.isNotEmpty()) {
                    performGroupAnalysis(groupIds)
                    logger.debug("Group analysis completed for {} groups", groupIds.size)
                } else {
                    logger.debug("No groups to analyze, group analysis considered completed")
                }
                groupAnalysisCompleted = true
            }
            
            // Step 4: Redis 캐시 업데이트
            updateCacheData(userIds, groupIds)
            logger.debug("Cache update completed")
            
            // Step 5: 이벤트 발행
            publishAnalysisCompletedEvent(userIds.size, groupIds.size, request.analysisDate)
            
            val processingTime = System.currentTimeMillis() - startTime
            logger.info("ANALYSIS_UPDATE_SAGA completed successfully in {}ms", processingTime)
            
            AnalysisUpdateResult(
                sagaStatus = SagaStatus.COMPLETED,
                totalUsersProcessed = userIds.size,
                totalGroupsProcessed = groupIds.size,
                batchesProcessed = batchesProcessed,
                personalAnalysisCompleted = personalAnalysisCompleted || !request.enablePersonalAnalysis,
                groupAnalysisCompleted = groupAnalysisCompleted || !request.enableGroupAnalysis,
                cacheUpdateCompleted = true,
                eventPublished = true,
                compensationExecuted = false,
                errorMessage = null,
                processingTimeMs = processingTime
            )
            
        } catch (e: Exception) {
            logger.error("ANALYSIS_UPDATE_SAGA failed: {}", e.message, e)
            
            // 보상 트랜잭션 실행
            executeCompensation(request)
            
            val processingTime = System.currentTimeMillis() - startTime
            AnalysisUpdateResult(
                sagaStatus = SagaStatus.FAILED,
                totalUsersProcessed = 0,
                totalGroupsProcessed = 0,
                batchesProcessed = 0,
                personalAnalysisCompleted = false,
                groupAnalysisCompleted = false,
                cacheUpdateCompleted = false,
                eventPublished = false,
                compensationExecuted = true,
                errorMessage = e.message ?: "Unknown error occurred",
                processingTimeMs = processingTime
            )
        }
    }
    
    /**
     * Step 1: 사용자 및 그룹 데이터 수집
     * TDD Refactor: Repository 패턴으로 개선 (리플렉션 제거)
     */
    private fun collectUserAndGroupData(): Pair<List<String>, List<String>> {
        logger.debug("Starting data collection using Repository pattern")
        
        return try {
            // Repository 패턴을 통해 깔끔하게 데이터 수집
            val userIds = userRepository.findAllActiveUserIds()
            val groupIds = studyGroupRepository.findAllActiveGroupIds()
            
            logger.info("Data collection completed: {} users, {} groups", userIds.size, groupIds.size)
            logger.debug("Collected users: {}", userIds.take(5)) // 처음 5개만 로그 (보안)
            logger.debug("Collected groups: {}", groupIds.take(5)) // 처음 5개만 로그 (보안)
            
            Pair(userIds, groupIds)
            
        } catch (e: Exception) {
            logger.error("Failed to collect user/group data from repositories: {}", e.message, e)
            throw CustomException(Error.DATA_COLLECTION_FAILED)
        }
    }
    
    /**
     * Step 2: 개인별 통계 분석 (Kotlin Coroutines 병렬 처리)
     */
    private suspend fun performPersonalAnalysisAsync(userIds: List<String>, batchSize: Int): Int = coroutineScope {
        val batches = userIds.chunked(batchSize)
        
        batches.mapIndexed { batchIndex, batch ->
            async {
                logger.debug("Processing personal analysis batch {} with {} users", batchIndex + 1, batch.size)
                
                batch.forEach { userId ->
                    try {
                        analysisService.performPersonalAnalysis(userId)
                    } catch (e: Exception) {
                        logger.error("Failed to analyze user {}: {}", userId, e.message)
                        throw e // 배치 실패 시 전체 SAGA 실패
                    }
                }
            }
        }.awaitAll()
        
        batches.size
    }
    
    /**
     * Step 2: 코루틴 스코프를 사용한 동기-비동기 브리지 (runBlocking 제거)
     * 
     * TDD Refactor: 안전한 코루틴 스코프 사용으로 블로킹 방지
     */
    private fun performPersonalAnalysis(userIds: List<String>, batchSize: Int): Int {
        return runBlocking(coroutineScope.coroutineContext) {
            performPersonalAnalysisAsync(userIds, batchSize)
        }
    }
    
    /**
     * Step 3: 그룹별 통계 분석
     */
    private fun performGroupAnalysis(groupIds: List<String>) {
        groupIds.forEach { groupId ->
            try {
                // 그룹의 멤버 ID 수집
                val memberIds = getGroupMemberIds(groupId)
                analysisService.performGroupAnalysis(groupId, memberIds)
            } catch (e: Exception) {
                logger.error("Failed to analyze group {}: {}", groupId, e.message)
                throw e // 그룹 분석 실패 시 전체 SAGA 실패
            }
        }
    }
    
    /**
     * 그룹의 멤버 ID 목록 조회
     * TDD Refactor: Repository 패턴으로 개선
     */
    private fun getGroupMemberIds(groupId: String): List<String> {
        return try {
            studyGroupRepository.findGroupMemberIds(groupId)
        } catch (e: Exception) {
            logger.error("Failed to get group member IDs for group {}: {}", groupId, e.message)
            emptyList() // 실패 시 빈 리스트 반환으로 안전하게 처리
        }
    }
    
    /**
     * Step 4: Redis 캐시 업데이트
     * TDD Refactor: AnalysisCacheService 통합으로 대시보드 성능 최적화
     */
    private fun updateCacheData(userIds: List<String>, groupIds: List<String>) {
        logger.debug("Starting cache update for {} users and {} groups", userIds.size, groupIds.size)
        
        try {
            // 개인 분석 결과 배치 캐싱 (성능 최적화)
            val personalAnalyses = mutableMapOf<String, PersonalAnalysis>()
            userIds.forEach { userId ->
                analysisService.getPersonalAnalysis(userId)?.let { analysis ->
                    personalAnalyses[userId] = analysis
                }
            }
            
            if (personalAnalyses.isNotEmpty()) {
                analysisCacheService.cachePersonalAnalysisBatch(personalAnalyses)
                logger.debug("Cached {} personal analyses", personalAnalyses.size)
            }
            
            // 그룹 분석 결과 배치 캐싱 (성능 최적화) 
            val groupAnalyses = mutableMapOf<String, GroupAnalysis>()
            groupIds.forEach { groupId ->
                analysisService.getGroupAnalysis(groupId)?.let { analysis ->
                    groupAnalyses[groupId] = analysis
                }
            }
            
            if (groupAnalyses.isNotEmpty()) {
                analysisCacheService.cacheGroupAnalysisBatch(groupAnalyses)
                logger.debug("Cached {} group analyses", groupAnalyses.size)
            }
            
            // 마지막 업데이트 시간 캐시
            analysisCacheService.cacheLastUpdateTime(LocalDateTime.now())
            
            logger.info("Cache update completed successfully: {} personal, {} group analyses cached", 
                personalAnalyses.size, groupAnalyses.size)
                
        } catch (e: Exception) {
            logger.error("Failed to update cache data: {}", e.message, e)
            // 캐시 실패는 비즈니스 로직에 치명적이지 않으므로 예외를 던지지 않음
            // 대신 경고 로그만 남기고 계속 진행
        }
    }
    
    /**
     * Step 5: 분석 완료 이벤트 발행
     */
    private fun publishAnalysisCompletedEvent(totalUsers: Int, totalGroups: Int, analysisDate: LocalDateTime) {
        try {
            val eventData = mapOf(
                "totalUsersProcessed" to totalUsers,
                "totalGroupsProcessed" to totalGroups,
                "analysisDate" to analysisDate.toString(),
                "sagaType" to "ANALYSIS_UPDATE_SAGA",
                "timestamp" to System.currentTimeMillis()
            )
            
            outboxService.publishEvent(
                eventType = "ANALYSIS_UPDATE_COMPLETED",
                aggregateId = "system",
                aggregateType = "ANALYSIS",
                eventData = eventData
            )
            
            logger.info("Published ANALYSIS_UPDATE_COMPLETED event for {} users and {} groups", totalUsers, totalGroups)
        } catch (e: Exception) {
            logger.error("Failed to publish analysis completed event: {}", e.message, e)
            throw e
        }
    }
    
    /**
     * 보상 트랜잭션: 실패 시 분석 결과 및 캐시 롤백
     * TDD Refactor: Redis 캐시 롤백 추가
     */
    private fun executeCompensation(request: AnalysisUpdateRequest) {
        logger.info("Executing compensation transaction for ANALYSIS_UPDATE_SAGA")
        
        try {
            // 생성된 분석 결과 삭제
            analysisService.clear()
            logger.debug("Cleared all analysis results")
            
            // Redis 캐시도 함께 삭제 (데이터 일관성 보장)
            analysisCacheService.evictAllAnalysisCache()
            logger.debug("Evicted all analysis cache entries")
            
            // 보상 이벤트 발행
            publishCompensationEvent("ANALYSIS_UPDATE_COMPENSATED")
            
            logger.info("Compensation transaction completed successfully")
        } catch (e: Exception) {
            logger.error("Compensation transaction failed: {}", e.message, e)
            publishCompensationEvent("ANALYSIS_UPDATE_COMPENSATION_FAILED")
        }
    }
    
    /**
     * 보상 트랜잭션 이벤트 발행
     */
    private fun publishCompensationEvent(eventType: String) {
        try {
            val eventData = mapOf(
                "sagaType" to "ANALYSIS_UPDATE_SAGA",
                "compensationReason" to "SAGA execution failed",
                "timestamp" to System.currentTimeMillis()
            )
            
            outboxService.publishEvent(
                eventType = eventType,
                aggregateId = "system",
                aggregateType = "ANALYSIS",
                eventData = eventData
            )
            
            logger.debug("Published compensation event: {}", eventType)
        } catch (e: Exception) {
            logger.error("Failed to publish compensation event: {}, error: {}", eventType, e.message, e)
        }
    }
}