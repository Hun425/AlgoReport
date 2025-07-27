package com.algoreport.config

import com.algoreport.collector.SolvedacApiClient
import com.algoreport.collector.SubmissionSyncService
import com.algoreport.collector.SubmissionRepository
import org.mockito.Mockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * 테스트용 Mock Bean 설정
 */
@TestConfiguration
class TestConfiguration {
    
    @Bean
    @Primary
    fun mockSolvedacApiClient(): SolvedacApiClient {
        return Mockito.mock(SolvedacApiClient::class.java)
    }
    
    @Bean
    @Primary  
    fun mockSubmissionSyncService(): SubmissionSyncService {
        return Mockito.mock(SubmissionSyncService::class.java)
    }
    
    @Bean
    @Primary
    fun mockSubmissionRepository(): SubmissionRepository {
        return Mockito.mock(SubmissionRepository::class.java)
    }
    
}