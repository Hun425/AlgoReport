package com.algoreport.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 코루틴 설정
 * 
 * 안전한 코루틴 스코프를 제공하여 runBlocking 사용을 방지하고
 * 비동기 처리의 안정성을 보장한다.
 */
@Configuration
class CoroutineConfig {
    
    /**
     * SAGA 전용 코루틴 스코프
     * 
     * - SupervisorJob: 하위 작업 실패가 상위 스코프에 영향을 주지 않음
     * - Dispatchers.IO: IO 작업에 최적화된 스레드 풀 사용
     */
    @Bean
    @Qualifier("sagaCoroutineScope")
    fun sagaCoroutineScope(): CoroutineScope = 
        CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 스케줄링 전용 코루틴 스코프
     * 
     * 스케줄링된 작업들이 독립적으로 실행되도록 별도 스코프 제공
     */
    @Bean
    @Qualifier("schedulingCoroutineScope") 
    fun schedulingCoroutineScope(): CoroutineScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob())
        
    /**
     * 분석 작업 전용 코루틴 스코프
     * 
     * CPU 집약적인 분석 작업을 위한 전용 스코프
     */
    @Bean
    @Qualifier("analysisCoroutineScope")
    fun analysisCoroutineScope(): CoroutineScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob())
}