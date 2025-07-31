package com.algoreport.collector

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeGreaterThan as longShouldBeGreaterThan
import java.time.LocalDateTime
import java.util.*

/**
 * SAGA 성능 최적화 도구 테스트
 * 
 * 커버리지 향상을 위한 누락된 테스트 추가
 */
class SagaPerformanceOptimizerTest : BehaviorSpec({
    
    given("SagaPerformanceOptimizer") {
        val optimizer = SagaPerformanceOptimizer()
        
        `when`("성공적인 SAGA 성능을 분석할 때") {
            val excellentSagaResult = InitialDataSyncSagaResult(
                sagaId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                successful = true,
                totalBatches = 10,
                successfulBatches = 10,
                failedBatches = 0,
                executionTimeMs = 8000L, // 8초, 평균 800ms/배치
                sagaStatus = SagaStatus.COMPLETED
            )
            
            then("EXCELLENT 등급으로 분석되어야 한다") {
                val analysis = optimizer.analyzeSagaPerformance(excellentSagaResult)
                
                analysis.sagaId shouldBe excellentSagaResult.sagaId
                analysis.successRate shouldBe 1.0
                analysis.averageBatchTimeMs shouldBe 800.0
                analysis.totalExecutionTimeMs shouldBe 8000L
                analysis.performanceGrade shouldBe PerformanceGrade.EXCELLENT
                analysis.optimizationSuggestions.shouldNotBeEmpty()
            }
        }
        
        `when`("성능이 나쁜 SAGA를 분석할 때") {
            val poorSagaResult = InitialDataSyncSagaResult(
                sagaId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                successful = false,
                totalBatches = 10,
                successfulBatches = 6,
                failedBatches = 4,
                executionTimeMs = 50000L, // 50초, 평균 8333ms/배치
                sagaStatus = SagaStatus.PARTIALLY_COMPLETED
            )
            
            then("POOR 또는 CRITICAL 등급으로 분석되어야 한다") {
                val analysis = optimizer.analyzeSagaPerformance(poorSagaResult)
                
                analysis.sagaId shouldBe poorSagaResult.sagaId
                analysis.successRate shouldBe 0.6
                analysis.averageBatchTimeMs.shouldBeGreaterThan(8000.0)
                analysis.performanceGrade shouldBe PerformanceGrade.CRITICAL
                analysis.optimizationSuggestions.shouldNotBeEmpty()
            }
        }
        
        `when`("소량 데이터에 대한 배치 크기를 추천할 때") {
            val totalSubmissions = 50
            
            then("25 크기의 배치를 추천해야 한다") {
                val recommendation = optimizer.recommendOptimalBatchSize(totalSubmissions)
                
                recommendation.recommendedBatchSize shouldBe 25
                recommendation.recommendedBatchCount shouldBe 2 // (50 + 25 - 1) / 25
                recommendation.recommendedConcurrencyLevel.shouldBeGreaterThan(0)
                recommendation.estimatedExecutionTimeMinutes.shouldBeGreaterThan(0)
                recommendation.reasoning shouldNotBe ""
            }
        }
        
        `when`("중간 규모 데이터에 대한 배치 크기를 추천할 때") {
            val totalSubmissions = 300
            
            then("50 크기의 배치를 추천해야 한다") {
                val recommendation = optimizer.recommendOptimalBatchSize(totalSubmissions)
                
                recommendation.recommendedBatchSize shouldBe 50
                recommendation.recommendedBatchCount shouldBe 6 // (300 + 50 - 1) / 50
                recommendation.recommendedConcurrencyLevel.shouldBeGreaterThan(0)
                recommendation.estimatedExecutionTimeMinutes.shouldBeGreaterThan(0)
            }
        }
        
        `when`("대량 데이터에 대한 배치 크기를 추천할 때") {
            val totalSubmissions = 1500
            
            then("100 크기의 배치를 추천해야 한다") {
                val recommendation = optimizer.recommendOptimalBatchSize(totalSubmissions)
                
                recommendation.recommendedBatchSize shouldBe 100
                recommendation.recommendedBatchCount shouldBe 15 // (1500 + 100 - 1) / 100
                recommendation.recommendedConcurrencyLevel.shouldBeGreaterThan(0)
                recommendation.estimatedExecutionTimeMinutes.shouldBeGreaterThan(0)
            }
        }
        
        `when`("초대용량 데이터에 대한 배치 크기를 추천할 때") {
            val totalSubmissions = 5000
            
            then("200 크기의 배치를 추천해야 한다") {
                val recommendation = optimizer.recommendOptimalBatchSize(totalSubmissions)
                
                recommendation.recommendedBatchSize shouldBe 200
                recommendation.recommendedBatchCount shouldBe 25 // (5000 + 200 - 1) / 200
                recommendation.recommendedConcurrencyLevel shouldBeGreaterThan 0
                recommendation.estimatedExecutionTimeMinutes shouldBeGreaterThan 0
            }
        }
        
        `when`("높은 실패율 히스토리로 재시도 전략을 최적화할 때") {
            val highFailureHistory = listOf(
                SagaPerformanceMetrics(
                    sagaId = UUID.randomUUID(),
                    totalBatches = 10,
                    successfulBatches = 4,
                    failedBatches = 6,
                    executionTimeMs = 30000L,
                    timestamp = LocalDateTime.now()
                ),
                SagaPerformanceMetrics(
                    sagaId = UUID.randomUUID(),
                    totalBatches = 8,
                    successfulBatches = 3,
                    failedBatches = 5,
                    executionTimeMs = 25000L,
                    timestamp = LocalDateTime.now()
                )
            )
            
            then("보수적인 재시도 전략을 추천해야 한다") {
                val optimization = optimizer.optimizeRetryStrategy(highFailureHistory)
                
                optimization.optimizedConfig.maxAttempts shouldBe 2
                optimization.optimizedConfig.baseDelayMs shouldBe 5000L
                optimization.optimizedConfig.maxDelayMs shouldBe 300000L
                optimization.optimizedConfig.backoffMultiplier shouldBe 3.0
                optimization.previousFailureRate shouldBeGreaterThanOrEqual 0.5
                optimization.expectedImprovementPercent shouldBe 40
                optimization.reasoning shouldNotBe ""
            }
        }
        
        `when`("중간 실패율 히스토리로 재시도 전략을 최적화할 때") {
            val mediumFailureHistory = listOf(
                SagaPerformanceMetrics(
                    sagaId = UUID.randomUUID(),
                    totalBatches = 10,
                    successfulBatches = 7,
                    failedBatches = 3,
                    executionTimeMs = 20000L,
                    timestamp = LocalDateTime.now()
                )
            )
            
            then("표준 재시도 전략을 추천해야 한다") {
                val optimization = optimizer.optimizeRetryStrategy(mediumFailureHistory)
                
                optimization.optimizedConfig.maxAttempts shouldBe 3
                optimization.optimizedConfig.baseDelayMs shouldBe 2000L
                optimization.optimizedConfig.maxDelayMs shouldBe 120000L
                optimization.optimizedConfig.backoffMultiplier shouldBe 2.0
                optimization.expectedImprovementPercent shouldBe 25
            }
        }
        
        `when`("낮은 실패율 히스토리로 재시도 전략을 최적화할 때") {
            val lowFailureHistory = listOf(
                SagaPerformanceMetrics(
                    sagaId = UUID.randomUUID(),
                    totalBatches = 10,
                    successfulBatches = 9,
                    failedBatches = 1,
                    executionTimeMs = 15000L,
                    timestamp = LocalDateTime.now()
                )
            )
            
            then("적극적인 재시도 전략을 추천해야 한다") {
                val optimization = optimizer.optimizeRetryStrategy(lowFailureHistory)
                
                optimization.optimizedConfig.maxAttempts shouldBe 4
                optimization.optimizedConfig.baseDelayMs shouldBe 500L
                optimization.optimizedConfig.maxDelayMs shouldBe 30000L
                optimization.optimizedConfig.backoffMultiplier shouldBe 1.5
                optimization.expectedImprovementPercent shouldBe 10
            }
        }
        
        `when`("히스토리가 없을 때 재시도 전략을 최적화할 때") {
            val emptyHistory = emptyList<SagaPerformanceMetrics>()
            
            then("기본 재시도 전략을 반환해야 한다") {
                val optimization = optimizer.optimizeRetryStrategy(emptyHistory)
                
                optimization.optimizedConfig.maxAttempts shouldBe 3
                optimization.optimizedConfig.baseDelayMs shouldBe 1000L
                optimization.optimizedConfig.maxDelayMs shouldBe 60000L
                optimization.optimizedConfig.backoffMultiplier shouldBe 2.0
                optimization.previousFailureRate shouldBe 0.0
                optimization.expectedImprovementPercent shouldBe 0
                optimization.reasoning shouldBe "Default configuration - no historical data available"
            }
        }
        
        `when`("빈 배치 결과를 분석할 때") {
            val emptySagaResult = InitialDataSyncSagaResult(
                sagaId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                successful = false,
                totalBatches = 0,
                successfulBatches = 0,
                failedBatches = 0,
                executionTimeMs = 100L,
                sagaStatus = SagaStatus.FAILED
            )
            
            then("적절한 기본값으로 분석되어야 한다") {
                val analysis = optimizer.analyzeSagaPerformance(emptySagaResult)
                
                analysis.sagaId shouldBe emptySagaResult.sagaId
                analysis.successRate shouldBe 0.0
                analysis.averageBatchTimeMs shouldBe 0.0
                analysis.totalExecutionTimeMs shouldBe 100L
                analysis.performanceGrade shouldBe PerformanceGrade.CRITICAL
                analysis.optimizationSuggestions.shouldNotBeEmpty()
            }
        }
        
        `when`("커스텀 스레드 수로 배치 크기를 추천할 때") {
            val totalSubmissions = 1000
            val customThreads = 16
            
            then("스레드 수를 고려한 추천을 제공해야 한다") {
                val recommendation = optimizer.recommendOptimalBatchSize(totalSubmissions, customThreads)
                
                recommendation.recommendedBatchSize shouldBe 100
                recommendation.recommendedBatchCount shouldBe 10
                recommendation.recommendedConcurrencyLevel shouldBeGreaterThan 0
                recommendation.estimatedExecutionTimeMinutes shouldBeGreaterThan 0
                recommendation.reasoning.contains("$totalSubmissions submissions") shouldBe true
            }
        }
    }
})