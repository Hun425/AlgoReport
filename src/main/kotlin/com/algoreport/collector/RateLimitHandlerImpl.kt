package com.algoreport.collector

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 레이트 리밋 처리 구현체
 * 
 * TDD Green 단계: 테스트를 통과하기 위한 최소한의 구현
 * Task 1-1-8: Resilience4j 기반 재시도 로직 구현
 */
@Service
class RateLimitHandlerImpl(
    private val solvedacApiClient: SolvedacApiClient
) : RateLimitHandler {
    
    private val logger = LoggerFactory.getLogger(RateLimitHandlerImpl::class.java)
    
    override fun <T> detectRateLimit(operation: () -> T): RateLimitDetectionResult {
        return try {
            operation()
            
            // 성공한 경우 - 레이트 리밋 없음
            RateLimitDetectionResult(
                rateLimitDetected = false,
                errorType = RateLimitErrorType.API_RATE_LIMIT,
                detectedAt = LocalDateTime.now()
            )
        } catch (e: CustomException) {
            when (e.error) {
                Error.SOLVEDAC_RATE_LIMIT_EXCEEDED -> {
                    logger.warn("Rate limit detected: {}", e.message)
                    RateLimitDetectionResult(
                        rateLimitDetected = true,
                        errorType = RateLimitErrorType.API_RATE_LIMIT,
                        detectedAt = LocalDateTime.now(),
                        retryAfterSeconds = 60L // 기본 1분 대기
                    )
                }
                Error.API_CONCURRENT_LIMIT_EXCEEDED -> {
                    logger.warn("Concurrent limit detected: {}", e.message)
                    RateLimitDetectionResult(
                        rateLimitDetected = true,
                        errorType = RateLimitErrorType.CONCURRENT_LIMIT,
                        detectedAt = LocalDateTime.now(),
                        retryAfterSeconds = 30L // 30초 대기
                    )
                }
                Error.DAILY_QUOTA_EXCEEDED -> {
                    logger.error("Daily quota exceeded: {}", e.message)
                    RateLimitDetectionResult(
                        rateLimitDetected = true,
                        errorType = RateLimitErrorType.DAILY_QUOTA_EXCEEDED,
                        detectedAt = LocalDateTime.now(),
                        retryAfterSeconds = 24 * 60 * 60L // 24시간 대기
                    )
                }
                else -> throw e // 다른 에러는 그대로 전파
            }
        }
    }
    
    override fun <T> executeWithRetry(
        maxAttempts: Int,
        baseDelayMs: Long,
        operation: () -> T
    ): RetryExecutionResult<T> {
        val startTime = System.currentTimeMillis()
        var lastError: Throwable? = null
        var attempts = 0
        
        val retryConfig = RetryConfig(
            maxAttempts = maxAttempts,
            baseDelayMs = baseDelayMs,
            maxDelayMs = 60000L, // 1분 최대
            backoffMultiplier = 2.0
        )
        
        val backoffCalculator = ExponentialBackoffCalculatorImpl(retryConfig)
        
        for (attempt in 1..maxAttempts) {
            attempts = attempt
            
            try {
                logger.debug("Executing operation, attempt: {}/{}", attempt, maxAttempts)
                val result = operation()
                
                val totalTime = System.currentTimeMillis() - startTime
                logger.info("Operation succeeded on attempt {}/{}, total time: {}ms", 
                           attempt, maxAttempts, totalTime)
                
                return RetryExecutionResult(
                    successful = true,
                    result = result,
                    totalAttempts = attempt,
                    finalAttemptSuccessful = true,
                    totalRetryTimeMs = totalTime,
                    lastError = null
                )
                
            } catch (e: CustomException) {
                lastError = e
                logger.warn("Attempt {}/{} failed: {}", attempt, maxAttempts, e.message)
                
                // 레이트 리밋 관련 에러만 재시도
                if (!isRetriableError(e)) {
                    logger.error("Non-retriable error encountered, giving up: {}", e.message)
                    break
                }
                
                // 마지막 시도가 아니면 대기
                if (attempt < maxAttempts) {
                    val delayMs = backoffCalculator.calculateDelay(attempt)
                    logger.info("Waiting {}ms before retry (attempt {}/{})", delayMs, attempt, maxAttempts)
                    
                    try {
                        Thread.sleep(delayMs)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        logger.warn("Interrupted while waiting for retry")
                        break
                    }
                }
            } catch (e: Exception) {
                lastError = e
                logger.error("Unexpected error on attempt {}/{}: {}", attempt, maxAttempts, e.message, e)
                break
            }
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        logger.error("Operation failed after {} attempts, total time: {}ms", attempts, totalTime)
        
        return RetryExecutionResult(
            successful = false,
            result = null,
            totalAttempts = attempts,
            finalAttemptSuccessful = false,
            totalRetryTimeMs = totalTime,
            lastError = lastError
        )
    }
    
    /**
     * 재시도 가능한 에러인지 판단
     */
    private fun isRetriableError(e: CustomException): Boolean {
        return when (e.error) {
            Error.SOLVEDAC_RATE_LIMIT_EXCEEDED,
            Error.API_CONCURRENT_LIMIT_EXCEEDED -> true
            
            Error.DAILY_QUOTA_EXCEEDED -> false // 일일 할당량 초과는 재시도 불가
            
            else -> false // 기타 에러는 재시도 불가
        }
    }
}