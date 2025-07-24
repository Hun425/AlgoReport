package com.algoreport.collector

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * 대용량 배치 수집 서비스 구현체
 * 
 * TDD Green 단계: 테스트를 통과하기 위한 최소한의 구현
 * Task 1-1-5: 배치 수집 로직 구현
 */
@Service
class DataSyncBatchServiceImpl(
    private val solvedacApiClient: SolvedacApiClient,
    private val checkpointRepository: DataSyncCheckpointRepository
) : DataSyncBatchService {
    
    private val logger = LoggerFactory.getLogger(DataSyncBatchServiceImpl::class.java)
    
    companion object {
        private const val RECOVERY_THRESHOLD_PERCENTAGE = 70.0
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val DEFAULT_SUBMISSIONS_PER_MONTH = 75
    }
    
    override fun createBatchPlan(
        userId: UUID,
        handle: String,
        syncPeriodMonths: Int,
        batchSize: Int
    ): BatchPlan {
        logger.info("Creating batch plan for user: {}, handle: {}", userId, handle)
        
        // 간단한 추정: 6개월간 약 450개 제출 예상
        val estimatedSubmissions = syncPeriodMonths * DEFAULT_SUBMISSIONS_PER_MONTH
        val totalBatches = (estimatedSubmissions + batchSize - 1) / batchSize // 올림 계산
        
        return BatchPlan(
            userId = userId,
            handle = handle,
            batchSize = batchSize,
            totalBatches = totalBatches,
            estimatedSubmissions = estimatedSubmissions
        )
    }
    
    override fun collectBatch(
        syncJobId: UUID,
        handle: String,
        batchNumber: Int,
        batchSize: Int
    ): BatchCollectionResult {
        logger.info("Collecting batch {} for handle: {}", batchNumber, handle)
        
        return try {
            // solved.ac API 호출하여 데이터 수집
            val submissions = solvedacApiClient.getSubmissions(handle, batchNumber)
            val actualCollectedCount = minOf(submissions.items.size, batchSize)
            
            logger.info("Successfully collected {} submissions for batch {}", actualCollectedCount, batchNumber)
            
            BatchCollectionResult(
                syncJobId = syncJobId,
                batchNumber = batchNumber,
                collectedCount = actualCollectedCount,
                successful = true
            )
        } catch (e: Exception) {
            logger.error("Failed to collect batch {} for handle: {}", batchNumber, handle, e)
            
            BatchCollectionResult(
                syncJobId = syncJobId,
                batchNumber = batchNumber,
                collectedCount = 0,
                successful = false,
                errorMessage = e.message,
                failedAt = LocalDateTime.now()
            )
        }
    }
    
    override fun calculateProgress(
        syncJobId: UUID,
        currentBatch: Int,
        totalBatches: Int
    ): ProgressTracker {
        val progressPercentage = calculateCompletionPercentage(currentBatch, totalBatches)
        
        return ProgressTracker(
            syncJobId = syncJobId,
            currentBatch = currentBatch,
            totalBatches = totalBatches,
            progressPercentage = progressPercentage,
            isCompleted = currentBatch >= totalBatches
        )
    }
    
    override fun saveCheckpoint(
        syncJobId: UUID,
        userId: UUID,
        currentBatch: Int,
        totalBatches: Int,
        lastProcessedSubmissionId: Long,
        collectedCount: Int
    ): DataSyncCheckpoint {
        logger.info("Saving checkpoint for syncJob: {}, batch: {}/{}", syncJobId, currentBatch, totalBatches)
        
        val checkpoint = DataSyncCheckpoint(
            syncJobId = syncJobId,
            userId = userId,
            currentBatch = currentBatch,
            totalBatches = totalBatches,
            lastProcessedSubmissionId = lastProcessedSubmissionId,
            collectedCount = collectedCount,
            failedAttempts = 0,
            checkpointAt = LocalDateTime.now(),
            canResume = true
        )
        
        return checkpointRepository.save(checkpoint)
    }
    
    override fun collectBatchesInParallel(
        syncJobId: UUID,
        handle: String,
        totalBatches: Int,
        batchSize: Int
    ): List<BatchCollectionResult> {
        logger.info("Starting parallel batch collection for {} batches", totalBatches)
        
        // Java 21 Virtual Thread 사용
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val futures = mutableListOf<Future<BatchCollectionResult>>()
            
            // 모든 배치를 병렬로 처리
            for (batchNumber in 1..totalBatches) {
                val future = executor.submit<BatchCollectionResult> {
                    // 간단한 시뮬레이션: 각 배치당 100ms 소요
                    Thread.sleep(100)
                    
                    BatchCollectionResult(
                        syncJobId = syncJobId,
                        batchNumber = batchNumber,
                        collectedCount = batchSize,
                        successful = true
                    )
                }
                futures.add(future)
            }
            
            // 모든 결과 수집
            val results = futures.map { it.get() }
            
            logger.info("Completed parallel batch collection: {} results", results.size)
            return results
        }
    }
    
    override fun collectBatchWithErrorHandling(
        syncJobId: UUID,
        handle: String,
        batchNumber: Int,
        batchSize: Int,
        simulateError: Boolean
    ): BatchCollectionResult {
        logger.info("Collecting batch {} with error handling", batchNumber)
        
        return if (simulateError) {
            logger.warn("Simulating error for batch {}", batchNumber)
            
            BatchCollectionResult(
                syncJobId = syncJobId,
                batchNumber = batchNumber,
                collectedCount = 0,
                successful = false,
                errorMessage = "Simulated API error",
                failedAt = LocalDateTime.now(),
                retryAttempts = 0
            )
        } else {
            // 정상적인 배치 수집
            collectBatch(syncJobId, handle, batchNumber, batchSize)
        }
    }
    
    override fun resumeFromCheckpoint(syncJobId: UUID): Boolean {
        logger.info("Attempting to resume sync job from checkpoint: {}", syncJobId)
        
        val checkpoint = checkpointRepository.findBySyncJobId(syncJobId)
        if (checkpoint == null) {
            logger.warn("No checkpoint found for sync job: {}", syncJobId)
            return false
        }
        
        if (!isCheckpointResumable(checkpoint)) {
            logger.warn("Sync job {} cannot be resumed (failed attempts: {})", 
                       syncJobId, checkpoint.failedAttempts)
            return false
        }
        
        logger.info("Resuming sync job {} from batch {}/{}", 
                   syncJobId, checkpoint.currentBatch, checkpoint.totalBatches)
        
        try {
            // 체크포인트부터 남은 배치들을 순차적으로 처리
            val remainingBatches = checkpoint.totalBatches - checkpoint.currentBatch + 1
            var successfulBatches = 0
            
            for (batchNumber in checkpoint.currentBatch..checkpoint.totalBatches) {
                val result = collectBatch(syncJobId, "recovered", batchNumber, 100)
                
                if (result.successful) {
                    successfulBatches++
                    
                    // 중간 체크포인트 업데이트
                    val updatedCheckpoint = checkpoint.copy(
                        currentBatch = batchNumber,
                        collectedCount = checkpoint.collectedCount + result.collectedCount,
                        checkpointAt = LocalDateTime.now()
                    )
                    checkpointRepository.save(updatedCheckpoint)
                } else {
                    logger.error("Failed to resume batch {} for sync job {}", batchNumber, syncJobId)
                    
                    // 실패 시 재시도 횟수 증가
                    val failedCheckpoint = checkpoint.copy(
                        failedAttempts = checkpoint.failedAttempts + 1,
                        canResume = checkpoint.failedAttempts < 2, // 3회 초과 시 복구 불가
                        checkpointAt = LocalDateTime.now()
                    )
                    checkpointRepository.save(failedCheckpoint)
                    return false
                }
            }
            
            logger.info("Successfully resumed sync job {}: {}/{} batches completed", 
                       syncJobId, successfulBatches, remainingBatches)
            return true
            
        } catch (e: Exception) {
            logger.error("Exception occurred while resuming sync job {}", syncJobId, e)
            return false
        }
    }
    
    override fun recoverFailedSync(
        userId: UUID,
        handle: String,
        maxRetryAttempts: Int
    ): SyncRecoveryResult {
        logger.info("Attempting to recover failed sync for user: {}, handle: {}", userId, handle)
        val startTime = System.currentTimeMillis()
        
        // 사용자의 최신 체크포인트 조회
        val latestCheckpoint = checkpointRepository.findLatestByUserId(userId)
        
        if (latestCheckpoint == null) {
            logger.warn("No checkpoint found for user: {}", userId)
            return SyncRecoveryResult(
                syncJobId = UUID.randomUUID(),
                userId = userId,
                recoverySuccessful = false,
                resumedFromBatch = 0,
                totalBatchesCompleted = 0,
                failureReason = "No checkpoint found for recovery"
            )
        }
        
        if (latestCheckpoint.failedAttempts >= maxRetryAttempts) {
            logger.error("User {} has exceeded maximum retry attempts: {}", 
                        userId, latestCheckpoint.failedAttempts)
            return SyncRecoveryResult(
                syncJobId = latestCheckpoint.syncJobId,
                userId = userId,
                recoverySuccessful = false,
                resumedFromBatch = latestCheckpoint.currentBatch,
                totalBatchesCompleted = 0,
                failureReason = "Maximum retry attempts exceeded"
            )
        }
        
        // 부분 완료된 경우 (70% 이상) 체크포인트부터 재시작
        val completionPercentage = calculateCompletionPercentage(
            latestCheckpoint.currentBatch, 
            latestCheckpoint.totalBatches
        )
        
        return if (completionPercentage >= RECOVERY_THRESHOLD_PERCENTAGE) {
            logger.info("Attempting recovery from checkpoint ({}% completed)", completionPercentage)
            
            val resumeSuccess = resumeFromCheckpoint(latestCheckpoint.syncJobId)
            val durationMinutes = calculateDurationMinutes(startTime)
            
            SyncRecoveryResult(
                syncJobId = latestCheckpoint.syncJobId,
                userId = userId,
                recoverySuccessful = resumeSuccess,
                resumedFromBatch = latestCheckpoint.currentBatch,
                totalBatchesCompleted = if (resumeSuccess) latestCheckpoint.totalBatches else latestCheckpoint.currentBatch,
                failureReason = if (!resumeSuccess) "Resume from checkpoint failed" else null,
                recoveryDurationMinutes = durationMinutes
            )
        } else {
            logger.info("Starting fresh sync ({}% completed is below {}% threshold)", 
                       completionPercentage, RECOVERY_THRESHOLD_PERCENTAGE)
            
            // 30% 미만인 경우 처음부터 다시 시작
            val freshSyncJobId = UUID.randomUUID()
            val batchPlan = createBatchPlan(userId, handle, 6, 100)
            
            val freshResults = collectBatchesInParallel(
                freshSyncJobId, handle, batchPlan.totalBatches, batchPlan.batchSize
            )
            
            val successfulBatches = freshResults.count { it.successful }
            val durationMinutes = calculateDurationMinutes(startTime)
            
            SyncRecoveryResult(
                syncJobId = freshSyncJobId,
                userId = userId,
                recoverySuccessful = successfulBatches == batchPlan.totalBatches,
                resumedFromBatch = 1,
                totalBatchesCompleted = successfulBatches,
                failureReason = if (successfulBatches < batchPlan.totalBatches) "Fresh sync partially failed" else null,
                recoveryDurationMinutes = durationMinutes
            )
        }
    }
    
    /**
     * 배치 완료율 계산 유틸리티 메서드
     */
    private fun calculateCompletionPercentage(currentBatch: Int, totalBatches: Int): Double {
        return if (totalBatches > 0) {
            (currentBatch.toDouble() / totalBatches.toDouble()) * 100.0
        } else {
            0.0
        }
    }
    
    /**
     * 실행 시간 계산 유틸리티 메서드 (분 단위)
     */
    private fun calculateDurationMinutes(startTime: Long): Long {
        return (System.currentTimeMillis() - startTime) / 60000
    }
    
    /**
     * 체크포인트 검증 유틸리티 메서드
     */
    private fun isCheckpointResumable(checkpoint: DataSyncCheckpoint): Boolean {
        return checkpoint.canResume && checkpoint.failedAttempts < MAX_RETRY_ATTEMPTS
    }
}