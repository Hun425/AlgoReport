package com.algoreport.collector

import org.springframework.stereotype.Component
import kotlin.math.min
import kotlin.math.pow

/**
 * 지수 백오프 계산기 구현체
 * 
 * TDD Green 단계: 테스트를 통과하기 위한 최소한의 구현
 * Task 1-1-8: 지수 백오프 재시도 로직 구현
 */
@Component
class ExponentialBackoffCalculatorImpl(
    private val retryConfig: RetryConfig
) : ExponentialBackoffCalculator {
    
    companion object {
        // 기본 설정값
        val DEFAULT_RETRY_CONFIG = RetryConfig(
            maxAttempts = 3,
            baseDelayMs = 1000L,      // 1초
            maxDelayMs = 60000L,      // 1분
            backoffMultiplier = 2.0   // 2배씩 증가
        )
    }
    
    constructor() : this(DEFAULT_RETRY_CONFIG)
    
    override fun calculateDelay(attemptNumber: Int): Long {
        if (attemptNumber <= 0) {
            return 0L
        }
        
        // 지수 백오프 계산: baseDelay * (multiplier ^ (attemptNumber - 1))
        val exponentialDelay = retryConfig.baseDelayMs * 
                              retryConfig.backoffMultiplier.pow(attemptNumber - 1).toLong()
        
        // 최대 지연 시간으로 제한
        return min(exponentialDelay, retryConfig.maxDelayMs)
    }
}