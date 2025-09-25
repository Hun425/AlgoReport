package com.algoreport.module.analysis

import com.algoreport.collector.SolvedacApiClient
import com.algoreport.collector.dto.SubmissionList
import com.algoreport.config.outbox.OutboxService
import com.algoreport.module.user.SagaStatus
import com.algoreport.module.user.UserRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import java.util.UUID

class PersonalStatsRefreshSagaUnitTest : BehaviorSpec({

    val userRepository: UserRepository = mockk()
    val analysisService: AnalysisService = mockk()
    val analysisCacheService: AnalysisCacheService = mockk()
    val elasticsearchService: ElasticsearchService = mockk()
    val solvedacApiClient: SolvedacApiClient = mockk()
    val outboxService: OutboxService = mockk()

    val saga = PersonalStatsRefreshSaga(
        userRepository,
        analysisService,
        analysisCacheService,
        elasticsearchService,
        solvedacApiClient,
        outboxService
    )

    given("PERSONAL_STATS_REFRESH_SAGA의 단위 테스트") {

        `when`("존재하지 않는 사용자에 대해 통계 갱신을 요청하면") {
            val userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
            val request = PersonalStatsRefreshRequest(userId = userId)

            every { userRepository.existsById(userId) } returns false
            every { analysisService.deletePersonalAnalysis(userId.toString()) } just Runs
            every { analysisCacheService.evictPersonalAnalysis(userId.toString()) } just Runs
            every { outboxService.publishEvent(any(), any(), any(), any()) } returns UUID.randomUUID()

            val result = saga.start(request)

            then("즉시 실패하고 보상 트랜잭션이 실행되어야 한다") {
                result.sagaStatus shouldBe SagaStatus.FAILED
                result.errorMessage shouldBe "User not found: $userId"
                result.compensationExecuted shouldBe true
            }

            then("데이터 수집 및 분석 로직은 호출되지 않는다") {
                verify(exactly = 0) { solvedacApiClient.getSubmissions(any(), any()) }
                verify(exactly = 0) { elasticsearchService.indexSubmissions(any(), any()) }
                verify(exactly = 0) { analysisService.performPersonalAnalysis(any()) }
            }

            then("보상 로직만 실행된다") {
                verify { analysisService.deletePersonalAnalysis(userId.toString()) }
                verify { analysisCacheService.evictPersonalAnalysis(userId.toString()) }
                verify { outboxService.publishEvent(any(), any(), any(), any()) }
            }
        }
    }

    given("Elasticsearch 인덱싱 실패 시나리오") {
        `when`("Elasticsearch 인덱싱만 실패하는 경우") {
            then("Saga는 부분 성공 상태로 완료되어야 한다") {
                val userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
                val request = PersonalStatsRefreshRequest(userId = userId, forceRefresh = true)

                val userRepo = mockk<UserRepository>()
                val analysisService = mockk<AnalysisService>()
                val cacheService = mockk<AnalysisCacheService>()
                val esService = mockk<ElasticsearchService>()
                val apiClient = mockk<SolvedacApiClient>()
                val outbox = mockk<OutboxService>()

                every { userRepo.existsById(userId) } returns true
                every { userRepo.findSolvedacHandleById(userId) } returns "handle"
                every { cacheService.getPersonalAnalysisFromCache(userId.toString()) } returns null
                every { apiClient.getSubmissions(any(), any()) } returns mockk<SubmissionList>(relaxed = true)
                every { esService.indexSubmissions(any(), any()) } just Runs
                every { esService.aggregateTagSkills(any()) } returns mapOf("dp" to 0.8)
                every { esService.aggregateSolvedByDifficulty(any()) } returns mapOf("Gold" to 50)
                every { esService.aggregateRecentActivity(any()) } returns mapOf("today" to 5)
                every { esService.indexPersonalAnalysis(any()) } throws RuntimeException("ES failure")
                every { analysisService.setPersonalAnalysis(any(), any()) } just Runs
                every { analysisService.performPersonalAnalysis(any()) } returns PersonalAnalysis(userId = userId.toString())
                every { cacheService.cachePersonalAnalysis(any(), any()) } just Runs
                every { outbox.publishEvent(any(), any(), any(), any()) } returns UUID.randomUUID()

                val saga = PersonalStatsRefreshSaga(userRepo, analysisService, cacheService, esService, apiClient, outbox)
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
            then("캐시된 데이터를 사용하고 빠르게 완료된다") {
                val userId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
                val request = PersonalStatsRefreshRequest(userId = userId, forceRefresh = false)
                val freshAnalysis = PersonalAnalysis(
                    userId = userId.toString(),
                    analysisDate = LocalDateTime.now().minusMinutes(30)
                )

                val userRepo = mockk<UserRepository>()
                val analysisService = mockk<AnalysisService>()
                val cacheService = mockk<AnalysisCacheService>()
                val esService = mockk<ElasticsearchService>()
                val apiClient = mockk<SolvedacApiClient>()
                val outbox = mockk<OutboxService>()

                every { userRepo.existsById(userId) } returns true
                every { cacheService.getPersonalAnalysisFromCache(userId.toString()) } returns freshAnalysis
                every { analysisService.setPersonalAnalysis(userId.toString(), freshAnalysis) } just Runs
                every { outbox.publishEvent(any(), any(), any(), any()) } returns UUID.randomUUID()

                val saga = PersonalStatsRefreshSaga(userRepo, analysisService, cacheService, esService, apiClient, outbox)
                val result = saga.start(request)

                result.sagaStatus shouldBe SagaStatus.COMPLETED
                result.usedCachedData shouldBe true
                result.cacheUpdateCompleted shouldBe true
            }
        }
    }
})
