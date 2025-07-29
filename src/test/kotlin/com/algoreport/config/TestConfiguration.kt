package com.algoreport.config

import com.algoreport.collector.SolvedacApiClient
import com.algoreport.collector.SubmissionSyncService
import com.algoreport.collector.SubmissionRepository
import com.algoreport.collector.dto.*
import com.algoreport.config.outbox.OutboxService
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
    fun testSolvedacApiClient(): SolvedacApiClient {
        return object : SolvedacApiClient {
            override fun getUserInfo(handle: String): UserInfo {
                // 존재하지 않는 핸들에 대해서는 예외 발생
                if (handle.startsWith("non_existent_")) {
                    throw RuntimeException("solved.ac API: User not found")
                }
                
                // 테스트용 유효한 UserInfo 반환 (실제 필드명에 맞춤)
                return UserInfo(
                    handle = handle,
                    bio = "테스트 사용자",
                    organizations = emptyList(),
                    badge = null,
                    background = "default",
                    profileImageUrl = null,
                    solvedCount = 100,
                    voteCount = 50,
                    tier = 10,  // @JsonProperty("class")
                    classDecoration = "none",
                    rivalCount = 0,
                    reverseRivalCount = 0,
                    maxStreak = 10,
                    coins = 100,
                    stardusts = 50,
                    joinedAt = null,
                    bannedUntil = null,
                    proUntil = null,
                    rank = 12345
                )
            }
            
            override fun getSubmissions(handle: String, page: Int): SubmissionList {
                return SubmissionList(count = 0, items = emptyList())
            }
            
            override fun getProblemInfo(problemId: Int): ProblemInfo {
                return ProblemInfo(
                    problemId = problemId,
                    titleKo = "테스트 문제",
                    titles = emptyList(),
                    level = 10,
                    acceptedUserCount = 100,
                    averageTries = 2.5,
                    tags = emptyList(),
                    metadata = emptyMap()
                )
            }
        }
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
    
    @Bean
    @Primary
    fun mockOutboxService(): OutboxService {
        // OutboxService는 class이므로 Mockito로 mock하고 적절한 반환값 설정
        val mockService = Mockito.mock(OutboxService::class.java)
        
        // publishEvent 메서드가 UUID를 반환하도록 설정
        Mockito.`when`(mockService.publishEvent(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyMap()
        )).thenReturn(java.util.UUID.randomUUID())
        
        return mockService
    }
    
}