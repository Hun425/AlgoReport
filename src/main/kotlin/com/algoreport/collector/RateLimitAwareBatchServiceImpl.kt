package com.algoreport.collector

import com.algoreport.collector.dto.SubmissionList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * 레이트 리밋 인식 배치 서비스 구현체
 * 
 * TDD Green 단계: 테스트를 통과하기 위한 최소한의 구현
 * Task 1-1-8: 레이트 리밋 처리가 포함된 배치 수집 구현
 */
@Service
class RateLimitAwareBatchServiceImpl(
    private val solvedacApiClient: SolvedacApiClient,
    private val rateLimitHandler: RateLimitHandler
) : RateLimitAwareBatchService {
    
    private val logger = LoggerFactory.getLogger(RateLimitAwareBatchServiceImpl::class.java)
    
    companion object {
        private const val DEFAULT_MAX_ATTEMPTS = 3
        private const val DEFAULT_BASE_DELAY_MS = 1000L // 1초
    }
    
    override fun collectBatchWithRateLimit(
        syncJobId: UUID,
        handle: String,
        batchNumber: Int,
        batchSize: Int
    ): RateLimitAwareBatchResult {
        logger.info("Collecting batch {} for handle: {} with rate limit handling", batchNumber, handle)
        
        val startTime = System.currentTimeMillis()
        
        val retryResult = rateLimitHandler.executeWithRetry(
            maxAttempts = DEFAULT_MAX_ATTEMPTS,
            baseDelayMs = DEFAULT_BASE_DELAY_MS
        ) {
            // solved.ac API 호출
            solvedacApiClient.getSubmissions(handle, batchNumber)
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        
        return if (retryResult.successful && retryResult.result != null) {
            val submissions = retryResult.result as SubmissionList
            val collectedCount = minOf(submissions.items.size, batchSize)
            
            logger.info("Successfully collected batch {} with {} submissions (attempts: {})", 
                       batchNumber, collectedCount, retryResult.totalAttempts)
            
            RateLimitAwareBatchResult(
                syncJobId = syncJobId,
                batchNumber = batchNumber,
                successful = true,
                collectedCount = collectedCount,
                retryAttempts = retryResult.totalAttempts,
                rateLimitHandled = retryResult.totalAttempts > 1, // 재시도가 있었다면 레이트 리밋 처리됨
                totalRetryTimeMs = totalTime,
                errorMessage = null
            )
        } else {
            logger.error("Failed to collect batch {} after {} attempts: {}", 
                        batchNumber, retryResult.totalAttempts, retryResult.lastError?.message)
            
            RateLimitAwareBatchResult(
                syncJobId = syncJobId,
                batchNumber = batchNumber,
                successful = false,
                collectedCount = 0,
                retryAttempts = retryResult.totalAttempts,
                rateLimitHandled = retryResult.totalAttempts > 1,
                totalRetryTimeMs = totalTime,
                errorMessage = retryResult.lastError?.message ?: "Unknown error"
            )
        }
    }
}