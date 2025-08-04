package com.algoreport.module.analysis

import com.algoreport.collector.SolvedacApiClient
import com.algoreport.collector.dto.SubmissionList
import com.algoreport.config.outbox.OutboxService
import com.algoreport.module.user.SagaStatus
import com.algoreport.module.user.UserRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.Runs
import io.mockk.just


/**
 * PersonalStatsRefreshSaga 순수 단위 테스트
 * - TDD Level 2: Saga 단위 테스트
 * - @SpringBootTest 없이 MockK를 사용하여 Saga의 오케스트레이션 로직 자체를 검증
 */
class PersonalStatsRefreshSagaUnitTest : BehaviorSpec({

    val userRepository: UserRepository = mockk()
    val analysisService: AnalysisService = mockk()
    val analysisCacheService: AnalysisCacheService = mockk()
    val elasticsearchService: ElasticsearchService = mockk()
    val solvedacApiClient: SolvedacApiClient = mockk()
    val outboxService: OutboxService = mockk()

    val personalStatsRefreshSaga: PersonalStatsRefreshSaga = PersonalStatsRefreshSaga(
        userRepository,
        analysisService,
        analysisCacheService,
        elasticsearchService,
        solvedacApiClient,
        outboxService
    )

    given("PERSONAL_STATS_REFRESH_SAGA의 단위 테스트") {

        `when`("존재하지 않는 사용자에 대해 통계 갱신을 요청하면") {
            val request = PersonalStatsRefreshRequest(userId = "non-existent-user")
            // MockK 설정
            every { userRepository.findAllActiveUserIds() } returns emptyList()
            every { analysisService.deletePersonalAnalysis(any()) } just Runs
            every { analysisCacheService.evictPersonalAnalysis(any()) } just Runs
            every { outboxService.publishEvent(any(), any(), any(), any()) } returns java.util.UUID.randomUUID()

            // When: Saga 실행
            val result = personalStatsRefreshSaga.start(request)

            then("즉시 실패하고 보상 트랜잭션이 실행되어야 한다") {
                result.sagaStatus shouldBe SagaStatus.FAILED
                result.errorMessage shouldBe "User not found: non-existent-user"
                result.compensationExecuted shouldBe true
            }

            then("데이터 수집 및 분석 로직은 전혀 호출되지 않아야 한다") {
                verify(exactly = 0) { solvedacApiClient.getSubmissions(any(), any()) }
                verify(exactly = 0) { elasticsearchService.indexSubmissions(any(), any()) }
                verify(exactly = 0) { analysisService.performPersonalAnalysis(any()) }
            }
            
            then("보상 로직 관련 서비스들만 정확히 호출되어야 한다") {
                verify(exactly = 1) { analysisService.deletePersonalAnalysis("non-existent-user") }
                verify(exactly = 1) { analysisCacheService.evictPersonalAnalysis("non-existent-user") }
                // 보상 이벤트 발행 검증 - OutboxService 호출 확인
                verify(atLeast = 1) { outboxService.publishEvent(any(), any(), any(), any()) }
            }
        }
    }

    given("Elasticsearch 인덱싱 실패 시나리오") {
        `when`("Elasticsearch 인덱싱만 실패하는 경우") {
            then("Saga는 부분 성공 상태로 완료되어야 한다") {
                val request = PersonalStatsRefreshRequest(userId = "test-user", forceRefresh = true)
                
                // MockK 설정 - 새로운 Mock 인스턴스 생성
                val userRepo = mockk<UserRepository>()
                val analysisService = mockk<AnalysisService>()
                val cacheService = mockk<AnalysisCacheService>()
                val esService = mockk<ElasticsearchService>()
                val apiClient = mockk<SolvedacApiClient>()  
                val outboxService = mockk<OutboxService>()
                
                every { userRepo.findAllActiveUserIds() } returns listOf("test-user")
                every { apiClient.getSubmissions(any(), any()) } returns mockk<SubmissionList>(relaxed = true)
                every { esService.indexSubmissions(any(), any()) } just Runs
                every { esService.aggregateTagSkills(any()) } returns mapOf("dp" to 0.8)
                every { esService.aggregateSolvedByDifficulty(any()) } returns mapOf("Gold" to 50)
                every { esService.aggregateRecentActivity(any()) } returns mapOf("today" to 5)
                every { esService.indexPersonalAnalysis(any()) } throws RuntimeException("ES connection failed")
                every { analysisService.setPersonalAnalysis(any(), any()) } just Runs
                every { cacheService.cachePersonalAnalysis(any(), any()) } just Runs
                every { outboxService.publishEvent(any(), any(), any(), any()) } returns java.util.UUID.randomUUID()

                val saga = PersonalStatsRefreshSaga(userRepo, analysisService, cacheService, esService, apiClient, outboxService)
                val result = saga.start(request)

                result.sagaStatus shouldBe SagaStatus.PARTIAL_SUCCESS
                result.elasticsearchIndexingCompleted shouldBe false
                result.cacheUpdateCompleted shouldBe true
                result.compensationExecuted shouldBe false
            }
        }
    }

    given("캐시 활용 시나리오") {
        `when`("강제 새로고침이 아니고 신선한 캐시가 있는 경우") {
            then("캐시된 데이터를 사용하고 빠르게 완료되어야 한다") {
                val request = PersonalStatsRefreshRequest(userId = "cached-user", forceRefresh = false)
                val freshAnalysis = PersonalAnalysis(
                    userId = "cached-user", 
                    analysisDate = java.time.LocalDateTime.now().minusMinutes(30)
                )
                
                // MockK 설정 - 새로운 Mock 인스턴스 생성
                val userRepo = mockk<UserRepository>()
                val analysisService = mockk<AnalysisService>()
                val cacheService = mockk<AnalysisCacheService>()
                val esService = mockk<ElasticsearchService>()
                val apiClient = mockk<SolvedacApiClient>()
                val outboxService = mockk<OutboxService>()
                
                every { userRepo.findAllActiveUserIds() } returns listOf("cached-user")
                every { cacheService.getPersonalAnalysisFromCache("cached-user") } returns freshAnalysis
                every { analysisService.setPersonalAnalysis("cached-user", freshAnalysis) } just Runs

                val saga = PersonalStatsRefreshSaga(userRepo, analysisService, cacheService, esService, apiClient, outboxService)
                val result = saga.start(request)

                result.sagaStatus shouldBe SagaStatus.COMPLETED
                result.usedCachedData shouldBe true
                result.dataCollectionCompleted shouldBe true
                result.elasticsearchIndexingCompleted shouldBe true
                result.cacheUpdateCompleted shouldBe true
                result.eventPublished shouldBe true
                
                // API 호출이 발생하지 않았는지 검증
                verify(exactly = 0) { apiClient.getSubmissions(any(), any()) }
                verify(exactly = 0) { analysisService.performPersonalAnalysis(any()) }
                verify(exactly = 0) { esService.indexSubmissions(any(), any()) }
                verify(exactly = 0) { esService.indexPersonalAnalysis(any()) }
                
                // 캐시된 데이터가 등록되었는지 검증
                verify(exactly = 1) { analysisService.setPersonalAnalysis("cached-user", freshAnalysis) }
            }
        }
    }
})
