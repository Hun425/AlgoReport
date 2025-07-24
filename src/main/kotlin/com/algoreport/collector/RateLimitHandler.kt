package com.algoreport.collector

import java.time.LocalDateTime

/**
 * 레이트 리밋 처리 인터페이스
 * 
 * TDD Red 단계: 인터페이스만 정의하고 구현은 없음
 * Task 1-1-7: 레이트 리밋 처리 기본 구조
 */
interface RateLimitHandler {
    
    /**
     * 레이트 리밋 감지
     * 
     * @param operation 실행할 작업
     * @return 레이트 리밋 감지 결과
     */
    fun <T> detectRateLimit(operation: () -> T): RateLimitDetectionResult
    
    /**
     * 지수 백오프 재시도 실행
     * 
     * @param maxAttempts 최대 재시도 횟수
     * @param baseDelayMs 기본 지연 시간 (밀리초)
     * @param operation 실행할 작업
     * @return 재시도 실행 결과
     */
    fun <T> executeWithRetry(
        maxAttempts: Int,
        baseDelayMs: Long,
        operation: () -> T
    ): RetryExecutionResult<T>
}

/**
 * 지수 백오프 계산기 인터페이스
 */
interface ExponentialBackoffCalculator {
    
    /**
     * 재시도 지연 시간 계산
     * 
     * @param attemptNumber 시도 횟수 (1부터 시작)
     * @return 지연 시간 (밀리초)
     */
    fun calculateDelay(attemptNumber: Int): Long
}

/**
 * 레이트 리밋 인식 배치 서비스 인터페이스
 */
interface RateLimitAwareBatchService {
    
    /**
     * 레이트 리밋 처리가 포함된 배치 수집
     * 
     * @param syncJobId 동기화 작업 ID
     * @param handle solved.ac 핸들
     * @param batchNumber 배치 번호
     * @param batchSize 배치 크기
     * @return 레이트 리밋 처리가 포함된 배치 결과
     */
    fun collectBatchWithRateLimit(
        syncJobId: java.util.UUID,
        handle: String,
        batchNumber: Int,
        batchSize: Int
    ): RateLimitAwareBatchResult
}

/**
 * 레이트 리밋 감지 결과 데이터 클래스
 */
data class RateLimitDetectionResult(
    val rateLimitDetected: Boolean,
    val errorType: RateLimitErrorType,
    val detectedAt: LocalDateTime,
    val retryAfterSeconds: Long? = null
)

/**
 * 레이트 리밋 에러 타입 열거형
 */
enum class RateLimitErrorType {
    API_RATE_LIMIT,      // API 호출 제한
    CONCURRENT_LIMIT,    // 동시 연결 제한
    DAILY_QUOTA_EXCEEDED // 일일 할당량 초과
}

/**
 * 재시도 설정 데이터 클래스
 */
data class RetryConfig(
    val maxAttempts: Int,
    val baseDelayMs: Long,
    val maxDelayMs: Long,
    val backoffMultiplier: Double
)

/**
 * 재시도 실행 결과 데이터 클래스
 */
data class RetryExecutionResult<T>(
    val successful: Boolean,
    val result: T?,
    val totalAttempts: Int,
    val finalAttemptSuccessful: Boolean,
    val totalRetryTimeMs: Long,
    val lastError: Throwable?
)

/**
 * 레이트 리밋 처리가 포함된 배치 수집 결과
 */
data class RateLimitAwareBatchResult(
    val syncJobId: java.util.UUID,
    val batchNumber: Int,
    val successful: Boolean,
    val collectedCount: Int = 0,
    val retryAttempts: Int = 1,
    val rateLimitHandled: Boolean = false,
    val totalRetryTimeMs: Long = 0L,
    val errorMessage: String? = null
)