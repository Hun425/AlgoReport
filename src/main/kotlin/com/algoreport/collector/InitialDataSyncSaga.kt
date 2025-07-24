package com.algoreport.collector

import com.algoreport.config.outbox.OutboxService
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

/**
 * INITIAL_DATA_SYNC_SAGA 오케스트레이터
 * 
 * TDD Refactor 단계: 모든 컴포넌트를 통합하여 완전한 SAGA 구현
 * Task 1-1-9: 전체 SAGA 최적화
 */
@Service
class InitialDataSyncSaga(
    private val dataSyncBatchService: DataSyncBatchService,
    private val rateLimitAwareBatchService: RateLimitAwareBatchService,
    private val checkpointRepository: DataSyncCheckpointRepository,
    private val outboxService: OutboxService
) {
    
    private val logger = LoggerFactory.getLogger(InitialDataSyncSaga::class.java)
    
    companion object {
        private const val DEFAULT_SYNC_PERIOD_MONTHS = 6
        private const val DEFAULT_BATCH_SIZE = 100
        private const val SAGA_TYPE = "INITIAL_DATA_SYNC_SAGA"
    }
    
    /**
     * SAGA 시작 - 사용자의 solved.ac 계정 연동 시 트리거
     * Kotlin Coroutines를 사용하여 비동기 처리
     */
    suspend fun startSaga(
        userId: UUID,
        handle: String,
        syncPeriodMonths: Int = DEFAULT_SYNC_PERIOD_MONTHS
    ): InitialDataSyncSagaResult {
        val sagaId = UUID.randomUUID()
        val startTime = System.currentTimeMillis()
        
        logger.info("Starting INITIAL_DATA_SYNC_SAGA - sagaId: {}, userId: {}, handle: {}", 
                   sagaId, userId, handle)
        
        try {
            // Step 1: 배치 계획 수립
            val batchPlan = dataSyncBatchService.createBatchPlan(
                userId = userId,
                handle = handle,
                syncPeriodMonths = syncPeriodMonths,
                batchSize = DEFAULT_BATCH_SIZE
            )
            
            // Outbox 이벤트 발행: DATA_SYNC_INITIATED
            publishDataSyncInitiatedEvent(sagaId, userId, handle, batchPlan)
            
            // Step 2: 레이트 리밋 인식 배치 수집 (Virtual Thread 활용)
            val batchResults = executeParallelBatchCollection(
                sagaId = sagaId,
                handle = handle,
                totalBatches = batchPlan.totalBatches
            )
            
            // Step 3: 수집 결과 검증 및 체크포인트 저장
            val (successfulBatches, failedBatches) = validateAndSaveResults(
                sagaId, userId, batchResults, batchPlan
            )
            
            val executionTime = System.currentTimeMillis() - startTime
            
            return if (failedBatches.isEmpty()) {
                // 모든 배치 성공
                publishHistoricalDataCollectedEvent(sagaId, userId, successfulBatches, executionTime)
                
                InitialDataSyncSagaResult(
                    sagaId = sagaId,
                    userId = userId,
                    successful = true,
                    totalBatches = batchPlan.totalBatches,
                    successfulBatches = successfulBatches.size,
                    failedBatches = 0,
                    executionTimeMs = executionTime,
                    sagaStatus = SagaStatus.COMPLETED
                )
            } else {
                // 일부 배치 실패 - 보상 트랜잭션 실행
                executeCompensationTransaction(sagaId, userId, successfulBatches, failedBatches)
                
                InitialDataSyncSagaResult(
                    sagaId = sagaId,
                    userId = userId,
                    successful = false,
                    totalBatches = batchPlan.totalBatches,
                    successfulBatches = successfulBatches.size,
                    failedBatches = failedBatches.size,
                    executionTimeMs = executionTime,
                    sagaStatus = SagaStatus.PARTIALLY_COMPLETED,
                    failureReason = "Some batches failed: ${failedBatches.size}/${batchPlan.totalBatches}"
                )
            }
            
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.error("SAGA failed with exception - sagaId: {}", sagaId, e)
            
            // 완전 실패 시 보상 트랜잭션
            executeEmergencyCompensation(sagaId, userId, e)
            
            return InitialDataSyncSagaResult(
                sagaId = sagaId,
                userId = userId,
                successful = false,
                totalBatches = 0,
                successfulBatches = 0,
                failedBatches = 0,
                executionTimeMs = executionTime,
                sagaStatus = SagaStatus.FAILED,
                failureReason = "SAGA execution failed: ${e.message}"
            )
        }
    }
    
    /**
     * Kotlin Coroutines를 활용한 병렬 배치 수집
     * Virtual Thread보다 메모리 효율적이고 높은 동시성 처리 가능
     */
    private suspend fun executeParallelBatchCollection(
        sagaId: UUID,
        handle: String,
        totalBatches: Int
    ): List<RateLimitAwareBatchResult> = coroutineScope {
        logger.info("Starting parallel batch collection - sagaId: {}, totalBatches: {}", sagaId, totalBatches)
        
        // Kotlin Coroutines를 사용한 병렬 처리 - 수천만 개 동시 처리 가능
        val results = (1..totalBatches).map { batchNumber ->
            async {
                try {
                    logger.debug("Processing batch {}/{} - sagaId: {}", batchNumber, totalBatches, sagaId)
                    
                    rateLimitAwareBatchService.collectBatchWithRateLimit(
                        syncJobId = sagaId,
                        handle = handle,
                        batchNumber = batchNumber,
                        batchSize = DEFAULT_BATCH_SIZE
                    )
                } catch (e: Exception) {
                    logger.error("Batch {} failed - sagaId: {}", batchNumber, sagaId, e)
                    
                    // 실패한 배치 결과 생성
                    RateLimitAwareBatchResult(
                        syncJobId = sagaId,
                        batchNumber = batchNumber,
                        successful = false,
                        collectedCount = 0,
                        retryAttempts = 1,
                        rateLimitHandled = false,
                        totalRetryTimeMs = 0L,
                        errorMessage = e.message ?: "Unknown error"
                    )
                }
            }
        }.awaitAll()
        
        logger.info("Parallel batch collection completed - sagaId: {}, successful: {}, failed: {}", 
                   sagaId, results.count { it.successful }, results.count { !it.successful })
        
        results
    }
    
    /**
     * 수집 결과 검증 및 체크포인트 저장
     */
    private fun validateAndSaveResults(
        sagaId: UUID,
        userId: UUID,
        batchResults: List<RateLimitAwareBatchResult>,
        batchPlan: BatchPlan
    ): Pair<List<RateLimitAwareBatchResult>, List<RateLimitAwareBatchResult>> {
        val successfulBatches = batchResults.filter { it.successful }
        val failedBatches = batchResults.filter { !it.successful }
        
        logger.info("Batch results - sagaId: {}, successful: {}, failed: {}", 
                   sagaId, successfulBatches.size, failedBatches.size)
        
        // 최종 체크포인트 저장
        if (successfulBatches.isNotEmpty()) {
            val checkpoint = DataSyncCheckpoint(
                syncJobId = sagaId,
                userId = userId,
                currentBatch = successfulBatches.size,
                totalBatches = batchPlan.totalBatches,
                lastProcessedSubmissionId = successfulBatches.maxOf { it.batchNumber }.toLong(),
                collectedCount = successfulBatches.sumOf { it.collectedCount },
                failedAttempts = failedBatches.size,
                checkpointAt = LocalDateTime.now(),
                canResume = failedBatches.isNotEmpty() && failedBatches.size < batchPlan.totalBatches / 2
            )
            
            checkpointRepository.save(checkpoint)
            logger.info("Final checkpoint saved - sagaId: {}, progress: {}/{}", 
                       sagaId, checkpoint.currentBatch, checkpoint.totalBatches)
        }
        
        return Pair(successfulBatches, failedBatches)
    }
    
    /**
     * 보상 트랜잭션 실행 (부분 실패 시)
     */
    private fun executeCompensationTransaction(
        sagaId: UUID,
        userId: UUID,
        successfulBatches: List<RateLimitAwareBatchResult>,
        failedBatches: List<RateLimitAwareBatchResult>
    ) {
        logger.warn("Executing compensation transaction - sagaId: {}, successful: {}, failed: {}", 
                   sagaId, successfulBatches.size, failedBatches.size)
        
        try {
            // 70% 이상 성공한 경우 부분 완료로 처리
            val successRate = successfulBatches.size.toDouble() / (successfulBatches.size + failedBatches.size)
            
            if (successRate >= 0.7) {
                // 부분 완료 이벤트 발행
                publishPartialCompletionEvent(sagaId, userId, successfulBatches, failedBatches)
                logger.info("Partial completion processed - sagaId: {}, success rate: {:.2f}", sagaId, successRate)
            } else {
                // 성공률이 낮으면 수집된 데이터 정리
                publishDataCleanupEvent(sagaId, userId, successfulBatches)
                logger.warn("Data cleanup initiated due to low success rate - sagaId: {}, success rate: {:.2f}", 
                           sagaId, successRate)
            }
            
        } catch (e: Exception) {
            logger.error("Compensation transaction failed - sagaId: {}", sagaId, e)
            publishCompensationFailedEvent(sagaId, userId, e)
        }
    }
    
    /**
     * 긴급 보상 트랜잭션 (완전 실패 시)
     */
    private fun executeEmergencyCompensation(sagaId: UUID, userId: UUID, error: Exception) {
        logger.error("Executing emergency compensation - sagaId: {}", sagaId, error)
        
        try {
            // 생성된 모든 데이터 정리
            publishEmergencyCleanupEvent(sagaId, userId, error)
            
            // 사용자에게 실패 알림
            publishSagaFailedEvent(sagaId, userId, error)
            
        } catch (e: Exception) {
            logger.error("Emergency compensation failed - sagaId: {}", sagaId, e)
        }
    }
    
    /**
     * SAGA 재시작 (체크포인트부터)
     */
    fun resumeSaga(sagaId: UUID): InitialDataSyncSagaResult? {
        logger.info("Attempting to resume SAGA - sagaId: {}", sagaId)
        
        val checkpoint = checkpointRepository.findBySyncJobId(sagaId)
        if (checkpoint == null) {
            logger.warn("No checkpoint found for SAGA - sagaId: {}", sagaId)
            return null
        }
        
        if (!checkpoint.canResume) {
            logger.warn("SAGA cannot be resumed - sagaId: {}", sagaId)
            return null
        }
        
        // 기존 SAGA와 동일하지만 체크포인트부터 시작
        val recoveryResult = dataSyncBatchService.recoverFailedSync(
            userId = checkpoint.userId,
            handle = "recovered", // TODO: handle을 체크포인트에서 가져와야 함
            maxRetryAttempts = 3
        )
        
        return InitialDataSyncSagaResult(
            sagaId = sagaId,
            userId = checkpoint.userId,
            successful = recoveryResult.recoverySuccessful,
            totalBatches = checkpoint.totalBatches,
            successfulBatches = recoveryResult.totalBatchesCompleted,
            failedBatches = checkpoint.totalBatches - recoveryResult.totalBatchesCompleted,
            executionTimeMs = recoveryResult.recoveryDurationMinutes * 60000,
            sagaStatus = if (recoveryResult.recoverySuccessful) SagaStatus.RECOVERED else SagaStatus.FAILED,
            failureReason = recoveryResult.failureReason
        )
    }
    
    // 이벤트 발행 메서드들 (간단한 구현)
    private fun publishDataSyncInitiatedEvent(sagaId: UUID, userId: UUID, handle: String, batchPlan: BatchPlan) {
        val eventData = mapOf(
            "sagaId" to sagaId.toString(),
            "userId" to userId.toString(),
            "handle" to handle,
            "totalBatches" to batchPlan.totalBatches,
            "estimatedSubmissions" to batchPlan.estimatedSubmissions
        )
        
        outboxService.publishEvent(
            aggregateType = "SYNC_JOB",
            aggregateId = "sync-job-$sagaId",
            eventType = "DATA_SYNC_INITIATED",
            eventData = eventData
        )
    }
    
    private fun publishHistoricalDataCollectedEvent(
        sagaId: UUID, 
        userId: UUID, 
        successfulBatches: List<RateLimitAwareBatchResult>,
        executionTime: Long
    ) {
        val eventData = mapOf(
            "sagaId" to sagaId.toString(),
            "userId" to userId.toString(),
            "collectedBatches" to successfulBatches.size,
            "totalSubmissions" to successfulBatches.sumOf { it.collectedCount },
            "executionTimeMs" to executionTime
        )
        
        outboxService.publishEvent(
            aggregateType = "SYNC_JOB",
            aggregateId = "sync-job-$sagaId",
            eventType = "HISTORICAL_DATA_COLLECTED",
            eventData = eventData
        )
    }
    
    private fun publishPartialCompletionEvent(
        sagaId: UUID,
        userId: UUID,
        successfulBatches: List<RateLimitAwareBatchResult>,
        failedBatches: List<RateLimitAwareBatchResult>
    ) {
        val eventData = mapOf(
            "sagaId" to sagaId.toString(),
            "userId" to userId.toString(),
            "successfulBatches" to successfulBatches.size,
            "failedBatches" to failedBatches.size,
            "compensationType" to "PARTIAL_COMPLETION"
        )
        
        outboxService.publishEvent(
            aggregateType = "SYNC_JOB",
            aggregateId = "sync-job-$sagaId",
            eventType = "DATA_SYNC_PARTIALLY_COMPLETED",
            eventData = eventData
        )
    }
    
    private fun publishDataCleanupEvent(
        sagaId: UUID,
        userId: UUID,
        successfulBatches: List<RateLimitAwareBatchResult>
    ) {
        val eventData = mapOf(
            "sagaId" to sagaId.toString(),
            "userId" to userId.toString(),
            "batchesToCleanup" to successfulBatches.size,
            "compensationType" to "DATA_CLEANUP"
        )
        
        outboxService.publishEvent(
            aggregateType = "SYNC_JOB",
            aggregateId = "sync-job-$sagaId",
            eventType = "DATA_SYNC_CLEANUP_INITIATED",
            eventData = eventData
        )
    }
    
    private fun publishCompensationFailedEvent(sagaId: UUID, userId: UUID, error: Exception) {
        val eventData = mapOf(
            "sagaId" to sagaId.toString(),
            "userId" to userId.toString(),
            "errorMessage" to (error.message ?: "Unknown error"),
            "compensationType" to "COMPENSATION_FAILED"
        )
        
        outboxService.publishEvent(
            aggregateType = "SYNC_JOB",
            aggregateId = "sync-job-$sagaId",
            eventType = "COMPENSATION_TRANSACTION_FAILED",
            eventData = eventData
        )
    }
    
    private fun publishEmergencyCleanupEvent(sagaId: UUID, userId: UUID, error: Exception) {
        val eventData = mapOf(
            "sagaId" to sagaId.toString(),
            "userId" to userId.toString(),
            "errorMessage" to (error.message ?: "Unknown error"),
            "compensationType" to "EMERGENCY_CLEANUP"
        )
        
        outboxService.publishEvent(
            aggregateType = "SYNC_JOB",
            aggregateId = "sync-job-$sagaId",
            eventType = "EMERGENCY_CLEANUP_INITIATED",
            eventData = eventData
        )
    }
    
    private fun publishSagaFailedEvent(sagaId: UUID, userId: UUID, error: Exception) {
        val eventData = mapOf(
            "sagaId" to sagaId.toString(),
            "userId" to userId.toString(),
            "errorMessage" to (error.message ?: "Unknown error"),
            "sagaStatus" to "FAILED"
        )
        
        outboxService.publishEvent(
            aggregateType = "SYNC_JOB",
            aggregateId = "sync-job-$sagaId",
            eventType = "INITIAL_DATA_SYNC_SAGA_FAILED",
            eventData = eventData
        )
    }
}

/**
 * INITIAL_DATA_SYNC_SAGA 실행 결과
 */
data class InitialDataSyncSagaResult(
    val sagaId: UUID,
    val userId: UUID,
    val successful: Boolean,
    val totalBatches: Int,
    val successfulBatches: Int,
    val failedBatches: Int,
    val executionTimeMs: Long,
    val sagaStatus: SagaStatus,
    val failureReason: String? = null
)

/**
 * SAGA 상태 열거형
 */
enum class SagaStatus {
    INITIATED,
    IN_PROGRESS,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED,
    COMPENSATED,
    RECOVERED
}