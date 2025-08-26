package com.algoreport.collector

import com.algoreport.collector.dto.SubmissionList
import com.algoreport.collector.dto.UserInfo
import com.algoreport.collector.dto.Submission
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.mockk.mockk
import io.mockk.every
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
        val dataSyncBatchService = DataSyncBatchServiceImpl(solvedacApiClient, checkpointRepository)
        
        // Mock 설정
        every { solvedacApiClient.getSubmissions(any(), any()) } returns SubmissionList(
            count = 100,
            items = (1..100).map { 
                Submission(
                    submissionId = it.toLong(),
                    problem = mockk(),
                    user = mockk(),
                    timestamp = LocalDateTime.now(),
                    result = "맞았습니다!!",
                    language = "Kotlin",
                    codeLength = 500
                )
            }
        )
        
        every { checkpointRepository.save(any()) } returnsArgument 0
        
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
        
        `when`("체크포인트로부터 배치 수집을 재시작할 때") {
            val syncJobId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            
            // 체크포인트 Mock 설정
            val checkpoint = DataSyncCheckpoint(
                syncJobId = syncJobId,
                userId = userId,
                currentBatch = 3,
                totalBatches = 5,
                lastProcessedSubmissionId = 12345L,
                collectedCount = 200,
                failedAttempts = 1,
                checkpointAt = LocalDateTime.now(),
                canResume = true
            )
            
            every { checkpointRepository.findBySyncJobId(syncJobId) } returns checkpoint
            
            then("체크포인트부터 재시작되어야 한다") {
                val resumeSuccess = dataSyncBatchService.resumeFromCheckpoint(syncJobId)
                
                resumeSuccess shouldBe true
            }
        }
        
        `when`("실패한 동기화 작업을 복구할 때") {
            val userId = UUID.randomUUID()
            val handle = "testuser"
            
            // 70% 완료된 체크포인트 Mock 설정
            val checkpoint = DataSyncCheckpoint(
                syncJobId = UUID.randomUUID(),
                userId = userId,
                currentBatch = 7, // 70% 완료 (7/10)
                totalBatches = 10,
                lastProcessedSubmissionId = 67890L,
                collectedCount = 700,
                failedAttempts = 1,
                checkpointAt = LocalDateTime.now(),
                canResume = true
            )
            
            every { checkpointRepository.findTopByUserIdOrderByCheckpointAtDesc(userId) } returns checkpoint
            every { checkpointRepository.findBySyncJobId(checkpoint.syncJobId) } returns checkpoint
            every { checkpointRepository.save(any()) } returnsArgument 0
            
            then("70% 이상 완료된 경우 체크포인트부터 복구되어야 한다") {
                val recoveryResult = dataSyncBatchService.recoverFailedSync(
                    userId = userId,
                    handle = handle,
                    maxRetryAttempts = 3
                )
                
                recoveryResult shouldNotBe null
                recoveryResult.userId shouldBe userId
                recoveryResult.resumedFromBatch shouldBe 7
                recoveryResult.recoverySuccessful shouldBe true
            }
        }
        
        `when`("30% 미만 완료된 동기화 작업을 복구할 때") {
            val userId = UUID.randomUUID()
            val handle = "testuser"
            
            // 20% 완료된 체크포인트 Mock 설정
            val checkpoint = DataSyncCheckpoint(
                syncJobId = UUID.randomUUID(),
                userId = userId,
                currentBatch = 2, // 20% 완료 (2/10)
                totalBatches = 10,
                lastProcessedSubmissionId = 12345L,
                collectedCount = 200,
                failedAttempts = 1,
                checkpointAt = LocalDateTime.now(),
                canResume = true
            )
            
            every { checkpointRepository.findTopByUserIdOrderByCheckpointAtDesc(userId) } returns checkpoint
            
            then("처음부터 새로 시작되어야 한다") {
                val recoveryResult = dataSyncBatchService.recoverFailedSync(
                    userId = userId,
                    handle = handle,
                    maxRetryAttempts = 3
                )
                
                recoveryResult shouldNotBe null
                recoveryResult.userId shouldBe userId
                recoveryResult.resumedFromBatch shouldBe 1 // 처음부터 시작
                recoveryResult.recoverySuccessful shouldBe true
            }
        }
        
        `when`("체크포인트가 복구 불가능한 상태일 때") {
            val syncJobId = UUID.randomUUID()
            
            // 복구 불가능한 체크포인트 Mock 설정
            val checkpoint = DataSyncCheckpoint(
                syncJobId = syncJobId,
                userId = UUID.randomUUID(),
                currentBatch = 3,
                totalBatches = 5,
                lastProcessedSubmissionId = 12345L,
                collectedCount = 200,
                failedAttempts = 5, // 최대 재시도 횟수 초과
                checkpointAt = LocalDateTime.now().minusDays(30), // 30일 전 체크포인트
                canResume = false // 복구 불가능
            )
            
            every { checkpointRepository.findBySyncJobId(syncJobId) } returns checkpoint
            
            then("복구가 실패해야 한다") {
                val resumeSuccess = dataSyncBatchService.resumeFromCheckpoint(syncJobId)
                
                resumeSuccess shouldBe false
            }
        }
        
        `when`("체크포인트가 존재하지 않을 때") {
            val syncJobId = UUID.randomUUID()
            
            every { checkpointRepository.findBySyncJobId(syncJobId) } returns null
            
            then("복구가 실패해야 한다") {
                val resumeSuccess = dataSyncBatchService.resumeFromCheckpoint(syncJobId)
                
                resumeSuccess shouldBe false
            }
        }
        
        `when`("완료된 작업의 진행률을 계산할 때") {
            val syncJobId = UUID.randomUUID()
            val currentBatch = 5 // 완료됨
            val totalBatches = 5
            
            then("100% 완료로 표시되어야 한다") {
                val progress = dataSyncBatchService.calculateProgress(
                    syncJobId = syncJobId,
                    currentBatch = currentBatch,
                    totalBatches = totalBatches
                )
                
                progress.progressPercentage shouldBe 100.0
                progress.isCompleted shouldBe true
            }
        }
        
        
        `when`("재시도 횟수가 초과된 작업을 복구할 때") {
            val userId = UUID.randomUUID()
            val handle = "testuser"
            
            // 재시도 횟수 초과된 체크포인트 Mock 설정
            val checkpoint = DataSyncCheckpoint(
                syncJobId = UUID.randomUUID(),
                userId = userId,
                currentBatch = 5,
                totalBatches = 10,
                lastProcessedSubmissionId = 12345L,
                collectedCount = 500,
                failedAttempts = 5, // 최대 재시도(3) 초과
                checkpointAt = LocalDateTime.now(),
                canResume = true
            )
            
            every { checkpointRepository.findTopByUserIdOrderByCheckpointAtDesc(userId) } returns checkpoint
            
            then("복구가 실패해야 한다") {
                val recoveryResult = dataSyncBatchService.recoverFailedSync(
                    userId = userId,
                    handle = handle,
                    maxRetryAttempts = 3
                )
                
                recoveryResult shouldNotBe null
                recoveryResult.userId shouldBe userId
                recoveryResult.recoverySuccessful shouldBe false
                recoveryResult.failureReason shouldNotBe null
            }
        }
        
        `when`("복구할 체크포인트가 없을 때") {
            val userId = UUID.randomUUID()
            val handle = "testuser"
            
            every { checkpointRepository.findTopByUserIdOrderByCheckpointAtDesc(userId) } returns null
            
            then("복구가 실패해야 한다") {
                val recoveryResult = dataSyncBatchService.recoverFailedSync(
                    userId = userId,
                    handle = handle,
                    maxRetryAttempts = 3
                )
                
                recoveryResult shouldNotBe null
                recoveryResult.userId shouldBe userId
                recoveryResult.resumedFromBatch shouldBe 0 // 체크포인트 없음
                recoveryResult.recoverySuccessful shouldBe false
                recoveryResult.failureReason shouldBe "No checkpoint found for recovery"
            }
        }
        
        `when`("정상적으로 배치 수집할 때 (에러 없음)") {
            val syncJobId = UUID.randomUUID()
            val handle = "testuser"
            val batchNumber = 1
            val batchSize = 100
            
            then("성공적으로 수집되어야 한다") {
                val result = dataSyncBatchService.collectBatchWithErrorHandling(
                    syncJobId = syncJobId,
                    handle = handle,
                    batchNumber = batchNumber,
                    batchSize = batchSize,
                    simulateError = false // 에러 없음
                )
                
                result shouldNotBe null
                result.successful shouldBe true
                result.errorMessage shouldBe null
                result.failedAt shouldBe null
            }
        }
    }
})

