package com.algoreport.collector

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual

/**
 * 지수 백오프 계산기 테스트
 * 
 * 커버리지 향상을 위한 누락된 테스트 추가
 */
class ExponentialBackoffCalculatorTest : BehaviorSpec({
    
    given("ExponentialBackoffCalculatorImpl") {
        val calculator = ExponentialBackoffCalculatorImpl()
        
        `when`("첫 번째 재시도(attemptNumber = 1)일 때") {
            then("기본 지연 시간(1초)을 반환해야 한다") {
                val delay = calculator.calculateDelay(1)
                delay shouldBe 1000L // 1초
            }
        }
        
        `when`("두 번째 재시도(attemptNumber = 2)일 때") {
            then("2배 증가한 지연 시간(2초)을 반환해야 한다") {
                val delay = calculator.calculateDelay(2)
                delay shouldBe 2000L // 2초
            }
        }
        
        `when`("세 번째 재시도(attemptNumber = 3)일 때") {
            then("4배 증가한 지연 시간(4초)을 반환해야 한다") {
                val delay = calculator.calculateDelay(3)
                delay shouldBe 4000L // 4초
            }
        }
        
        `when`("네 번째 재시도(attemptNumber = 4)일 때") {
            then("8배 증가한 지연 시간(8초)을 반환해야 한다") {
                val delay = calculator.calculateDelay(4)
                delay shouldBe 8000L // 8초
            }
        }
        
        `when`("매우 높은 재시도 횟수일 때") {
            then("최대 지연 시간(60초)으로 제한되어야 한다") {
                val delay = calculator.calculateDelay(10)
                delay shouldBe 60000L // 1분 최대
            }
        }
        
        `when`("잘못된 입력(attemptNumber <= 0)일 때") {
            then("0을 반환해야 한다") {
                calculator.calculateDelay(0) shouldBe 0L
                calculator.calculateDelay(-1) shouldBe 0L
                calculator.calculateDelay(-5) shouldBe 0L
            }
        }
        
        `when`("커스텀 RetryConfig로 생성할 때") {
            val customConfig = RetryConfig(
                maxAttempts = 5,
                baseDelayMs = 500L,      // 0.5초
                maxDelayMs = 10000L,     // 10초
                backoffMultiplier = 3.0  // 3배씩 증가
            )
            val customCalculator = ExponentialBackoffCalculatorImpl(customConfig)
            
            then("커스텀 설정에 따라 지연 시간이 계산되어야 한다") {
                customCalculator.calculateDelay(1) shouldBe 500L  // 0.5초
                customCalculator.calculateDelay(2) shouldBe 1500L // 1.5초
                customCalculator.calculateDelay(3) shouldBe 4500L // 4.5초
                customCalculator.calculateDelay(4) shouldBe 10000L // 10초 최대
            }
        }
        
        `when`("지수 백오프 증가 패턴을 검증할 때") {
            then("각 재시도마다 지연 시간이 지수적으로 증가해야 한다") {
                val delay1 = calculator.calculateDelay(1)
                val delay2 = calculator.calculateDelay(2)
                val delay3 = calculator.calculateDelay(3)
                val delay4 = calculator.calculateDelay(4)
                
                delay2 shouldBeGreaterThan delay1
                delay3 shouldBeGreaterThan delay2
                delay4 shouldBeGreaterThan delay3
                
                // 지수 증가 패턴 검증 (2배씩 증가)
                delay2 shouldBe delay1 * 2
                delay3 shouldBe delay1 * 4
                delay4 shouldBe delay1 * 8
            }
        }
        
        `when`("최대 지연 시간 제한을 테스트할 때") {
            then("매우 높은 재시도 횟수에서도 최대값을 초과하지 않아야 한다") {
                val maxDelay = 60000L
                
                for (attempt in 7..15) {
                    val delay = calculator.calculateDelay(attempt)
                    delay shouldBeLessThanOrEqual maxDelay
                    delay shouldBe maxDelay // 모두 최대값이어야 함
                }
            }
        }
    }
})