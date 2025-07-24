package com.algoreport.collector

import com.algoreport.collector.dto.SubmissionList
import com.algoreport.collector.dto.UserInfo
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.*

/**
 * 대용량 배치 수집 서비스 테스트
 * 
 * TDD Red 단계: 실패하는 테스트를 먼저 작성
 * Task 1-1-4: 대용량 배치 수집 테스트 작성
 */
class DataSyncBatchServiceTest : BehaviorSpec({
    
    given("DataSyncBatchService") {
        val solvedacApiClient = mockk<SolvedacApiClient>()
        val checkpointRepository = mockk<DataSyncCheckpointRepository>()
        val dataSyncBatchService = DataSyncBatchService(solvedacApiClient, checkpointRepository)
        
        `when`("사용자의 과거 6개월 데이터를 배치로 수집할 때") {
            val userId = UUID.randomUUID()
            val handle = "testuser"
            val syncPeriodMonths = 6
            val batchSize = 100
            
            then("배치 작업 계획이 올바르게 수립되어야 한다") {
                val batchPlan = dataSyncBatchService.createBatchPlan(
                    userId = userId,
                    handle = handle,
                    syncPeriodMonths = syncPeriodMonths,
                    batchSize = batchSize
                )
                
                batchPlan shouldNotBe null
                batchPlan.userId shouldBe userId
                batchPlan.handle shouldBe handle
                batchPlan.batchSize shouldBe batchSize
                batchPlan.totalBatches shouldBeGreaterThan 0
                batchPlan.estimatedSubmissions shouldBeGreaterThan 0
            }
        }
        
        `when`("배치별로 데이터를 수집할 때") {
            val syncJobId = UUID.randomUUID()
            val handle = "testuser"
            val batchNumber = 1
            val batchSize = 100
            
            then("100개씩 나누어 수집해야 한다") {
                val result = dataSyncBatchService.collectBatch(
                    syncJobId = syncJobId,
                    handle = handle,
                    batchNumber = batchNumber,
                    batchSize = batchSize
                )
                
                result shouldNotBe null
                result.syncJobId shouldBe syncJobId
                result.batchNumber shouldBe batchNumber
                result.collectedCount shouldBe batchSize
                result.successful shouldBe true
            }
        }
        
        `when`("수집 진행률을 추적할 때") {
            val syncJobId = UUID.randomUUID()
            val currentBatch = 2
            val totalBatches = 5
            
            then("정확한 진행률을 계산해야 한다") {
                val progress = dataSyncBatchService.calculateProgress(
                    syncJobId = syncJobId,
                    currentBatch = currentBatch,
                    totalBatches = totalBatches
                )
                
                progress shouldNotBe null
                progress.syncJobId shouldBe syncJobId
                progress.currentBatch shouldBe currentBatch
                progress.totalBatches shouldBe totalBatches
                progress.progressPercentage shouldBe 40.0 // 2/5 * 100
                progress.isCompleted shouldBe false
            }
        }
        
        `when`("체크포인트를 저장할 때") {
            val syncJobId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val currentBatch = 3
            val totalBatches = 10
            val lastProcessedSubmissionId = 12345L
            val collectedCount = 250
            
            then("체크포인트가 올바르게 저장되어야 한다") {
                val checkpoint = dataSyncBatchService.saveCheckpoint(
                    syncJobId = syncJobId,
                    userId = userId,
                    currentBatch = currentBatch,
                    totalBatches = totalBatches,
                    lastProcessedSubmissionId = lastProcessedSubmissionId,
                    collectedCount = collectedCount
                )
                
                checkpoint shouldNotBe null
                checkpoint.syncJobId shouldBe syncJobId
                checkpoint.userId shouldBe userId
                checkpoint.currentBatch shouldBe currentBatch
                checkpoint.totalBatches shouldBe totalBatches
                checkpoint.lastProcessedSubmissionId shouldBe lastProcessedSubmissionId
                checkpoint.collectedCount shouldBe collectedCount
                checkpoint.canResume shouldBe true
                checkpoint.checkpointAt shouldNotBe null
            }
        }
        
        `when`("Virtual Thread로 병렬 배치 처리를 할 때") {
            val syncJobId = UUID.randomUUID()
            val handle = "testuser"
            val totalBatches = 5
            val batchSize = 100
            
            then("모든 배치가 병렬로 처리되어야 한다") {
                val startTime = System.currentTimeMillis()
                
                val results = dataSyncBatchService.collectBatchesInParallel(
                    syncJobId = syncJobId,
                    handle = handle,
                    totalBatches = totalBatches,
                    batchSize = batchSize
                )
                
                val endTime = System.currentTimeMillis()
                val executionTime = endTime - startTime
                
                results shouldHaveSize totalBatches
                results.all { it.successful } shouldBe true
                
                // Virtual Thread로 병렬 처리하면 순차 처리보다 빨라야 함
                // 순차 처리 예상 시간: 5배치 * 1초 = 5초
                // 병렬 처리 예상 시간: 1초 정도
                executionTime shouldBeLessThan 3000L // 3초 미만
            }
        }
        
        `when`("배치 수집 중 실패가 발생할 때") {
            val syncJobId = UUID.randomUUID()
            val handle = "testuser"
            val batchNumber = 3
            val batchSize = 100
            
            then("실패한 배치 정보가 기록되어야 한다") {
                val result = dataSyncBatchService.collectBatchWithErrorHandling(
                    syncJobId = syncJobId,
                    handle = handle,
                    batchNumber = batchNumber,
                    batchSize = batchSize,
                    simulateError = true
                )
                
                result shouldNotBe null
                result.successful shouldBe false
                result.errorMessage shouldNotBe null
                result.failedAt shouldNotBe null
                result.retryAttempts shouldBe 0
            }
        }
    }
})

/**
 * 배치 계획 데이터 클래스 (TDD Red 단계)
 */
data class BatchPlan(
    val userId: UUID,
    val handle: String,
    val batchSize: Int,
    val totalBatches: Int,
    val estimatedSubmissions: Int,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 배치 수집 결과 데이터 클래스 (TDD Red 단계)
 */
data class BatchCollectionResult(
    val syncJobId: UUID,
    val batchNumber: Int,
    val collectedCount: Int,
    val successful: Boolean,
    val errorMessage: String? = null,
    val failedAt: LocalDateTime? = null,
    val retryAttempts: Int = 0
)

/**
 * 진행률 추적 데이터 클래스 (TDD Red 단계)
 */
data class ProgressTracker(
    val syncJobId: UUID,
    val currentBatch: Int,
    val totalBatches: Int,
    val progressPercentage: Double,
    val isCompleted: Boolean
)

/**
 * 체크포인트 데이터 클래스 (TDD Red 단계)
 */
data class DataSyncCheckpoint(
    val syncJobId: UUID,
    val userId: UUID,
    val currentBatch: Int,
    val totalBatches: Int,
    val lastProcessedSubmissionId: Long,
    val collectedCount: Int,
    val failedAttempts: Int = 0,
    val checkpointAt: LocalDateTime,
    val canResume: Boolean
)