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

/**
 * 개인 학습 대시보드 서비스 단위 테스트
 * TDD RED 단계: Mock 기반 단위 테스트 작성
 * 
 * 테스트 전략: 모든 외부 의존성 Mock으로 대체
 * - UserRepository: Mock
 * - AnalysisCacheService: Mock
 * - ElasticsearchService: Mock
 * - 빠른 실행: < 5초
 * - 비즈니스 로직만 검증
 */
class PersonalDashboardServiceUnitTest : BehaviorSpec() {
    
    init {
        given("개인 학습 대시보드 서비스 단위 테스트") {
            
            `when`("유효한 사용자 ID로 대시보드 데이터를 요청하면") {
                then("개인 대시보드 데이터가 반환되어야 한다") {
                    // 독립적인 Mock 인스턴스 생성 (Mock 격리 원칙)
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    // Mock 설정
                    every { userRepository.findAllActiveUserIds() } returns listOf("test-user-123")
                    every { analysisCacheService.getPersonalAnalysisFromCache("test-user-123") } returns null
                    every { elasticsearchService.aggregateTagSkills("test-user-123") } returns mapOf("dp" to 0.8, "graph" to 0.6)
                    every { elasticsearchService.aggregateSolvedByDifficulty("test-user-123") } returns mapOf("Gold" to 45, "Silver" to 55, "Bronze" to 50)
                    every { elasticsearchService.aggregateRecentActivity("test-user-123") } returns mapOf(
                        "2024-08-01" to 3, "2024-08-02" to 2, "2024-08-03" to 1
                    )
                    
                    val personalDashboardService = PersonalDashboardService(userRepository, analysisCacheService, elasticsearchService)
                    val result = personalDashboardService.getPersonalDashboard("test-user-123")
                    
                    result shouldNotBe null
                    result.userId shouldBe "test-user-123"
                    result.totalSolved shouldBe 150 // 예상 값
                    result.currentTier shouldBe 12 // 골드 티어
                }
                
                then("잔디밭 히트맵 데이터가 포함되어야 한다") {
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    every { userRepository.findAllActiveUserIds() } returns listOf("test-user-123")
                    every { analysisCacheService.getPersonalAnalysisFromCache("test-user-123") } returns null
                    every { elasticsearchService.aggregateTagSkills("test-user-123") } returns mapOf("dp" to 0.8)
                    every { elasticsearchService.aggregateSolvedByDifficulty("test-user-123") } returns mapOf("Gold" to 45)
                    every { elasticsearchService.aggregateRecentActivity("test-user-123") } returns mapOf(
                        "2024-01-01" to 3, "2024-01-02" to 1
                    )
                    
                    val personalDashboardService = PersonalDashboardService(userRepository, analysisCacheService, elasticsearchService)
                    val result = personalDashboardService.getPersonalDashboard("test-user-123")
                    
                    result.heatmapData shouldNotBe null
                    result.heatmapData.size shouldBe 365 // 최근 1년 데이터
                    result.heatmapData.keys.first().shouldBeInstanceOf<String>() // "2024-01-01" 형식
                    result.heatmapData.values.first().shouldBeInstanceOf<Int>() // 문제 해결 수
                }
                
                then("알고리즘 태그별 숙련도 데이터가 포함되어야 한다") {
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    every { userRepository.findAllActiveUserIds() } returns listOf("test-user-123")
                    every { analysisCacheService.getPersonalAnalysisFromCache("test-user-123") } returns null
                    every { elasticsearchService.aggregateTagSkills("test-user-123") } returns mapOf(
                        "dp" to 0.8, "graph" to 0.6, "greedy" to 0.9, "implementation" to 0.7,
                        "math" to 0.5, "string" to 0.3, "geometry" to 0.2, "data_structures" to 0.4
                    )
                    every { elasticsearchService.aggregateSolvedByDifficulty("test-user-123") } returns mapOf("Gold" to 50)
                    every { elasticsearchService.aggregateRecentActivity("test-user-123") } returns mapOf("2024-08-01" to 2)
                    
                    val personalDashboardService = PersonalDashboardService(userRepository, analysisCacheService, elasticsearchService)
                    val result = personalDashboardService.getPersonalDashboard("test-user-123")
                    
                    result.tagSkillsRadar shouldNotBe null
                    result.tagSkillsRadar.size shouldBe 8 // 주요 8개 태그
                    result.tagSkillsRadar.keys.any { it == "dp" } shouldBe true
                    result.tagSkillsRadar.keys.any { it == "graph" } shouldBe true
                    result.tagSkillsRadar.values.all { value -> value >= 0.0 && value <= 1.0 } shouldBe true // 0-1 정규화
                }
            }
            
            `when`("존재하지 않는 사용자 ID로 요청하면") {
                then("USER_NOT_FOUND 예외가 발생해야 한다") {
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    // Mock 설정: 존재하지 않는 사용자
                    every { userRepository.findAllActiveUserIds() } returns emptyList()
                    
                    val personalDashboardService = PersonalDashboardService(userRepository, analysisCacheService, elasticsearchService)
                    
                    val exception = try {
                        personalDashboardService.getPersonalDashboard("nonexistent-user")
                        null
                    } catch (e: Exception) {
                        e
                    }
                    
                    exception shouldNotBe null
                    exception.shouldBeInstanceOf<CustomException>()
                    (exception as CustomException).error shouldBe Error.USER_NOT_FOUND
                }
            }
            
            `when`("캐시된 데이터로 요청하면") {
                then("캐시된 데이터가 반환되고 응답이 빨라야 한다") {
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    // Mock 설정: 캐시된 데이터 존재
                    every { userRepository.findAllActiveUserIds() } returns listOf("test-user-123")
                    val cachedAnalysis = PersonalAnalysis(
                        userId = "test-user-123",
                        analysisDate = LocalDateTime.now().minusMinutes(30),
                        totalSolved = 150,
                        currentTier = 12,
                        tagSkills = mapOf("dp" to 0.8),
                        solvedByDifficulty = mapOf("Gold" to 45),
                        recentActivity = mapOf("last7days" to 12),
                        weakTags = emptyList(),
                        strongTags = listOf("dp")
                    )
                    every { analysisCacheService.getPersonalAnalysisFromCache("test-user-123") } returns cachedAnalysis
                    
                    val personalDashboardService = PersonalDashboardService(userRepository, analysisCacheService, elasticsearchService)
                    val result = personalDashboardService.getPersonalDashboard("test-user-123")
                    
                    result.cacheHit shouldBe true
                    (result.responseTimeMs < 50L) shouldBe true
                    result.userId shouldBe "test-user-123"
                }
            }
            
            `when`("분석 데이터가 없는 신규 사용자로 요청하면") {
                then("기본값으로 채워진 대시보드 데이터가 반환되어야 한다") {
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    // Mock 설정: 신규 사용자 (데이터 없음)
                    every { userRepository.findAllActiveUserIds() } returns listOf("new-user-456")
                    every { analysisCacheService.getPersonalAnalysisFromCache("new-user-456") } returns null
                    every { elasticsearchService.aggregateTagSkills("new-user-456") } returns emptyMap()
                    every { elasticsearchService.aggregateSolvedByDifficulty("new-user-456") } returns emptyMap()
                    every { elasticsearchService.aggregateRecentActivity("new-user-456") } returns emptyMap()
                    
                    val personalDashboardService = PersonalDashboardService(userRepository, analysisCacheService, elasticsearchService)
                    val result = personalDashboardService.getPersonalDashboard("new-user-456")
                    
                    result shouldNotBe null
                    result.userId shouldBe "new-user-456"
                    result.totalSolved shouldBe 0
                    result.currentTier shouldBe 0 // Unrated
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
                then("최신 데이터가 조회되고 캐시가 업데이트되어야 한다") {
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    every { userRepository.findAllActiveUserIds() } returns listOf("refresh-user-789")
                    every { analysisCacheService.getPersonalAnalysisFromCache("refresh-user-789") } returns null
                    every { elasticsearchService.aggregateTagSkills("refresh-user-789") } returns mapOf("dp" to 0.9)
                    every { elasticsearchService.aggregateSolvedByDifficulty("refresh-user-789") } returns mapOf("Gold" to 100)
                    every { elasticsearchService.aggregateRecentActivity("refresh-user-789") } returns mapOf("2024-08-04" to 5)
                    
                    val personalDashboardService = PersonalDashboardService(userRepository, analysisCacheService, elasticsearchService)
                    val result = personalDashboardService.getPersonalDashboard("refresh-user-789", forceRefresh = true)
                    
                    result shouldNotBe null
                    result.userId shouldBe "refresh-user-789"
                    result.cacheHit shouldBe false
                    result.lastUpdated.isAfter(LocalDateTime.now().minusMinutes(1)) shouldBe true
                }
            }
        }
    }
}