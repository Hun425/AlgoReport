package com.algoreport.module.analysis

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.module.studygroup.StudyGroupRepository
import com.algoreport.module.user.UserRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk

/**
 * 스터디 그룹 대시보드 서비스 단위 테스트
 * TDD RED 단계: Mock 기반 테스트 작성
 * 
 * 요구사항:
 * - 그룹 통계: 평균 티어, 총 문제 해결 수, 활성 멤버 비율
 * - 멤버 분석: 개별 멤버 현황, 기여도 순위, 활동도
 * - 그룹 인사이트: 강점/취약 태그, 주간 진행도, 상위 성과자
 */
class StudyGroupDashboardServiceTest : BehaviorSpec() {
    
    init {
        given("스터디 그룹 대시보드 서비스 테스트") {
            
            `when`("존재하는 그룹의 대시보드를 요청하면") {
                then("그룹 통계와 멤버 정보가 포함된 대시보드를 반환해야 한다") {
                    // 독립적인 Mock 인스턴스 생성
                    val studyGroupRepository = mockk<StudyGroupRepository>()
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    // Mock 설정: 그룹 존재 및 멤버 정보
                    every { studyGroupRepository.existsById("group-123") } returns true
                    every { studyGroupRepository.findGroupMemberIds("group-123") } returns listOf("user1", "user2", "user3")
                    every { analysisCacheService.getGroupDashboardFromCache("group-123") } returns null // 캐시 미스
                    
                    // Mock 설정: 멤버별 개인 분석 데이터
                    val user1Analysis = PersonalAnalysis(
                        userId = "user1",
                        analysisDate = java.time.LocalDateTime.now(),
                        totalSolved = 150,
                        currentTier = 12, // Gold
                        tagSkills = mapOf("dp" to 0.8, "graph" to 0.6, "greedy" to 0.9, "math" to 0.3),
                        solvedByDifficulty = mapOf("Gold" to 50, "Silver" to 100),
                        recentActivity = mapOf("last7days" to 7),
                        weakTags = listOf("math"),
                        strongTags = listOf("greedy", "graph")
                    )
                    
                    val user2Analysis = PersonalAnalysis(
                        userId = "user2",
                        analysisDate = java.time.LocalDateTime.now(),
                        totalSolved = 80,
                        currentTier = 8, // Silver
                        tagSkills = mapOf("implementation" to 0.7, "math" to 0.5, "dp" to 0.5),
                        solvedByDifficulty = mapOf("Silver" to 60, "Bronze" to 20),
                        recentActivity = mapOf("last7days" to 3),
                        weakTags = listOf("math", "graph"),
                        strongTags = listOf("implementation")
                    )
                    
                    val user3Analysis = PersonalAnalysis(
                        userId = "user3",
                        analysisDate = java.time.LocalDateTime.now(),
                        totalSolved = 200,
                        currentTier = 16, // Platinum
                        tagSkills = mapOf("dp" to 0.9, "graph" to 0.8, "tree" to 0.7, "math" to 0.4),
                        solvedByDifficulty = mapOf("Platinum" to 50, "Gold" to 80, "Silver" to 70),
                        recentActivity = mapOf("last7days" to 10),
                        weakTags = listOf("math"),
                        strongTags = listOf("dp", "graph", "tree")
                    )
                    
                    every { analysisCacheService.getPersonalAnalysisFromCache("user1") } returns user1Analysis
                    every { analysisCacheService.getPersonalAnalysisFromCache("user2") } returns user2Analysis
                    every { analysisCacheService.getPersonalAnalysisFromCache("user3") } returns user3Analysis
                    every { analysisCacheService.cacheGroupDashboard("group-123", any(), any()) } returns Unit
                    
                    // Mock 설정: 그룹 정보
                    every { elasticsearchService.getStudyGroupInfo("group-123") } returns mapOf(
                        "name" to "알고리즘 스터디",
                        "memberCount" to 3
                    )
                    
                    val dashboardService = StudyGroupDashboardService(studyGroupRepository, userRepository, analysisCacheService, elasticsearchService)
                    val request = StudyGroupDashboardRequest("group-123")
                    val result = dashboardService.getStudyGroupDashboard(request)
                    
                    // 기본 응답 검증
                    result shouldNotBe null
                    result.groupId shouldBe "group-123"
                    result.groupName shouldBe "알고리즘 스터디"
                    result.memberCount shouldBe 3
                    result.memberDetails.size shouldBe 3
                    
                    // 그룹 통계 검증
                    result.groupStats.totalMembers shouldBe 3
                    result.groupStats.activeMembers shouldBe 3 // 모두 최근 활동 있음
                    result.groupStats.totalSolvedByGroup shouldBe 430 // 150 + 80 + 200
                    result.groupStats.averageTier shouldBe 12.0 // (12 + 8 + 16) / 3
                    result.groupStats.memberActivityRate shouldBe 1.0 // 100% 활성
                    
                    // 상위 성과자 검증 (총 해결 문제 수 기준)
                    result.groupStats.topPerformers.size shouldBe 3
                    result.groupStats.topPerformers[0].userId shouldBe "user3" // 200문제
                    result.groupStats.topPerformers[1].userId shouldBe "user1" // 150문제
                    result.groupStats.topPerformers[2].userId shouldBe "user2" // 80문제
                    
                    // 그룹 강점/취약 태그 검증
                    result.groupStats.groupStrongTags.contains("dp") shouldBe true
                    result.groupStats.groupWeakTags.contains("math") shouldBe true // math는 평균 0.5로 취약
                }
                
                then("캐시된 데이터가 있으면 캐시에서 반환해야 한다") {
                    val studyGroupRepository = mockk<StudyGroupRepository>()
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    every { studyGroupRepository.existsById("group-456") } returns true
                    
                    // Mock 설정: 캐시 히트
                    val cachedDashboard = StudyGroupDashboardResponse(
                        groupId = "group-456",
                        groupName = "캐시된 그룹",
                        memberCount = 2,
                        groupStats = StudyGroupStats(
                            averageTier = 10.0,
                            totalSolvedByGroup = 100,
                            activeMembers = 2,
                            totalMembers = 2,
                            topPerformers = emptyList(),
                            groupWeakTags = listOf("dp"),
                            groupStrongTags = listOf("greedy"),
                            weeklyProgress = mapOf("2024-08-05" to 5),
                            memberActivityRate = 1.0
                        ),
                        memberDetails = emptyList(),
                        cacheHit = false // 원본은 false, 서비스에서 true로 변경될 것
                    )
                    every { analysisCacheService.getGroupDashboardFromCache("group-456") } returns cachedDashboard
                    
                    val dashboardService = StudyGroupDashboardService(studyGroupRepository, userRepository, analysisCacheService, elasticsearchService)
                    val request = StudyGroupDashboardRequest("group-456")
                    val result = dashboardService.getStudyGroupDashboard(request)
                    
                    result.cacheHit shouldBe true
                    result.dataSource shouldBe "CACHE"
                    result.groupName shouldBe "캐시된 그룹"
                }
                
                then("강제 갱신 플래그가 true면 캐시를 무시하고 실시간 데이터를 사용해야 한다") {
                    val studyGroupRepository = mockk<StudyGroupRepository>()
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    every { studyGroupRepository.existsById("group-789") } returns true
                    every { studyGroupRepository.findGroupMemberIds("group-789") } returns listOf("user1")
                    every { analysisCacheService.getGroupDashboardFromCache("group-789") } returns null // 캐시 확인 안함
                    
                    val userAnalysis = PersonalAnalysis(
                        userId = "user1",
                        analysisDate = java.time.LocalDateTime.now(),
                        totalSolved = 50,
                        currentTier = 6,
                        tagSkills = mapOf("implementation" to 0.5),
                        solvedByDifficulty = mapOf("Bronze" to 50),
                        recentActivity = mapOf("last7days" to 2),
                        weakTags = listOf("dp"),
                        strongTags = listOf("implementation")
                    )
                    
                    every { analysisCacheService.getPersonalAnalysisFromCache("user1") } returns userAnalysis
                    every { analysisCacheService.cacheGroupDashboard("group-789", any(), any()) } returns Unit
                    every { elasticsearchService.getStudyGroupInfo("group-789") } returns mapOf(
                        "name" to "갱신된 그룹",
                        "memberCount" to 1
                    )
                    
                    val dashboardService = StudyGroupDashboardService(studyGroupRepository, userRepository, analysisCacheService, elasticsearchService)
                    val request = StudyGroupDashboardRequest("group-789", forceRefresh = true)
                    val result = dashboardService.getStudyGroupDashboard(request)
                    
                    result.cacheHit shouldBe false
                    result.dataSource shouldBe "LIVE"
                    result.groupName shouldBe "갱신된 그룹"
                }
            }
            
            `when`("존재하지 않는 그룹의 대시보드를 요청하면") {
                then("STUDY_GROUP_NOT_FOUND 예외가 발생해야 한다") {
                    val studyGroupRepository = mockk<StudyGroupRepository>()
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    every { studyGroupRepository.existsById("nonexistent-group") } returns false
                    
                    val dashboardService = StudyGroupDashboardService(studyGroupRepository, userRepository, analysisCacheService, elasticsearchService)
                    val request = StudyGroupDashboardRequest("nonexistent-group")
                    
                    val exception = try {
                        dashboardService.getStudyGroupDashboard(request)
                        null
                    } catch (e: Exception) {
                        e
                    }
                    
                    exception shouldNotBe null
                    exception.shouldBeInstanceOf<CustomException>()
                    (exception as CustomException).error shouldBe Error.STUDY_GROUP_NOT_FOUND
                }
            }
            
            `when`("멤버가 없는 빈 그룹의 대시보드를 요청하면") {
                then("기본값으로 설정된 빈 대시보드를 반환해야 한다") {
                    val studyGroupRepository = mockk<StudyGroupRepository>()
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    every { studyGroupRepository.existsById("empty-group") } returns true
                    every { studyGroupRepository.findGroupMemberIds("empty-group") } returns emptyList()
                    every { analysisCacheService.getGroupDashboardFromCache("empty-group") } returns null
                    every { analysisCacheService.cacheGroupDashboard("empty-group", any(), any()) } returns Unit
                    every { elasticsearchService.getStudyGroupInfo("empty-group") } returns mapOf(
                        "name" to "빈 그룹",
                        "memberCount" to 0
                    )
                    
                    val dashboardService = StudyGroupDashboardService(studyGroupRepository, userRepository, analysisCacheService, elasticsearchService)
                    val request = StudyGroupDashboardRequest("empty-group")
                    val result = dashboardService.getStudyGroupDashboard(request)
                    
                    result.groupId shouldBe "empty-group"
                    result.memberCount shouldBe 0
                    result.memberDetails.size shouldBe 0
                    result.groupStats.totalMembers shouldBe 0
                    result.groupStats.activeMembers shouldBe 0
                    result.groupStats.totalSolvedByGroup shouldBe 0
                    result.groupStats.averageTier shouldBe 0.0
                    result.message shouldBe "아직 멤버가 없는 그룹입니다. 멤버를 초대해보세요!"
                }
            }
            
            `when`("분석 데이터가 없는 신규 멤버들만 있는 그룹의 대시보드를 요청하면") {
                then("기본 분석 데이터로 대시보드를 생성해야 한다") {
                    val studyGroupRepository = mockk<StudyGroupRepository>()
                    val userRepository = mockk<UserRepository>()
                    val analysisCacheService = mockk<AnalysisCacheService>()
                    val elasticsearchService = mockk<ElasticsearchService>()
                    
                    every { studyGroupRepository.existsById("new-group") } returns true
                    every { studyGroupRepository.findGroupMemberIds("new-group") } returns listOf("new-user1", "new-user2")
                    every { analysisCacheService.getGroupDashboardFromCache("new-group") } returns null
                    
                    // 신규 사용자들은 분석 데이터가 없음
                    every { analysisCacheService.getPersonalAnalysisFromCache("new-user1") } returns null
                    every { analysisCacheService.getPersonalAnalysisFromCache("new-user2") } returns null
                    every { analysisCacheService.cacheGroupDashboard("new-group", any(), any()) } returns Unit
                    
                    every { elasticsearchService.getStudyGroupInfo("new-group") } returns mapOf(
                        "name" to "신규 그룹",
                        "memberCount" to 2
                    )
                    
                    val dashboardService = StudyGroupDashboardService(studyGroupRepository, userRepository, analysisCacheService, elasticsearchService)
                    val request = StudyGroupDashboardRequest("new-group")
                    val result = dashboardService.getStudyGroupDashboard(request)
                    
                    result.groupStats.totalMembers shouldBe 2
                    result.groupStats.activeMembers shouldBe 0 // 활동 데이터 없음
                    result.groupStats.totalSolvedByGroup shouldBe 0
                    result.groupStats.averageTier shouldBe 0.0
                    result.groupStats.memberActivityRate shouldBe 0.0
                    result.message shouldBe "그룹 멤버들이 solved.ac 계정을 연동하면 더 자세한 통계를 확인할 수 있습니다."
                }
            }
        }
    }
}