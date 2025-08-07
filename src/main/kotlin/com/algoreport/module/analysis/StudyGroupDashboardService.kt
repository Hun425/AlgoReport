package com.algoreport.module.analysis

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.module.studygroup.StudyGroupRepository
import com.algoreport.module.user.UserRepository
import java.time.LocalDateTime

/**
 * 스터디 그룹 대시보드 서비스
 * 
 * TDD RED 단계: 기본 구현체 (테스트 실패 유도)
 * 
 * 그룹별 학습 현황 분석 및 대시보드 데이터 제공 서비스입니다.
 * 그룹 통계, 멤버 분석, 강점/취약점 인사이트를 제공합니다.
 * 
 * @property studyGroupRepository 스터디 그룹 데이터 접근을 위한 리포지토리
 * @property userRepository 사용자 데이터 접근을 위한 리포지토리
 * @property analysisCacheService Redis 기반 분석 결과 캐시 서비스
 * @property elasticsearchService 검색 및 집계 서비스
 * 
 * @author 채기훈
 * @since 2025-08-05
 */
class StudyGroupDashboardService(
    private val studyGroupRepository: StudyGroupRepository,
    private val userRepository: UserRepository,
    private val analysisCacheService: AnalysisCacheService,
    private val elasticsearchService: ElasticsearchService
) {
    
    /**
     * 스터디 그룹 대시보드 데이터 조회
     * 
     * TDD GREEN 단계: 실제 비즈니스 로직 구현
     * 
     * @param request 대시보드 요청 데이터
     * @return 스터디 그룹 대시보드 응답 데이터
     * @throws CustomException 그룹을 찾을 수 없는 경우
     */
    fun getStudyGroupDashboard(request: StudyGroupDashboardRequest): StudyGroupDashboardResponse {
        val startTime = System.currentTimeMillis()
        
        // 1. 그룹 존재 여부 검증
        if (!studyGroupRepository.existsById(request.groupId)) {
            throw CustomException(Error.STUDY_GROUP_NOT_FOUND)
        }
        
        // 2. 캐시 확인 (강제 갱신이 아닌 경우)
        if (!request.forceRefresh) {
            val cachedDashboard = analysisCacheService.getGroupDashboardFromCache(request.groupId)
            if (cachedDashboard != null) {
                return cachedDashboard.copy(
                    cacheHit = true,
                    dataSource = "CACHE",
                    responseTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }
        
        // 3. 그룹 기본 정보 조회
        val groupInfo = elasticsearchService.getStudyGroupInfo(request.groupId)
        val groupName = groupInfo["name"] as? String ?: "Unknown Group"
        
        // 4. 그룹 멤버 ID 목록 조회
        val memberIds = studyGroupRepository.findGroupMemberIds(request.groupId)
        
        // 5. 멤버가 없는 경우 빈 대시보드 반환
        if (memberIds.isEmpty()) {
            val emptyDashboard = StudyGroupDashboardResponse(
                groupId = request.groupId,
                groupName = groupName,
                memberCount = 0,
                groupStats = StudyGroupStats(
                    averageTier = 0.0,
                    totalSolvedByGroup = 0,
                    activeMembers = 0,
                    totalMembers = 0,
                    topPerformers = emptyList(),
                    groupWeakTags = emptyList(),
                    groupStrongTags = emptyList(),
                    weeklyProgress = emptyMap(),
                    memberActivityRate = 0.0
                ),
                memberDetails = emptyList(),
                cacheHit = false,
                responseTimeMs = System.currentTimeMillis() - startTime,
                dataSource = "LIVE",
                lastUpdated = LocalDateTime.now(),
                message = "아직 멤버가 없는 그룹입니다. 멤버를 초대해보세요!"
            )
            analysisCacheService.cacheGroupDashboard(request.groupId, emptyDashboard)
            return emptyDashboard
        }
        
        // 6. 멤버별 개인 분석 데이터 수집
        val memberAnalysisData = memberIds.mapNotNull { memberId ->
            analysisCacheService.getPersonalAnalysisFromCache(memberId)
        }
        
        // 7. 신규 그룹 (분석 데이터가 없는 경우) 처리
        if (memberAnalysisData.isEmpty()) {
            val newGroupDashboard = StudyGroupDashboardResponse(
                groupId = request.groupId,
                groupName = groupName,
                memberCount = memberIds.size,
                groupStats = StudyGroupStats(
                    averageTier = 0.0,
                    totalSolvedByGroup = 0,
                    activeMembers = 0,
                    totalMembers = memberIds.size,
                    topPerformers = emptyList(),
                    groupWeakTags = emptyList(),
                    groupStrongTags = emptyList(),
                    weeklyProgress = emptyMap(),
                    memberActivityRate = 0.0
                ),
                memberDetails = emptyList(),
                cacheHit = false,
                responseTimeMs = System.currentTimeMillis() - startTime,
                dataSource = "LIVE",
                lastUpdated = LocalDateTime.now(),
                message = "그룹 멤버들이 solved.ac 계정을 연동하면 더 자세한 통계를 확인할 수 있습니다."
            )
            analysisCacheService.cacheGroupDashboard(request.groupId, newGroupDashboard)
            return newGroupDashboard
        }
        
        // 8. 그룹 통계 계산
        val groupStats = calculateGroupStats(memberAnalysisData, memberIds.size)
        val memberDetails = createMemberDetails(memberAnalysisData)
        
        // 9. 대시보드 응답 생성
        val dashboard = StudyGroupDashboardResponse(
            groupId = request.groupId,
            groupName = groupName,
            memberCount = memberIds.size,
            groupStats = groupStats,
            memberDetails = memberDetails,
            cacheHit = false,
            responseTimeMs = System.currentTimeMillis() - startTime,
            dataSource = "LIVE",
            lastUpdated = LocalDateTime.now(),
            message = null
        )
        
        // 10. 캐시에 저장
        analysisCacheService.cacheGroupDashboard(request.groupId, dashboard)
        
        return dashboard
    }
    
    /**
     * 그룹 통계 계산
     */
    private fun calculateGroupStats(memberAnalysisData: List<PersonalAnalysis>, totalMembers: Int): StudyGroupStats {
        if (memberAnalysisData.isEmpty()) {
            return StudyGroupStats(
                averageTier = 0.0,
                totalSolvedByGroup = 0,
                activeMembers = 0,
                totalMembers = totalMembers,
                topPerformers = emptyList(),
                groupWeakTags = emptyList(),
                groupStrongTags = emptyList(),
                weeklyProgress = emptyMap(),
                memberActivityRate = 0.0
            )
        }
        
        // 평균 티어 계산
        val averageTier = memberAnalysisData.map { it.currentTier }.average()
        
        // 총 해결 문제 수
        val totalSolved = memberAnalysisData.sumOf { it.totalSolved }
        
        // 활성 멤버 수 (최근 7일간 활동이 있는 멤버)
        val activeMembers = memberAnalysisData.count { analysis ->
            analysis.recentActivity["last7days"] ?: 0 > 0
        }
        
        // 멤버 활성도 비율
        val memberActivityRate = activeMembers.toDouble() / totalMembers
        
        // 상위 성과자 (총 해결 문제 수 기준 상위 3명)
        val topPerformers = memberAnalysisData
            .sortedByDescending { it.totalSolved }
            .take(3)
            .mapIndexed { index, analysis ->
                GroupMemberInfo(
                    userId = analysis.userId,
                    nickname = null,
                    currentTier = analysis.currentTier,
                    totalSolved = analysis.totalSolved,
                    recentActivity = analysis.recentActivity["last7days"] ?: 0,
                    contributionRank = index + 1,
                    isActive = (analysis.recentActivity["last7days"] ?: 0) > 0
                )
            }
        
        // 그룹 강점/취약 태그 분석
        val allTags = memberAnalysisData.flatMap { it.tagSkills.keys }.distinct()
        val groupTagSkills = allTags.associateWith { tag ->
            val skillValues = memberAnalysisData.mapNotNull { it.tagSkills[tag] }
            if (skillValues.isNotEmpty()) skillValues.average() else 0.0
        }
        
        val groupStrongTags = groupTagSkills
            .filter { it.value >= 0.7 }
            .toList()
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
            
        val groupWeakTags = groupTagSkills
            .filter { it.value < 0.5 }
            .toList()
            .sortedBy { it.second }
            .take(3)
            .map { it.first }
        
        // 주간 진행도 (간단히 최근 7일 활동 합계)
        val weeklyProgress = mapOf(
            "2024-08-05" to (memberAnalysisData.sumOf { it.recentActivity["last7days"] ?: 0 })
        )
        
        return StudyGroupStats(
            averageTier = averageTier,
            totalSolvedByGroup = totalSolved,
            activeMembers = activeMembers,
            totalMembers = totalMembers,
            topPerformers = topPerformers,
            groupWeakTags = groupWeakTags,
            groupStrongTags = groupStrongTags,
            weeklyProgress = weeklyProgress,
            memberActivityRate = memberActivityRate
        )
    }
    
    /**
     * 멤버 상세 정보 생성
     */
    private fun createMemberDetails(memberAnalysisData: List<PersonalAnalysis>): List<GroupMemberInfo> {
        return memberAnalysisData
            .sortedByDescending { it.totalSolved }
            .mapIndexed { index, analysis ->
                GroupMemberInfo(
                    userId = analysis.userId,
                    nickname = null,
                    currentTier = analysis.currentTier,
                    totalSolved = analysis.totalSolved,
                    recentActivity = analysis.recentActivity["last7days"] ?: 0,
                    contributionRank = index + 1,
                    isActive = (analysis.recentActivity["last7days"] ?: 0) > 0
                )
            }
    }
}