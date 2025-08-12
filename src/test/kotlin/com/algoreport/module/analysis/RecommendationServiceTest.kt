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

/**
 * 맞춤 문제 추천 서비스 단위 테스트
 * TDD RED 단계: Mock 기반 테스트 작성
 * 
 * 요구사항:
 * - 추천 개수: 5개 문제
 * - 난이도 범위: 사용자 현재 티어 ±2
 * - 추천 기준: 가장 취약한 태그 2개
 */
class RecommendationServiceTest : BehaviorSpec() {
    
    init {
        given("맞춤 문제 추천 서비스 테스트") {
            
            `when`("유효한 사용자에게 문제 추천을 요청하면") {
                then("취약 태그 기반으로 5개 문제가 추천되어야 한다") {
                    // 독립적인 Mock 인스턴스 생성
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    // Mock 설정: 사용자 존재
                    every { userRepository.findAllActiveUserIds() } returns listOf("test-user-123")
                    every { analysisCacheService.getRecommendationFromCache("test-user-123") } returns null // 캐시 미스
                    
                    // Mock 설정: 개인 분석 데이터 (DP 취약, Graph 보통)
                    val personalAnalysis = PersonalAnalysis(
                        userId = "test-user-123",
                        analysisDate = java.time.LocalDateTime.now(),
                        totalSolved = 150,
                        currentTier = 8, // Silver
                        tagSkills = mapOf(
                            "dp" to 0.3,      // 취약 태그 1
                            "graph" to 0.4,   // 취약 태그 2  
                            "greedy" to 0.8,  // 강점 태그
                            "implementation" to 0.7
                        ),
                        solvedByDifficulty = mapOf("Silver" to 50, "Gold" to 30),
                        recentActivity = mapOf("last7days" to 5),
                        weakTags = listOf("dp", "graph"),
                        strongTags = listOf("greedy", "implementation")
                    )
                    every { analysisCacheService.getPersonalAnalysisFromCache("test-user-123") } returns personalAnalysis
                    every { analysisCacheService.cacheRecommendation("test-user-123", any(), any()) } returns Unit
                    
                    // Mock 설정: 추천 문제 데이터
                    val mockProblems = listOf(
                        ProblemMetadata("1001", "DP 기초 문제", "Gold V", 11, listOf("dp"), 5000, 11),
                        ProblemMetadata("1002", "그래프 탐색", "Silver I", 9, listOf("graph", "dfs"), 3000, 9),
                        ProblemMetadata("1003", "동적 계획법", "Silver III", 7, listOf("dp"), 2500, 7),
                        ProblemMetadata("1004", "최단 경로", "Gold IV", 12, listOf("graph", "dijkstra"), 1800, 12),
                        ProblemMetadata("1005", "DP 최적화", "Silver II", 8, listOf("dp", "optimization"), 2200, 8)
                    )
                    every { elasticsearchService.searchProblemsByTags(listOf("dp", "graph"), any(), any()) } returns mockProblems
                    every { elasticsearchService.getUserSolvedProblems("test-user-123") } returns setOf("999", "998") // 이미 푼 문제들
                    
                    val analysisProperties = mockk<com.algoreport.config.properties.AlgoreportProperties.AnalysisProperties>()
                    val externalProperties = mockk<com.algoreport.config.properties.AlgoreportProperties.ExternalProperties>()
                    val algoreportProperties = mockk<com.algoreport.config.properties.AlgoreportProperties>()
                    every { algoreportProperties.analysis } returns analysisProperties
                    every { algoreportProperties.external } returns externalProperties
                    every { analysisProperties.maxRecommendations } returns 5
                    every { analysisProperties.strongTagThreshold } returns 0.7
                    every { analysisProperties.weakTagThreshold } returns 0.5
                    every { externalProperties.baekjoonProblemBaseUrl } returns "https://www.acmicpc.net/problem/"
                    
                    val recommendationService = RecommendationService(userRepository, analysisCacheService, elasticsearchService, algoreportProperties)
                    val request = RecommendationRequest("test-user-123", maxRecommendations = 5)
                    val result = recommendationService.getPersonalizedRecommendations(request)
                    
                    result shouldNotBe null
                    result.userId shouldBe "test-user-123"
                    result.recommendedProblems.size shouldBe 5
                    result.totalRecommendations shouldBe 5
                    result.weakTags shouldBe listOf("dp", "graph")
                    result.userCurrentTier shouldBe 8
                    result.recommendationStrategy shouldBe "WEAK_TAG_BASED"
                }
                
                then("추천된 문제들은 사용자 티어 ±2 범위에 있어야 한다") {
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    every { userRepository.findAllActiveUserIds() } returns listOf("test-user-456")
                    every { analysisCacheService.getRecommendationFromCache("test-user-456") } returns null // 캐시 미스
                    
                    val personalAnalysis = PersonalAnalysis(
                        userId = "test-user-456",
                        analysisDate = java.time.LocalDateTime.now(),
                        totalSolved = 80,
                        currentTier = 6, // Bronze
                        tagSkills = mapOf("implementation" to 0.2, "math" to 0.3),
                        solvedByDifficulty = mapOf("Bronze" to 50),
                        recentActivity = mapOf("last7days" to 3),
                        weakTags = listOf("implementation", "math"),
                        strongTags = emptyList()
                    )
                    every { analysisCacheService.getPersonalAnalysisFromCache("test-user-456") } returns personalAnalysis
                    every { analysisCacheService.cacheRecommendation("test-user-456", any(), any()) } returns Unit
                    
                    val mockProblems = listOf(
                        ProblemMetadata("2001", "구현 연습", "Bronze I", 5, listOf("implementation"), 8000, 5),
                        ProblemMetadata("2002", "수학 문제", "Silver V", 7, listOf("math"), 4000, 7),
                        ProblemMetadata("2003", "간단한 구현", "Bronze II", 4, listOf("implementation"), 6000, 4)
                    )
                    every { elasticsearchService.searchProblemsByTags(listOf("implementation", "math"), any(), any()) } returns mockProblems
                    every { elasticsearchService.getUserSolvedProblems("test-user-456") } returns emptySet()
                    
                    val analysisProperties = mockk<com.algoreport.config.properties.AlgoreportProperties.AnalysisProperties>()
                    val externalProperties = mockk<com.algoreport.config.properties.AlgoreportProperties.ExternalProperties>()
                    val algoreportProperties = mockk<com.algoreport.config.properties.AlgoreportProperties>()
                    every { algoreportProperties.analysis } returns analysisProperties
                    every { algoreportProperties.external } returns externalProperties
                    every { analysisProperties.maxRecommendations } returns 5
                    every { analysisProperties.strongTagThreshold } returns 0.7
                    every { analysisProperties.weakTagThreshold } returns 0.5
                    every { externalProperties.baekjoonProblemBaseUrl } returns "https://www.acmicpc.net/problem/"
                    
                    val recommendationService = RecommendationService(userRepository, analysisCacheService, elasticsearchService, algoreportProperties)
                    val request = RecommendationRequest("test-user-456")
                    val result = recommendationService.getPersonalizedRecommendations(request)
                    
                    // Bronze 티어(6) ±2 = 4~8 범위
                    result.recommendedProblems.forEach { problem ->
                        (problem.estimatedDifficulty >= 4 && problem.estimatedDifficulty <= 8) shouldBe true
                    }
                }
                
                then("추천 이유가 명확히 제시되어야 한다") {
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    every { userRepository.findAllActiveUserIds() } returns listOf("test-user-789")
                    every { analysisCacheService.getRecommendationFromCache("test-user-789") } returns null // 캐시 미스
                    
                    val personalAnalysis = PersonalAnalysis(
                        userId = "test-user-789",
                        analysisDate = java.time.LocalDateTime.now(),
                        totalSolved = 200,
                        currentTier = 12, // Gold
                        tagSkills = mapOf(
                            "string" to 0.1,    // 가장 취약
                            "geometry" to 0.2,  // 두 번째 취약
                            "dp" to 0.9
                        ),
                        solvedByDifficulty = mapOf("Gold" to 80),
                        recentActivity = mapOf("last7days" to 7),
                        weakTags = listOf("string", "geometry"),
                        strongTags = listOf("dp")
                    )
                    every { analysisCacheService.getPersonalAnalysisFromCache("test-user-789") } returns personalAnalysis
                    every { analysisCacheService.cacheRecommendation("test-user-789", any(), any()) } returns Unit
                    
                    val mockProblems = listOf(
                        ProblemMetadata("3001", "문자열 처리", "Gold III", 13, listOf("string"), 1500, 13),
                        ProblemMetadata("3002", "기하 문제", "Gold V", 11, listOf("geometry"), 1200, 11)
                    )
                    every { elasticsearchService.searchProblemsByTags(listOf("string", "geometry"), any(), any()) } returns mockProblems
                    every { elasticsearchService.getUserSolvedProblems("test-user-789") } returns emptySet()
                    
                    val analysisProperties = mockk<com.algoreport.config.properties.AlgoreportProperties.AnalysisProperties>()
                    val externalProperties = mockk<com.algoreport.config.properties.AlgoreportProperties.ExternalProperties>()
                    val algoreportProperties = mockk<com.algoreport.config.properties.AlgoreportProperties>()
                    every { algoreportProperties.analysis } returns analysisProperties
                    every { algoreportProperties.external } returns externalProperties
                    every { analysisProperties.maxRecommendations } returns 5
                    every { analysisProperties.strongTagThreshold } returns 0.7
                    every { analysisProperties.weakTagThreshold } returns 0.5
                    every { externalProperties.baekjoonProblemBaseUrl } returns "https://www.acmicpc.net/problem/"
                    
                    val recommendationService = RecommendationService(userRepository, analysisCacheService, elasticsearchService, algoreportProperties)
                    val request = RecommendationRequest("test-user-789")
                    val result = recommendationService.getPersonalizedRecommendations(request)
                    
                    result.recommendedProblems.forEach { problem ->
                        problem.recommendationReason shouldNotBe null
                        problem.recommendationReason.isNotEmpty() shouldBe true
                        problem.weakTag.isNotEmpty() shouldBe true
                        (problem.weakTag == "string" || problem.weakTag == "geometry") shouldBe true
                    }
                }
            }
            
            `when`("존재하지 않는 사용자로 요청하면") {
                then("USER_NOT_FOUND 예외가 발생해야 한다") {
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    every { userRepository.findAllActiveUserIds() } returns emptyList()
                    
                    val analysisProperties = mockk<com.algoreport.config.properties.AlgoreportProperties.AnalysisProperties>()
                    val externalProperties = mockk<com.algoreport.config.properties.AlgoreportProperties.ExternalProperties>()
                    val algoreportProperties = mockk<com.algoreport.config.properties.AlgoreportProperties>()
                    every { algoreportProperties.analysis } returns analysisProperties
                    every { algoreportProperties.external } returns externalProperties
                    every { analysisProperties.maxRecommendations } returns 5
                    every { analysisProperties.strongTagThreshold } returns 0.7
                    every { analysisProperties.weakTagThreshold } returns 0.5
                    every { externalProperties.baekjoonProblemBaseUrl } returns "https://www.acmicpc.net/problem/"
                    
                    val recommendationService = RecommendationService(userRepository, analysisCacheService, elasticsearchService, algoreportProperties)
                    val request = RecommendationRequest("nonexistent-user")
                    
                    val exception = try {
                        recommendationService.getPersonalizedRecommendations(request)
                        null
                    } catch (e: Exception) {
                        e
                    }
                    
                    exception shouldNotBe null
                    exception.shouldBeInstanceOf<CustomException>()
                    (exception as CustomException).error shouldBe Error.USER_NOT_FOUND
                }
            }
            
            `when`("분석 데이터가 없는 신규 사용자로 요청하면") {
                then("기본 추천이 제공되어야 한다") {
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    every { userRepository.findAllActiveUserIds() } returns listOf("new-user-999")
                    every { analysisCacheService.getRecommendationFromCache("new-user-999") } returns null // 캐시 미스
                    every { analysisCacheService.getPersonalAnalysisFromCache("new-user-999") } returns null
                    
                    // 신규 사용자용 기본 추천 문제
                    val beginnerProblems = listOf(
                        ProblemMetadata("1000", "A+B", "Bronze V", 1, listOf("implementation"), 50000, 1),
                        ProblemMetadata("1001", "A-B", "Bronze V", 1, listOf("implementation"), 30000, 1)
                    )
                    every { elasticsearchService.getBeginnerRecommendations(5) } returns beginnerProblems
                    every { analysisCacheService.cacheRecommendation("new-user-999", any(), any()) } returns Unit
                    
                    val analysisProperties = mockk<com.algoreport.config.properties.AlgoreportProperties.AnalysisProperties>()
                    val externalProperties = mockk<com.algoreport.config.properties.AlgoreportProperties.ExternalProperties>()
                    val algoreportProperties = mockk<com.algoreport.config.properties.AlgoreportProperties>()
                    every { algoreportProperties.analysis } returns analysisProperties
                    every { algoreportProperties.external } returns externalProperties
                    every { analysisProperties.maxRecommendations } returns 5
                    every { analysisProperties.strongTagThreshold } returns 0.7
                    every { analysisProperties.weakTagThreshold } returns 0.5
                    every { externalProperties.baekjoonProblemBaseUrl } returns "https://www.acmicpc.net/problem/"
                    
                    val recommendationService = RecommendationService(userRepository, analysisCacheService, elasticsearchService, algoreportProperties)
                    val request = RecommendationRequest("new-user-999")
                    val result = recommendationService.getPersonalizedRecommendations(request)
                    
                    result shouldNotBe null
                    result.userId shouldBe "new-user-999"
                    result.recommendationStrategy shouldBe "BEGINNER_FRIENDLY"
                    result.userCurrentTier shouldBe 0 // Unrated
                    result.message shouldBe "초보자를 위한 기본 문제들을 추천드려요!"
                }
            }
            
            `when`("이미 많은 문제를 푼 고수 사용자로 요청하면") {
                then("고난이도 문제가 추천되어야 한다") {
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    every { userRepository.findAllActiveUserIds() } returns listOf("expert-user-777")
                    every { analysisCacheService.getRecommendationFromCache("expert-user-777") } returns null // 캐시 미스
                    
                    val expertAnalysis = PersonalAnalysis(
                        userId = "expert-user-777",
                        analysisDate = java.time.LocalDateTime.now(),
                        totalSolved = 1500,
                        currentTier = 16, // Platinum
                        tagSkills = mapOf(
                            "advanced_data_structures" to 0.3, // 취약
                            "number_theory" to 0.4,           // 취약
                            "dp" to 0.95,
                            "graph" to 0.9
                        ),
                        solvedByDifficulty = mapOf("Platinum" to 200, "Gold" to 500),
                        recentActivity = mapOf("last7days" to 15),
                        weakTags = listOf("advanced_data_structures", "number_theory"),
                        strongTags = listOf("dp", "graph")
                    )
                    every { analysisCacheService.getPersonalAnalysisFromCache("expert-user-777") } returns expertAnalysis
                    every { analysisCacheService.cacheRecommendation("expert-user-777", any(), any()) } returns Unit
                    
                    val hardProblems = listOf(
                        ProblemMetadata("4001", "세그먼트 트리", "Platinum IV", 18, listOf("advanced_data_structures"), 300, 18),
                        ProblemMetadata("4002", "정수론 심화", "Platinum V", 17, listOf("number_theory"), 250, 17)
                    )
                    every { elasticsearchService.searchProblemsByTags(listOf("advanced_data_structures", "number_theory"), any(), any()) } returns hardProblems
                    every { elasticsearchService.getUserSolvedProblems("expert-user-777") } returns (1..1000).map { it.toString() }.toSet()
                    
                    val analysisProperties = mockk<com.algoreport.config.properties.AlgoreportProperties.AnalysisProperties>()
                    val externalProperties = mockk<com.algoreport.config.properties.AlgoreportProperties.ExternalProperties>()
                    val algoreportProperties = mockk<com.algoreport.config.properties.AlgoreportProperties>()
                    every { algoreportProperties.analysis } returns analysisProperties
                    every { algoreportProperties.external } returns externalProperties
                    every { analysisProperties.maxRecommendations } returns 5
                    every { analysisProperties.strongTagThreshold } returns 0.7
                    every { analysisProperties.weakTagThreshold } returns 0.5
                    every { externalProperties.baekjoonProblemBaseUrl } returns "https://www.acmicpc.net/problem/"
                    
                    val recommendationService = RecommendationService(userRepository, analysisCacheService, elasticsearchService, algoreportProperties)
                    val request = RecommendationRequest("expert-user-777")
                    val result = recommendationService.getPersonalizedRecommendations(request)
                    
                    result.recommendedProblems.forEach { problem ->
                        (problem.estimatedDifficulty >= 14) shouldBe true // Platinum 티어(16) ±2 = 14~18
                    }
                    result.recommendationStrategy shouldBe "WEAK_TAG_BASED"
                }
            }
        }
    }
}