package com.algoreport.module.analysis

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.config.properties.AlgoreportProperties
import com.algoreport.module.user.UserRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.UUID

class RecommendationServiceTest : BehaviorSpec({

    fun mockProperties(): AlgoreportProperties {
        val analysisProperties = mockk<AlgoreportProperties.AnalysisProperties>()
        val externalProperties = mockk<AlgoreportProperties.ExternalProperties>()
        val properties = mockk<AlgoreportProperties>()
        every { properties.analysis } returns analysisProperties
        every { properties.external } returns externalProperties
        every { analysisProperties.maxRecommendations } returns 5
        every { analysisProperties.strongTagThreshold } returns 0.7
        every { analysisProperties.weakTagThreshold } returns 0.5
        every { externalProperties.baekjoonProblemBaseUrl } returns "https://www.acmicpc.net/problem/"
        return properties
    }

    given("맞춤 문제 추천 서비스 테스트") {

        `when`("유효한 사용자에게 문제 추천을 요청하면") {
            then("취약 태그 기반으로 5개 문제가 추천되어야 한다") {
                val userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                val userKey = userId.toString()
                val userRepository = mockk<UserRepository>()
                val analysisCacheService = mockk<AnalysisCacheService>()
                val elasticsearchService = mockk<ElasticsearchService>()

                every { userRepository.existsById(userId) } returns true
                every { analysisCacheService.getRecommendationFromCache(userKey) } returns null

                val personalAnalysis = PersonalAnalysis(
                    userId = userKey,
                    analysisDate = LocalDateTime.now(),
                    totalSolved = 150,
                    currentTier = 8,
                    tagSkills = mapOf(
                        "dp" to 0.3,
                        "graph" to 0.4,
                        "greedy" to 0.8,
                        "implementation" to 0.7
                    ),
                    solvedByDifficulty = mapOf("Silver" to 50, "Gold" to 30),
                    recentActivity = mapOf("last7days" to 5),
                    weakTags = listOf("dp", "graph"),
                    strongTags = listOf("greedy", "implementation")
                )
                every { analysisCacheService.getPersonalAnalysisFromCache(userKey) } returns personalAnalysis
                every { analysisCacheService.cacheRecommendation(userKey, any(), any()) } returns Unit

                val mockProblems = listOf(
                    ProblemMetadata("1001", "DP 기초 문제", "Gold V", 11, listOf("dp"), 5000, 11),
                    ProblemMetadata("1002", "그래프 탐색", "Silver I", 9, listOf("graph", "dfs"), 3000, 9),
                    ProblemMetadata("1003", "동적 계획법", "Silver III", 7, listOf("dp"), 2500, 7),
                    ProblemMetadata("1004", "최단 경로", "Gold IV", 12, listOf("graph", "dijkstra"), 1800, 12),
                    ProblemMetadata("1005", "DP 최적화", "Silver II", 8, listOf("dp", "optimization"), 2200, 8)
                )
                every { elasticsearchService.searchProblemsByTags(listOf("dp", "graph"), any(), any()) } returns mockProblems
                every { elasticsearchService.getUserSolvedProblems(userKey) } returns setOf("999", "998")

                val recommendationService = RecommendationService(
                    userRepository,
                    analysisCacheService,
                    elasticsearchService,
                    mockProperties()
                )

                val request = RecommendationRequest(userId, maxRecommendations = 5)
                val result = recommendationService.getPersonalizedRecommendations(request)

                result shouldNotBe null
                result.userId shouldBe userId
                result.recommendedProblems.shouldHaveSize(5)
                result.totalRecommendations shouldBe 5
                result.weakTags shouldBe listOf("dp", "graph")
                result.userCurrentTier shouldBe 8
                result.recommendationStrategy shouldBe "WEAK_TAG_BASED"
            }
        }

        `when`("추천된 문제들은 사용자 티어 범위를 벗어나지 않아야 한다") {
            then("사용자 티어 ±2 범위로만 추천한다") {
                val userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
                val userKey = userId.toString()
                val userRepository = mockk<UserRepository>()
                val analysisCacheService = mockk<AnalysisCacheService>()
                val elasticsearchService = mockk<ElasticsearchService>()

                every { userRepository.existsById(userId) } returns true
                every { analysisCacheService.getRecommendationFromCache(userKey) } returns null

                val personalAnalysis = PersonalAnalysis(
                    userId = userKey,
                    analysisDate = LocalDateTime.now(),
                    totalSolved = 80,
                    currentTier = 6,
                    tagSkills = mapOf("implementation" to 0.2, "math" to 0.3),
                    solvedByDifficulty = mapOf("Bronze" to 50),
                    recentActivity = mapOf("last7days" to 3),
                    weakTags = listOf("implementation", "math"),
                    strongTags = emptyList()
                )
                every { analysisCacheService.getPersonalAnalysisFromCache(userKey) } returns personalAnalysis
                every { analysisCacheService.cacheRecommendation(userKey, any(), any()) } returns Unit

                val mockProblems = listOf(
                    ProblemMetadata("2001", "구현 연습", "Bronze I", 5, listOf("implementation"), 8000, 5),
                    ProblemMetadata("2002", "수학 문제", "Silver V", 7, listOf("math"), 4000, 7),
                    ProblemMetadata("2003", "간단한 구현", "Bronze II", 4, listOf("implementation"), 6000, 4)
                )
                every { elasticsearchService.searchProblemsByTags(listOf("implementation", "math"), any(), any()) } returns mockProblems
                every { elasticsearchService.getUserSolvedProblems(userKey) } returns emptySet()

                val recommendationService = RecommendationService(
                    userRepository,
                    analysisCacheService,
                    elasticsearchService,
                    mockProperties()
                )

                val request = RecommendationRequest(userId)
                val result = recommendationService.getPersonalizedRecommendations(request)

                result.recommendedProblems.all { it.estimatedDifficulty in 4..8 } shouldBe true
            }
        }

        `when`("분석 데이터가 없는 신규 사용자에게 추천하면") {
            then("초보자용 기본 추천을 제공한다") {
                val userId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
                val userKey = userId.toString()
                val userRepository = mockk<UserRepository>()
                val analysisCacheService = mockk<AnalysisCacheService>()
                val elasticsearchService = mockk<ElasticsearchService>()

                every { userRepository.existsById(userId) } returns true
                every { analysisCacheService.getRecommendationFromCache(userKey) } returns null
                every { analysisCacheService.getPersonalAnalysisFromCache(userKey) } returns null
                every { analysisCacheService.cacheRecommendation(userKey, any(), any()) } returns Unit

                val beginnerProblems = listOf(
                    ProblemMetadata("3001", "기초 구현", "Bronze V", 1, listOf("implementation"), 10000, 1),
                    ProblemMetadata("3002", "쉬운 수학", "Bronze IV", 2, listOf("math"), 9000, 2)
                )
                every { elasticsearchService.getBeginnerRecommendations(any()) } returns beginnerProblems

                val recommendationService = RecommendationService(
                    userRepository,
                    analysisCacheService,
                    elasticsearchService,
                    mockProperties()
                )

                val request = RecommendationRequest(userId, maxRecommendations = 2)
                val result = recommendationService.getPersonalizedRecommendations(request)

                result.userId shouldBe userId
                result.recommendedProblems.shouldHaveSize(2)
                result.recommendationStrategy shouldBe "BEGINNER_FRIENDLY"
            }
        }

        `when`("존재하지 않는 사용자에게 추천을 요청하면") {
            then("USER_NOT_FOUND 예외가 발생한다") {
                val userId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")
                val userRepository = mockk<UserRepository>()
                val analysisCacheService = mockk<AnalysisCacheService>()
                val elasticsearchService = mockk<ElasticsearchService>()

                every { userRepository.existsById(userId) } returns false

                val recommendationService = RecommendationService(
                    userRepository,
                    analysisCacheService,
                    elasticsearchService,
                    mockProperties()
                )

                val request = RecommendationRequest(userId)
                val exception = kotlin.runCatching { recommendationService.getPersonalizedRecommendations(request) }.exceptionOrNull()

                exception shouldNotBe null
                exception.shouldBeInstanceOf<CustomException>()
                (exception as CustomException).error shouldBe Error.USER_NOT_FOUND
            }
        }
    }
})
