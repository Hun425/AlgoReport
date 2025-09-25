package com.algoreport.module.analysis

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.module.user.UserRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.UUID

class PersonalDashboardServiceUnitTest : BehaviorSpec({

    given("개인 학습 대시보드 서비스 단위 테스트") {

        `when`("유효한 사용자 ID로 대시보드 데이터를 요청하면") {
            then("개인 대시보드 데이터가 반환되어야 한다") {
                val userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                val userKey = userId.toString()
                val userRepository = mockk<UserRepository>()
                val analysisCacheService = mockk<AnalysisCacheService>()
                val elasticsearchService = mockk<ElasticsearchService>()

                every { userRepository.existsById(userId) } returns true
                every { analysisCacheService.getPersonalAnalysisFromCache(userKey) } returns null
                every { elasticsearchService.aggregateTagSkills(userKey) } returns mapOf("dp" to 0.8, "graph" to 0.6)
                every { elasticsearchService.aggregateSolvedByDifficulty(userKey) } returns mapOf("Gold" to 45, "Silver" to 55, "Bronze" to 50)
                every { elasticsearchService.aggregateRecentActivity(userKey) } returns mapOf(
                    "2024-08-01" to 3,
                    "2024-08-02" to 2,
                    "2024-08-03" to 1
                )

                val service = PersonalDashboardService(userRepository, analysisCacheService, elasticsearchService)
                val result = service.getPersonalDashboard(userId)

                result shouldNotBe null
                result.userId shouldBe userId
                result.totalSolved shouldBe 150
                result.currentTier shouldBe 12
            }
        }

        `when`("히트맵 데이터가 필요한 경우") {
            then("365일 히트맵 데이터가 반환된다") {
                val userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
                val userKey = userId.toString()
                val userRepository = mockk<UserRepository>()
                val analysisCacheService = mockk<AnalysisCacheService>()
                val elasticsearchService = mockk<ElasticsearchService>()

                every { userRepository.existsById(userId) } returns true
                every { analysisCacheService.getPersonalAnalysisFromCache(userKey) } returns null
                every { elasticsearchService.aggregateTagSkills(userKey) } returns mapOf("dp" to 0.8)
                every { elasticsearchService.aggregateSolvedByDifficulty(userKey) } returns mapOf("Gold" to 45)
                every { elasticsearchService.aggregateRecentActivity(userKey) } returns mapOf(
                    "2024-01-01" to 3,
                    "2024-01-02" to 1
                )

                val service = PersonalDashboardService(userRepository, analysisCacheService, elasticsearchService)
                val result = service.getPersonalDashboard(userId)

                result.heatmapData shouldNotBe null
                result.heatmapData.size shouldBe 365
                result.heatmapData.keys.first().shouldBeInstanceOf<String>()
                result.heatmapData.values.first().shouldBeInstanceOf<Int>()
            }
        }

        `when`("태그 숙련도 데이터가 필요한 경우") {
            then("다양한 태그 숙련도가 포함된다") {
                val userId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
                val userKey = userId.toString()
                val userRepository = mockk<UserRepository>()
                val analysisCacheService = mockk<AnalysisCacheService>()
                val elasticsearchService = mockk<ElasticsearchService>()

                every { userRepository.existsById(userId) } returns true
                every { analysisCacheService.getPersonalAnalysisFromCache(userKey) } returns null
                every { elasticsearchService.aggregateTagSkills(userKey) } returns mapOf(
                    "dp" to 0.8,
                    "graph" to 0.6,
                    "greedy" to 0.9,
                    "implementation" to 0.7,
                    "math" to 0.5,
                    "string" to 0.3,
                    "geometry" to 0.2,
                    "data_structures" to 0.4
                )
                every { elasticsearchService.aggregateSolvedByDifficulty(userKey) } returns mapOf("Gold" to 50)
                every { elasticsearchService.aggregateRecentActivity(userKey) } returns mapOf("2024-08-01" to 2)

                val service = PersonalDashboardService(userRepository, analysisCacheService, elasticsearchService)
                val result = service.getPersonalDashboard(userId)

                result.tagSkillsRadar shouldNotBe null
                result.tagSkillsRadar.size shouldBe 8
                result.tagSkillsRadar.keys.any { it == "dp" } shouldBe true
                result.tagSkillsRadar.keys.any { it == "graph" } shouldBe true
                result.tagSkillsRadar.values.all { it in 0.0..1.0 } shouldBe true
            }
        }

        `when`("존재하지 않는 사용자 ID로 요청하면") {
            then("USER_NOT_FOUND 예외가 발생해야 한다") {
                val userId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")
                val userRepository = mockk<UserRepository>()
                val analysisCacheService = mockk<AnalysisCacheService>()
                val elasticsearchService = mockk<ElasticsearchService>()

                every { userRepository.existsById(userId) } returns false

                val service = PersonalDashboardService(userRepository, analysisCacheService, elasticsearchService)

                val exception = kotlin.runCatching { service.getPersonalDashboard(userId) }.exceptionOrNull()

                exception shouldNotBe null
                exception.shouldBeInstanceOf<CustomException>()
                (exception as CustomException).error shouldBe Error.USER_NOT_FOUND
            }
        }

        `when`("캐시된 데이터가 존재하면") {
            then("캐시된 분석 결과를 반환한다") {
                val userId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")
                val userKey = userId.toString()
                val userRepository = mockk<UserRepository>()
                val analysisCacheService = mockk<AnalysisCacheService>()
                val elasticsearchService = mockk<ElasticsearchService>()

                every { userRepository.existsById(userId) } returns true
                val cachedAnalysis = PersonalAnalysis(
                    userId = userKey,
                    analysisDate = LocalDateTime.now().minusMinutes(30),
                    totalSolved = 150,
                    currentTier = 12,
                    tagSkills = mapOf("dp" to 0.8),
                    solvedByDifficulty = mapOf("Gold" to 45),
                    recentActivity = mapOf("last7days" to 12),
                    weakTags = emptyList(),
                    strongTags = listOf("dp")
                )
                every { analysisCacheService.getPersonalAnalysisFromCache(userKey) } returns cachedAnalysis

                val service = PersonalDashboardService(userRepository, analysisCacheService, elasticsearchService)
                val result = service.getPersonalDashboard(userId)

                result.cacheHit shouldBe true
                result.userId shouldBe userId
            }
        }

        `when`("분석 데이터가 없는 신규 사용자가 요청하면") {
            then("기본값으로 채워진 대시보드 데이터가 반환되어야 한다") {
                val userId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
                val userKey = userId.toString()
                val userRepository = mockk<UserRepository>()
                val analysisCacheService = mockk<AnalysisCacheService>()
                val elasticsearchService = mockk<ElasticsearchService>()

                every { userRepository.existsById(userId) } returns true
                every { analysisCacheService.getPersonalAnalysisFromCache(userKey) } returns null
                every { elasticsearchService.aggregateTagSkills(userKey) } returns emptyMap()
                every { elasticsearchService.aggregateSolvedByDifficulty(userKey) } returns emptyMap()
                every { elasticsearchService.aggregateRecentActivity(userKey) } returns emptyMap()

                val service = PersonalDashboardService(userRepository, analysisCacheService, elasticsearchService)
                val result = service.getPersonalDashboard(userId)

                result.userId shouldBe userId
                result.totalSolved shouldBe 0
                result.currentTier shouldBe 0
                result.heatmapData.isEmpty() shouldBe true
                result.tagSkillsRadar.isEmpty() shouldBe true
                result.difficultyDistribution.isEmpty() shouldBe true
                result.isNewUser shouldBe true
                result.message shouldBe "solved.ac 계정을 연동하여 개인 통계를 확인해보세요!"
            }
        }
    }

    given("대시보드 데이터 갱신 요청이 들어올 때") {
        `when`("강제 갱신 플래그와 함께 요청하면") {
            then("최신 데이터가 조회되고 캐시가 무시된다") {
                val userId = UUID.fromString("12345678-1234-1234-1234-123456789012")
                val userKey = userId.toString()
                val userRepository = mockk<UserRepository>()
                val analysisCacheService = mockk<AnalysisCacheService>()
                val elasticsearchService = mockk<ElasticsearchService>()

                every { userRepository.existsById(userId) } returns true
                every { analysisCacheService.getPersonalAnalysisFromCache(userKey) } returns null
                every { elasticsearchService.aggregateTagSkills(userKey) } returns mapOf("dp" to 0.9)
                every { elasticsearchService.aggregateSolvedByDifficulty(userKey) } returns mapOf("Gold" to 100)
                every { elasticsearchService.aggregateRecentActivity(userKey) } returns mapOf("2024-08-04" to 5)

                val service = PersonalDashboardService(userRepository, analysisCacheService, elasticsearchService)
                val result = service.getPersonalDashboard(userId, forceRefresh = true)

                result.cacheHit shouldBe false
                result.userId shouldBe userId
            }
        }
    }
})
