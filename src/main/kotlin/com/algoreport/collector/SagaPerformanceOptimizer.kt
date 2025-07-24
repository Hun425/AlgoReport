package com.algoreport.collector

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * SAGA 성능 최적화 유틸리티
 * 
 * TDD Refactor 단계: 성능 모니터링 및 최적화 기능
 * Task 1-1-9: 성능 튜닝
 */
@Component
class SagaPerformanceOptimizer {
    
    private val logger = LoggerFactory.getLogger(SagaPerformanceOptimizer::class.java)
    
    // 성능 메트릭 저장소 (실제로는 Redis나 DB를 사용)
    private val performanceMetrics = ConcurrentHashMap<UUID, SagaPerformanceMetrics>()
    
    /**
     * SAGA 실행 성능 분석
     */
    fun analyzeSagaPerformance(sagaResult: InitialDataSyncSagaResult): SagaPerformanceAnalysis {
        val metrics = SagaPerformanceMetrics(
            sagaId = sagaResult.sagaId,
            totalBatches = sagaResult.totalBatches,
            successfulBatches = sagaResult.successfulBatches,
            failedBatches = sagaResult.failedBatches,
            executionTimeMs = sagaResult.executionTimeMs,
            timestamp = LocalDateTime.now()
        )
        
        performanceMetrics[sagaResult.sagaId] = metrics
        
        // 성능 분석 수행
        val successRate = if (sagaResult.totalBatches > 0) {
            sagaResult.successfulBatches.toDouble() / sagaResult.totalBatches.toDouble()
        } else {
            0.0
        }
        
        val avgBatchTime = if (sagaResult.successfulBatches > 0) {
            sagaResult.executionTimeMs.toDouble() / sagaResult.successfulBatches.toDouble()
        } else {
            0.0
        }
        
        val performanceGrade = calculatePerformanceGrade(successRate, avgBatchTime)
        val optimizationSuggestions = generateOptimizationSuggestions(metrics, successRate, avgBatchTime)
        
        val analysis = SagaPerformanceAnalysis(
            sagaId = sagaResult.sagaId,
            successRate = successRate,
            averageBatchTimeMs = avgBatchTime,
            totalExecutionTimeMs = sagaResult.executionTimeMs,
            performanceGrade = performanceGrade,
            optimizationSuggestions = optimizationSuggestions
        )
        
        logger.info("SAGA performance analysis completed - sagaId: {}, grade: {}, success rate: {:.2f}%", 
                   sagaResult.sagaId, performanceGrade, successRate * 100)
        
        return analysis
    }
    
    /**
     * 최적의 배치 크기 추천
     */
    fun recommendOptimalBatchSize(
        totalSubmissions: Int,
        availableThreads: Int = Runtime.getRuntime().availableProcessors()
    ): BatchSizeRecommendation {
        // Virtual Thread를 고려한 최적 배치 크기 계산
        val virtualThreadMultiplier = 10 // Virtual Thread는 OS Thread보다 많이 생성 가능
        val maxConcurrentBatches = availableThreads * virtualThreadMultiplier
        
        val optimalBatchSize = when {
            totalSubmissions <= 100 -> 25  // 소량: 작은 배치로 빠른 피드백
            totalSubmissions <= 500 -> 50  // 중간: 균형잡힌 배치 크기
            totalSubmissions <= 2000 -> 100 // 대량: 표준 배치 크기
            else -> 200 // 초대용량: 큰 배치로 오버헤드 감소
        }
        
        val recommendedBatches = max(1, (totalSubmissions + optimalBatchSize - 1) / optimalBatchSize)
        val concurrencyLevel = max(1, maxConcurrentBatches.coerceAtMost(recommendedBatches))
        
        return BatchSizeRecommendation(
            recommendedBatchSize = optimalBatchSize,
            recommendedBatchCount = recommendedBatches,
            recommendedConcurrencyLevel = concurrencyLevel,
            estimatedExecutionTimeMinutes = estimateExecutionTime(
                recommendedBatches, concurrencyLevel
            ),
            reasoning = buildReasoningText(totalSubmissions, optimalBatchSize, concurrencyLevel)
        )
    }
    
    /**
     * SAGA 재시도 전략 최적화
     */
    fun optimizeRetryStrategy(
        failureHistory: List<SagaPerformanceMetrics>
    ): RetryStrategyOptimization {
        if (failureHistory.isEmpty()) {
            return RetryStrategyOptimization.default()
        }
        
        // 실패 패턴 분석
        val avgFailureRate = failureHistory.map { metrics ->
            if (metrics.totalBatches > 0) {
                metrics.failedBatches.toDouble() / metrics.totalBatches.toDouble()
            } else {
                0.0
            }
        }.average()
        
        val avgExecutionTime = failureHistory.map { it.executionTimeMs }.average()
        
        // 최적화된 재시도 전략 생성
        val optimizedConfig = when {
            avgFailureRate > 0.5 -> {
                // 높은 실패율: 긴 대기시간, 적은 재시도
                RetryConfig(
                    maxAttempts = 2,
                    baseDelayMs = 5000L, // 5초
                    maxDelayMs = 300000L, // 5분
                    backoffMultiplier = 3.0
                )
            }
            avgFailureRate > 0.2 -> {
                // 중간 실패율: 표준 설정
                RetryConfig(
                    maxAttempts = 3,
                    baseDelayMs = 2000L, // 2초
                    maxDelayMs = 120000L, // 2분
                    backoffMultiplier = 2.0
                )
            }
            else -> {
                // 낮은 실패율: 빠른 재시도
                RetryConfig(
                    maxAttempts = 4,
                    baseDelayMs = 500L, // 0.5초
                    maxDelayMs = 30000L, // 30초
                    backoffMultiplier = 1.5
                )
            }
        }
        
        return RetryStrategyOptimization(
            optimizedConfig = optimizedConfig,
            previousFailureRate = avgFailureRate,
            expectedImprovementPercent = calculateExpectedImprovement(avgFailureRate),
            reasoning = "Based on ${failureHistory.size} previous executions with ${(avgFailureRate * 100).toInt()}% failure rate"
        )
    }
    
    /**
     * 성능 등급 계산
     */
    private fun calculatePerformanceGrade(successRate: Double, avgBatchTimeMs: Double): PerformanceGrade {
        return when {
            successRate >= 0.95 && avgBatchTimeMs <= 1000 -> PerformanceGrade.EXCELLENT
            successRate >= 0.90 && avgBatchTimeMs <= 2000 -> PerformanceGrade.GOOD
            successRate >= 0.80 && avgBatchTimeMs <= 5000 -> PerformanceGrade.FAIR
            successRate >= 0.70 -> PerformanceGrade.POOR
            else -> PerformanceGrade.CRITICAL
        }
    }
    
    /**
     * 최적화 제안 생성
     */
    private fun generateOptimizationSuggestions(
        metrics: SagaPerformanceMetrics,
        successRate: Double,
        avgBatchTimeMs: Double
    ): List<String> {
        val suggestions = mutableListOf<String>()
        
        if (successRate < 0.9) {
            suggestions.add("성공률이 낮습니다. 레이트 리밋 처리 전략을 개선하거나 배치 크기를 줄여보세요.")
        }
        
        if (avgBatchTimeMs > 3000) {
            suggestions.add("배치 처리 시간이 깁니다. Virtual Thread 동시성을 늘리거나 배치 크기를 조정하세요.")
        }
        
        if (metrics.failedBatches > metrics.totalBatches * 0.3) {
            suggestions.add("실패율이 높습니다. API 호출 간격을 늘리거나 재시도 전략을 강화하세요.")
        }
        
        if (metrics.executionTimeMs > 600000) { // 10분 초과
            suggestions.add("전체 실행 시간이 깁니다. 병렬 처리 수준을 높이는 것을 검토하세요.")
        }
        
        if (suggestions.isEmpty()) {
            suggestions.add("성능이 양호합니다. 현재 설정을 유지하세요.")
        }
        
        return suggestions
    }
    
    private fun estimateExecutionTime(batchCount: Int, concurrencyLevel: Int): Int {
        // 기본 배치당 2초, 병렬성 고려하여 계산
        val sequentialTimeMinutes = (batchCount * 2) / 60
        val parallelTimeMinutes = max(1, sequentialTimeMinutes / concurrencyLevel)
        return parallelTimeMinutes
    }
    
    private fun buildReasoningText(
        totalSubmissions: Int,
        batchSize: Int,
        concurrencyLevel: Int
    ): String {
        return "For $totalSubmissions submissions: batch size $batchSize optimizes throughput vs memory usage, " +
               "concurrency level $concurrencyLevel maximizes Virtual Thread utilization"
    }
    
    private fun calculateExpectedImprovement(currentFailureRate: Double): Int {
        return when {
            currentFailureRate > 0.5 -> 40 // 큰 개선 기대
            currentFailureRate > 0.2 -> 25 // 중간 개선 기대
            else -> 10 // 소폭 개선 기대
        }
    }
}

/**
 * SAGA 성능 메트릭
 */
data class SagaPerformanceMetrics(
    val sagaId: UUID,
    val totalBatches: Int,
    val successfulBatches: Int,
    val failedBatches: Int,
    val executionTimeMs: Long,
    val timestamp: LocalDateTime
)

/**
 * SAGA 성능 분석 결과
 */
data class SagaPerformanceAnalysis(
    val sagaId: UUID,
    val successRate: Double,
    val averageBatchTimeMs: Double,
    val totalExecutionTimeMs: Long,
    val performanceGrade: PerformanceGrade,
    val optimizationSuggestions: List<String>
)

/**
 * 배치 크기 추천 결과
 */
data class BatchSizeRecommendation(
    val recommendedBatchSize: Int,
    val recommendedBatchCount: Int,
    val recommendedConcurrencyLevel: Int,
    val estimatedExecutionTimeMinutes: Int,
    val reasoning: String
)

/**
 * 재시도 전략 최적화 결과
 */
data class RetryStrategyOptimization(
    val optimizedConfig: RetryConfig,
    val previousFailureRate: Double,
    val expectedImprovementPercent: Int,
    val reasoning: String
) {
    companion object {
        fun default() = RetryStrategyOptimization(
            optimizedConfig = RetryConfig(
                maxAttempts = 3,
                baseDelayMs = 1000L,
                maxDelayMs = 60000L,
                backoffMultiplier = 2.0
            ),
            previousFailureRate = 0.0,
            expectedImprovementPercent = 0,
            reasoning = "Default configuration - no historical data available"
        )
    }
}

/**
 * 성능 등급
 */
enum class PerformanceGrade {
    EXCELLENT,  // 95%+ 성공률, 1초 이하 배치 시간
    GOOD,       // 90%+ 성공률, 2초 이하 배치 시간
    FAIR,       // 80%+ 성공률, 5초 이하 배치 시간
    POOR,       // 70%+ 성공률
    CRITICAL    // 70% 미만 성공률
}