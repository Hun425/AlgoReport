package com.algoreport.collector

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import java.time.LocalDateTime
import java.util.*

/**
 * 레이트 리밋 처리 테스트
 * 
 * TDD Red 단계: 실패하는 테스트를 먼저 작성
 * Task 1-1-7: 레이트 리밋 처리 테스트
 */
class RateLimitHandlerTest : BehaviorSpec({
    
    given("RateLimitHandler") {
        val solvedacApiClient = mockk<SolvedacApiClient>()
        val rateLimitHandler = RateLimitHandlerImpl(solvedacApiClient)
        
        `when`("API 레이트 리밋에 도달했을 때") {
            val handle = "testuser"
            
            // solved.ac API에서 429 Too Many Requests 에러 시뮬레이션
            every { solvedacApiClient.getSubmissions(handle, any()) } throws 
                CustomException(Error.SOLVEDAC_RATE_LIMIT_EXCEEDED)
            
            then("레이트 리밋 감지가 되어야 한다") {
                val rateLimitResult = rateLimitHandler.detectRateLimit {
                    solvedacApiClient.getSubmissions(handle, 1)
                }
                
                rateLimitResult shouldNotBe null
                rateLimitResult.rateLimitDetected shouldBe true
                rateLimitResult.errorType shouldBe RateLimitErrorType.API_RATE_LIMIT
                rateLimitResult.detectedAt shouldNotBe null
            }
        }
        
        `when`("지수 백오프로 재시도할 때") {
            val retryConfig = RetryConfig(
                maxAttempts = 3,
                baseDelayMs = 1000L, // 1초
                maxDelayMs = 60000L,  // 1분
                backoffMultiplier = 2.0
            )
            
            then("재시도 간격이 지수적으로 증가해야 한다") {
                val delayCalculator = ExponentialBackoffCalculator(retryConfig)
                
                val firstDelay = delayCalculator.calculateDelay(1)
                val secondDelay = delayCalculator.calculateDelay(2)
                val thirdDelay = delayCalculator.calculateDelay(3)
                
                firstDelay shouldBe 1000L  // 1초
                secondDelay shouldBe 2000L // 2초
                thirdDelay shouldBe 4000L  // 4초
                
                // 지수적 증가 확인
                secondDelay shouldBe (firstDelay * 2)
                thirdDelay shouldBe (secondDelay * 2)
            }
        }
        
        `when`("최대 대기 시간을 초과하는 경우") {
            val retryConfig = RetryConfig(
                maxAttempts = 10,
                baseDelayMs = 30000L, // 30초
                maxDelayMs = 60000L,  // 1분 최대
                backoffMultiplier = 2.0
            )
            
            then("최대 대기 시간으로 제한되어야 한다") {
                val delayCalculator = ExponentialBackoffCalculator(retryConfig)
                
                val highAttemptDelay = delayCalculator.calculateDelay(5) // 30 * 2^4 = 480초 → 60초로 제한
                
                highAttemptDelay shouldBe 60000L // 최대 1분으로 제한
                highAttemptDelay shouldBeLessThan 61000L
            }
        }
        
        `when`("재시도를 실행할 때") {
            val handle = "testuser" 
            var attemptCount = 0
            
            // 처음 2번은 실패, 3번째에 성공하도록 Mock 설정
            every { solvedacApiClient.getSubmissions(handle, any()) } answers {
                attemptCount++
                when (attemptCount) {
                    1, 2 -> throw CustomException(Error.SOLVEDAC_RATE_LIMIT_EXCEEDED)
                    else -> mockk() // 성공 응답
                }
            }
            
            then("지정된 횟수만큼 재시도해야 한다") {
                val startTime = System.currentTimeMillis()
                
                val retryResult = rateLimitHandler.executeWithRetry(
                    maxAttempts = 3,
                    baseDelayMs = 100L // 테스트를 위해 짧게 설정
                ) {
                    solvedacApiClient.getSubmissions(handle, 1)
                }
                
                val endTime = System.currentTimeMillis()
                val executionTime = endTime - startTime
                
                retryResult shouldNotBe null
                retryResult.successful shouldBe true
                retryResult.totalAttempts shouldBe 3
                retryResult.finalAttemptSuccessful shouldBe true
                
                // 2번의 재시도 지연시간이 포함되어야 함 (100ms + 200ms = 300ms 이상)
                executionTime shouldBeGreaterThan 300L
                
                verify(exactly = 3) { solvedacApiClient.getSubmissions(handle, any()) }
            }
        }
        
        `when`("최대 재시도 횟수를 초과할 때") {
            val handle = "testuser"
            
            // 항상 레이트 리밋 에러 발생
            every { solvedacApiClient.getSubmissions(handle, any()) } throws 
                CustomException(Error.SOLVEDAC_RATE_LIMIT_EXCEEDED)
            
            then("재시도를 포기하고 실패 결과를 반환해야 한다") {
                val retryResult = rateLimitHandler.executeWithRetry(
                    maxAttempts = 3,
                    baseDelayMs = 50L
                ) {
                    solvedacApiClient.getSubmissions(handle, 1)
                }
                
                retryResult shouldNotBe null
                retryResult.successful shouldBe false
                retryResult.totalAttempts shouldBe 3
                retryResult.finalAttemptSuccessful shouldBe false
                retryResult.lastError shouldNotBe null
                
                verify(exactly = 3) { solvedacApiClient.getSubmissions(handle, any()) }
            }
        }
        
        `when`("다양한 에러 타입을 처리할 때") {
            val handle = "testuser"
            
            then("레이트 리밋 에러만 재시도하고 다른 에러는 즉시 실패해야 한다") {
                // 사용자 없음 에러 (재시도하면 안 됨)
                every { solvedacApiClient.getSubmissions(handle, any()) } throws 
                    CustomException(Error.SOLVEDAC_USER_NOT_FOUND)
                
                val retryResult = rateLimitHandler.executeWithRetry(
                    maxAttempts = 3,
                    baseDelayMs = 50L
                ) {
                    solvedacApiClient.getSubmissions(handle, 1)
                }
                
                retryResult shouldNotBe null
                retryResult.successful shouldBe false
                retryResult.totalAttempts shouldBe 1 // 재시도 없이 즉시 실패
                retryResult.lastError?.message shouldBe Error.SOLVEDAC_USER_NOT_FOUND.message
                
                verify(exactly = 1) { solvedacApiClient.getSubmissions(handle, any()) }
            }
        }
        
        `when`("배치 수집에 레이트 리밋 처리를 적용할 때") {
            val syncJobId = UUID.randomUUID()
            val handle = "testuser"
            val batchNumber = 1
            val batchSize = 100
            
            then("레이트 리밋 발생 시 자동으로 재시도되어야 한다") {
                val rateLimitAwareBatchService = RateLimitAwareBatchService(
                    solvedacApiClient = solvedacApiClient,
                    rateLimitHandler = rateLimitHandler
                )
                
                var callCount = 0
                every { solvedacApiClient.getSubmissions(handle, any()) } answers {
                    callCount++
                    if (callCount <= 2) {
                        throw CustomException(Error.SOLVEDAC_RATE_LIMIT_EXCEEDED)
                    } else {
                        mockk() // 성공 응답
                    }
                }
                
                val batchResult = rateLimitAwareBatchService.collectBatchWithRateLimit(
                    syncJobId = syncJobId,
                    handle = handle,
                    batchNumber = batchNumber,
                    batchSize = batchSize
                )
                
                batchResult shouldNotBe null
                batchResult.successful shouldBe true
                batchResult.retryAttempts shouldBe 3 // 2번 실패 + 1번 성공
                batchResult.rateLimitHandled shouldBe true
            }
        }
    }
})

