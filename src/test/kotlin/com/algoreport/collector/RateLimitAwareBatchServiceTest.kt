package com.algoreport.collector

import com.algoreport.collector.dto.SubmissionList
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import java.util.*

/**
 * RateLimitAwareBatchService 테스트
 * 
 * 브랜치 커버리지 개선을 위한 포괄적 테스트
 */
class RateLimitAwareBatchServiceTest : BehaviorSpec({
    
    given("RateLimitAwareBatchServiceImpl") {
        val mockSolvedacApiClient = mockk<SolvedacApiClient>()
        val mockRateLimitHandler = mockk<RateLimitHandler>()
        
        val service = RateLimitAwareBatchServiceImpl(
            solvedacApiClient = mockSolvedacApiClient,
            rateLimitHandler = mockRateLimitHandler
        )
        
        `when`("성공적으로 배치를 수집할 때") {
            val syncJobId = UUID.randomUUID()
            val handle = "testuser"
            val batchNumber = 1
            val batchSize = 100
            
            val submissionList = SubmissionList(
                count = 50,
                items = emptyList()
            )
            
            val successfulRetryResult = RetryExecutionResult(
                successful = true,
                result = submissionList,
                totalAttempts = 1,
                finalAttemptSuccessful = true,
                totalRetryTimeMs = 1000L,
                lastError = null
            )
            
            then("올바른 RateLimitAwareBatchResult를 반환해야 한다") {
                every { 
                    mockRateLimitHandler.executeWithRetry(3, 1000L, any<() -> SubmissionList>()) 
                } returns successfulRetryResult
                
                val result = service.collectBatchWithRateLimit(syncJobId, handle, batchNumber, batchSize)
                
                result.syncJobId shouldBe syncJobId
                result.batchNumber shouldBe batchNumber
                result.successful shouldBe true
                result.collectedCount shouldBe 50
                result.retryAttempts shouldBe 1
                result.rateLimitHandled shouldBe false // 재시도 없었으므로
                result.errorMessage shouldBe null
                
                verify(exactly = 1) { 
                    mockRateLimitHandler.executeWithRetry(3, 1000L, any<() -> SubmissionList>()) 
                }
            }
        }
        
        `when`("레이트 리밋으로 인한 재시도 후 성공할 때") {
            val syncJobId = UUID.randomUUID()
            val handle = "testuser"
            val batchNumber = 2
            val batchSize = 100
            
            val submissionList = SubmissionList(
                count = 75,
                items = emptyList()
            )
            
            val retryResult = RetryExecutionResult(
                successful = true,
                result = submissionList,
                totalAttempts = 2, // 재시도 발생
                finalAttemptSuccessful = true,
                totalRetryTimeMs = 3000L,
                lastError = null
            )
            
            then("rateLimitHandled가 true로 설정되어야 한다") {
                every { 
                    mockRateLimitHandler.executeWithRetry(3, 1000L, any<() -> SubmissionList>()) 
                } returns retryResult
                
                val result = service.collectBatchWithRateLimit(syncJobId, handle, batchNumber, batchSize)
                
                result.successful shouldBe true
                result.collectedCount shouldBe 75
                result.retryAttempts shouldBe 2
                result.rateLimitHandled shouldBe true // 재시도가 있었으므로
                result.totalRetryTimeMs shouldNotBe null
            }
        }
        
        `when`("수집된 아이템 수가 배치 사이즈보다 클 때") {
            val syncJobId = UUID.randomUUID()
            val handle = "testuser"
            val batchNumber = 1
            val batchSize = 50 // 작은 배치 사이즈
            
            val submissionList = SubmissionList(
                count = 100,
                items = List(100) { mockk() } // 100개 아이템
            )
            
            val successfulRetryResult = RetryExecutionResult(
                successful = true,
                result = submissionList,
                totalAttempts = 1,
                finalAttemptSuccessful = true,
                totalRetryTimeMs = 1000L,
                lastError = null
            )
            
            then("collectedCount가 배치 사이즈로 제한되어야 한다") {
                every { 
                    mockRateLimitHandler.executeWithRetry(3, 1000L, any<() -> SubmissionList>()) 
                } returns successfulRetryResult
                
                val result = service.collectBatchWithRateLimit(syncJobId, handle, batchNumber, batchSize)
                
                result.successful shouldBe true
                result.collectedCount shouldBe 50 // batchSize로 제한됨
            }
        }
        
        `when`("모든 재시도가 실패할 때") {
            val syncJobId = UUID.randomUUID()
            val handle = "testuser"
            val batchNumber = 3
            val batchSize = 100
            
            val failedRetryResult = RetryExecutionResult<SubmissionList>(
                successful = false,
                result = null,
                totalAttempts = 3,
                finalAttemptSuccessful = false,
                totalRetryTimeMs = 7000L,
                lastError = RuntimeException("API rate limit exceeded")
            )
            
            then("실패한 RateLimitAwareBatchResult를 반환해야 한다") {
                every { 
                    mockRateLimitHandler.executeWithRetry(3, 1000L, any<() -> SubmissionList>()) 
                } returns failedRetryResult
                
                val result = service.collectBatchWithRateLimit(syncJobId, handle, batchNumber, batchSize)
                
                result.syncJobId shouldBe syncJobId
                result.batchNumber shouldBe batchNumber
                result.successful shouldBe false
                result.collectedCount shouldBe 0
                result.retryAttempts shouldBe 3
                result.rateLimitHandled shouldBe true // 재시도가 있었으므로
                result.totalRetryTimeMs shouldNotBe null // 실제 측정된 시간 확인
                result.errorMessage shouldBe "API rate limit exceeded"
            }
        }
        
        `when`("예외가 발생했지만 에러 메시지가 null일 때") {
            val syncJobId = UUID.randomUUID()
            val handle = "testuser"
            val batchNumber = 4
            val batchSize = 100
            
            val failedRetryResult = RetryExecutionResult<SubmissionList>(
                successful = false,
                result = null,
                totalAttempts = 2,
                finalAttemptSuccessful = false,
                totalRetryTimeMs = 3000L,
                lastError = null // null 에러
            )
            
            then("Unknown error 메시지를 반환해야 한다") {
                every { 
                    mockRateLimitHandler.executeWithRetry(3, 1000L, any<() -> SubmissionList>()) 
                } returns failedRetryResult
                
                val result = service.collectBatchWithRateLimit(syncJobId, handle, batchNumber, batchSize)
                
                result.successful shouldBe false
                result.errorMessage shouldBe "Unknown error"
            }
        }
        
        `when`("첫 번째 시도에서 실패할 때") {
            val syncJobId = UUID.randomUUID()
            val handle = "testuser"
            val batchNumber = 5
            val batchSize = 100
            
            val failedRetryResult = RetryExecutionResult<SubmissionList>(
                successful = false,
                result = null,
                totalAttempts = 1, // 첫 번째 시도에서 실패
                finalAttemptSuccessful = false,
                totalRetryTimeMs = 500L,
                lastError = RuntimeException("Connection timeout")
            )
            
            then("rateLimitHandled가 false여야 한다") {
                every { 
                    mockRateLimitHandler.executeWithRetry(3, 1000L, any<() -> SubmissionList>()) 
                } returns failedRetryResult
                
                val result = service.collectBatchWithRateLimit(syncJobId, handle, batchNumber, batchSize)
                
                result.successful shouldBe false
                result.retryAttempts shouldBe 1
                result.rateLimitHandled shouldBe false // 재시도가 없었으므로
                result.errorMessage shouldBe "Connection timeout"
            }
        }
    }
})