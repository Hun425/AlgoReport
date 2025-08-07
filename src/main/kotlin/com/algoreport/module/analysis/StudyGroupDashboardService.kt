package com.algoreport.module.analysis

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.module.studygroup.StudyGroupRepository
import com.algoreport.module.user.UserRepository
import java.time.LocalDateTime

/**
 * 스터디 그룹 대시보드 서비스
 * 
 * 그룹별 학습 현황 분석 및 대시보드 데이터 제공 서비스입니다.
 * 그룹 통계, 멤버 분석, 강점/취약점 인사이트를 제공합니다.
 * 
 * 주요 기능:
 * - 그룹 전체 통계 및 성과 지표 계산
 * - 멤버별 상세 분석 및 기여도 순위
 * - 그룹 강점/취약 태그 분석
 * - Redis 캐시를 활용한 성능 최적화
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
    
    companion object {
        /** 그룹 강점 태그 기준 임계값 (70% 이상) */
        private const val STRONG_TAG_THRESHOLD = 0.7
        
        /** 그룹 취약 태그 기준 임계값 (50% 미만) */
        private const val WEAK_TAG_THRESHOLD = 0.5
        
        /** 상위 성과자 표시 개수 */
        private const val TOP_PERFORMERS_COUNT = 3
        
        /** 최대 강점/취약 태그 표시 개수 */
        private const val MAX_TAG_COUNT = 3
        
        /** 활성 사용자 기준 최근 일수 */
        private const val ACTIVE_USER_RECENT_DAYS = 7
        
        /** 기본 그룹 이름 (조회 실패 시) */
        private const val DEFAULT_GROUP_NAME = "Unknown Group"
    }
    
    /**
     * 스터디 그룹 대시보드 데이터 조회
     * 
     * 그룹별 학습 현황, 멤버 통계, 강점/약점 분석 정보를 제공합니다.
     * 캐시 우선 전략을 통해 성능을 최적화하고, 다양한 그룹 상태에 대응합니다.
     * 
     * 처리 흐름:
     * 1. 그룹 존재 여부 검증
     * 2. 캐시 확인 및 활용 (forceRefresh=false인 경우)
     * 3. 그룹 기본 정보 및 멤버 목록 조회
     * 4. 멤버별 분석 데이터 수집
     * 5. 그룹 통계 계산 및 대시보드 생성
     * 6. 결과 캐싱
     * 
     * @param request 대시보드 요청 데이터 (그룹 ID, 강제 갱신 여부)
     * @return 스터디 그룹 대시보드 응답 데이터
     * @throws CustomException 그룹을 찾을 수 없는 경우
     */
    fun getStudyGroupDashboard(request: StudyGroupDashboardRequest): StudyGroupDashboardResponse {
        val startTime = System.currentTimeMillis()
        
        // 1. 그룹 존재 여부 검증
        validateGroupExists(request.groupId)
        
        // 2. 캐시 확인 (강제 갱신이 아닌 경우)
        if (!request.forceRefresh) {
            val cachedDashboard = checkCachedDashboard(request.groupId, startTime)
            if (cachedDashboard != null) {
                return cachedDashboard
            }
        }
        
        // 3. 그룹 기본 정보 조회
        val groupName = getGroupName(request.groupId)
        
        // 4. 그룹 멤버 ID 목록 조회
        val memberIds = studyGroupRepository.findGroupMemberIds(request.groupId)
        
        // 5. 특수 케이스 처리 (멤버 없음 또는 분석 데이터 없음)
        val memberAnalysisData = collectMemberAnalysisData(memberIds)
        if (memberIds.isEmpty()) {
            return createEmptyGroupDashboard(request.groupId, groupName, startTime)
        }
        if (memberAnalysisData.isEmpty()) {
            return createNewGroupDashboard(request.groupId, groupName, memberIds.size, startTime)
        }
        
        // 6. 그룹 통계 계산 및 대시보드 생성
        return buildCompleteDashboard(request.groupId, groupName, memberIds, memberAnalysisData, startTime)
    }
    
    /**
     * 그룹 존재 여부 검증
     * 
     * @param groupId 검증할 그룹 ID
     * @throws CustomException 그룹이 존재하지 않는 경우
     */
    private fun validateGroupExists(groupId: String) {
        if (!studyGroupRepository.existsById(groupId)) {
            throw CustomException(Error.STUDY_GROUP_NOT_FOUND)
        }
    }
    
    /**
     * 캐시된 대시보드 확인
     * 
     * @param groupId 그룹 ID
     * @param startTime 요청 시작 시간
     * @return 캐시된 대시보드 데이터 또는 null
     */
    private fun checkCachedDashboard(groupId: String, startTime: Long): StudyGroupDashboardResponse? {
        val cachedDashboard = analysisCacheService.getGroupDashboardFromCache(groupId)
        return cachedDashboard?.copy(
            cacheHit = true,
            dataSource = "CACHE",
            responseTimeMs = System.currentTimeMillis() - startTime
        )
    }
    
    /**
     * 그룹 이름 조회
     * 
     * @param groupId 그룹 ID
     * @return 그룹 이름
     */
    private fun getGroupName(groupId: String): String {
        val groupInfo = elasticsearchService.getStudyGroupInfo(groupId)
        return groupInfo["name"] as? String ?: DEFAULT_GROUP_NAME
    }
    
    /**
     * 멤버별 분석 데이터 수집
     * 
     * @param memberIds 멤버 ID 목록
     * @return 멤버별 개인 분석 데이터 목록
     */
    private fun collectMemberAnalysisData(memberIds: List<String>): List<PersonalAnalysis> {
        return memberIds.mapNotNull { memberId ->
            analysisCacheService.getPersonalAnalysisFromCache(memberId)
        }
    }
    
    /**
     * 빈 그룹 대시보드 생성 (멤버가 없는 경우)
     * 
     * @param groupId 그룹 ID
     * @param groupName 그룹 이름
     * @param startTime 요청 시작 시간
     * @return 빈 그룹 대시보드 응답
     */
    private fun createEmptyGroupDashboard(
        groupId: String, 
        groupName: String, 
        startTime: Long
    ): StudyGroupDashboardResponse {
        val emptyDashboard = StudyGroupDashboardResponse(
            groupId = groupId,
            groupName = groupName,
            memberCount = 0,
            groupStats = createEmptyGroupStats(0),
            memberDetails = emptyList(),
            cacheHit = false,
            responseTimeMs = System.currentTimeMillis() - startTime,
            dataSource = "LIVE",
            lastUpdated = LocalDateTime.now(),
            message = "아직 멤버가 없는 그룹입니다. 멤버를 초대해보세요!"
        )
        analysisCacheService.cacheGroupDashboard(groupId, emptyDashboard)
        return emptyDashboard
    }
    
    /**
     * 신규 그룹 대시보드 생성 (분석 데이터가 없는 경우)
     * 
     * @param groupId 그룹 ID
     * @param groupName 그룹 이름
     * @param memberCount 멤버 수
     * @param startTime 요청 시작 시간
     * @return 신규 그룹 대시보드 응답
     */
    private fun createNewGroupDashboard(
        groupId: String, 
        groupName: String, 
        memberCount: Int, 
        startTime: Long
    ): StudyGroupDashboardResponse {
        val newGroupDashboard = StudyGroupDashboardResponse(
            groupId = groupId,
            groupName = groupName,
            memberCount = memberCount,
            groupStats = createEmptyGroupStats(memberCount),
            memberDetails = emptyList(),
            cacheHit = false,
            responseTimeMs = System.currentTimeMillis() - startTime,
            dataSource = "LIVE",
            lastUpdated = LocalDateTime.now(),
            message = "그룹 멤버들이 solved.ac 계정을 연동하면 더 자세한 통계를 확인할 수 있습니다."
        )
        analysisCacheService.cacheGroupDashboard(groupId, newGroupDashboard)
        return newGroupDashboard
    }
    
    /**
     * 완전한 대시보드 생성
     * 
     * @param groupId 그룹 ID
     * @param groupName 그룹 이름
     * @param memberIds 멤버 ID 목록
     * @param memberAnalysisData 멤버 분석 데이터
     * @param startTime 요청 시작 시간
     * @return 완전한 대시보드 응답
     */
    private fun buildCompleteDashboard(
        groupId: String,
        groupName: String,
        memberIds: List<String>,
        memberAnalysisData: List<PersonalAnalysis>,
        startTime: Long
    ): StudyGroupDashboardResponse {
        val groupStats = calculateGroupStats(memberAnalysisData, memberIds.size)
        val memberDetails = createMemberDetails(memberAnalysisData)
        
        val dashboard = StudyGroupDashboardResponse(
            groupId = groupId,
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
        
        analysisCacheService.cacheGroupDashboard(groupId, dashboard)
        return dashboard
    }
    
    /**
     * 빈 그룹 통계 생성
     * 
     * @param totalMembers 총 멤버 수
     * @return 빈 그룹 통계 객체
     */
    private fun createEmptyGroupStats(totalMembers: Int): StudyGroupStats {
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
    
    /**
     * 그룹 통계 계산
     * 
     * 멤버들의 개인 분석 데이터를 바탕으로 그룹 전체의 통계를 계산합니다.
     * 평균 티어, 총 해결 문제 수, 활성 멤버 비율, 상위 성과자, 
     * 그룹 강점/취약 태그 등을 포함합니다.
     * 
     * @param memberAnalysisData 멤버별 개인 분석 데이터 목록
     * @param totalMembers 총 멤버 수
     * @return 계산된 그룹 통계 객체
     */
    private fun calculateGroupStats(memberAnalysisData: List<PersonalAnalysis>, totalMembers: Int): StudyGroupStats {
        if (memberAnalysisData.isEmpty()) {
            return createEmptyGroupStats(totalMembers)
        }
        
        // 기본 통계 계산
        val averageTier = calculateAverageTier(memberAnalysisData)
        val totalSolved = calculateTotalSolved(memberAnalysisData)
        val activeMembers = calculateActiveMembers(memberAnalysisData)
        val memberActivityRate = activeMembers.toDouble() / totalMembers
        
        // 상위 성과자 및 태그 분석
        val topPerformers = findTopPerformers(memberAnalysisData)
        val (groupStrongTags, groupWeakTags) = analyzeGroupTags(memberAnalysisData)
        val weeklyProgress = calculateWeeklyProgress(memberAnalysisData)
        
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
     * 평균 티어 계산
     */
    private fun calculateAverageTier(memberAnalysisData: List<PersonalAnalysis>): Double {
        return memberAnalysisData.map { it.currentTier }.average()
    }
    
    /**
     * 총 해결 문제 수 계산
     */
    private fun calculateTotalSolved(memberAnalysisData: List<PersonalAnalysis>): Int {
        return memberAnalysisData.sumOf { it.totalSolved }
    }
    
    /**
     * 활성 멤버 수 계산 (최근 지정된 일수간 활동이 있는 멤버)
     */
    private fun calculateActiveMembers(memberAnalysisData: List<PersonalAnalysis>): Int {
        return memberAnalysisData.count { analysis ->
            analysis.recentActivity["last${ACTIVE_USER_RECENT_DAYS}days"] ?: 0 > 0
        }
    }
    
    /**
     * 상위 성과자 찾기 (총 해결 문제 수 기준 상위 N명)
     */
    private fun findTopPerformers(memberAnalysisData: List<PersonalAnalysis>): List<GroupMemberInfo> {
        return memberAnalysisData
            .sortedByDescending { it.totalSolved }
            .take(TOP_PERFORMERS_COUNT)
            .mapIndexed { index, analysis ->
                GroupMemberInfo(
                    userId = analysis.userId,
                    nickname = null,
                    currentTier = analysis.currentTier,
                    totalSolved = analysis.totalSolved,
                    recentActivity = analysis.recentActivity["last${ACTIVE_USER_RECENT_DAYS}days"] ?: 0,
                    contributionRank = index + 1,
                    isActive = (analysis.recentActivity["last${ACTIVE_USER_RECENT_DAYS}days"] ?: 0) > 0
                )
            }
    }
    
    /**
     * 그룹 강점/취약 태그 분석
     * 
     * @param memberAnalysisData 멤버 분석 데이터
     * @return Pair(강점 태그 목록, 취약 태그 목록)
     */
    private fun analyzeGroupTags(memberAnalysisData: List<PersonalAnalysis>): Pair<List<String>, List<String>> {
        val allTags = memberAnalysisData.flatMap { it.tagSkills.keys }.distinct()
        val groupTagSkills = calculateGroupTagSkills(memberAnalysisData, allTags)
        
        val strongTags = groupTagSkills
            .filter { it.value >= STRONG_TAG_THRESHOLD }
            .toList()
            .sortedByDescending { it.second }
            .take(MAX_TAG_COUNT)
            .map { it.first }
            
        val weakTags = groupTagSkills
            .filter { it.value < WEAK_TAG_THRESHOLD }
            .toList()
            .sortedBy { it.second }
            .take(MAX_TAG_COUNT)
            .map { it.first }
            
        return Pair(strongTags, weakTags)
    }
    
    /**
     * 그룹 태그별 평균 숙련도 계산
     */
    private fun calculateGroupTagSkills(memberAnalysisData: List<PersonalAnalysis>, allTags: List<String>): Map<String, Double> {
        return allTags.associateWith { tag ->
            val skillValues = memberAnalysisData.mapNotNull { it.tagSkills[tag] }
            if (skillValues.isNotEmpty()) skillValues.average() else 0.0
        }
    }
    
    /**
     * 주간 진행도 계산
     */
    private fun calculateWeeklyProgress(memberAnalysisData: List<PersonalAnalysis>): Map<String, Int> {
        return mapOf(
            "2024-08-05" to (memberAnalysisData.sumOf { it.recentActivity["last${ACTIVE_USER_RECENT_DAYS}days"] ?: 0 })
        )
    }
    
    /**
     * 멤버 상세 정보 생성
     * 
     * 그룹 내 모든 멤버의 상세 정보를 기여도 순으로 정렬하여 생성합니다.
     * 총 해결 문제 수를 기준으로 순위를 매기고, 최근 활동 여부를 판단합니다.
     * 
     * @param memberAnalysisData 멤버별 개인 분석 데이터 목록
     * @return 기여도 순으로 정렬된 멤버 상세 정보 목록
     */
    private fun createMemberDetails(memberAnalysisData: List<PersonalAnalysis>): List<GroupMemberInfo> {
        return memberAnalysisData
            .sortedByDescending { it.totalSolved }
            .mapIndexed { index, analysis ->
                createGroupMemberInfo(analysis, index + 1)
            }
    }
    
    /**
     * 개별 멤버 정보 생성
     * 
     * @param analysis 개인 분석 데이터
     * @param rank 기여도 순위
     * @return 그룹 멤버 정보 객체
     */
    private fun createGroupMemberInfo(analysis: PersonalAnalysis, rank: Int): GroupMemberInfo {
        val recentActivityCount = analysis.recentActivity["last${ACTIVE_USER_RECENT_DAYS}days"] ?: 0
        
        return GroupMemberInfo(
            userId = analysis.userId,
            nickname = null, // TODO: 추후 사용자 서비스에서 닉네임 조회 구현
            currentTier = analysis.currentTier,
            totalSolved = analysis.totalSolved,
            recentActivity = recentActivityCount,
            contributionRank = rank,
            isActive = recentActivityCount > 0
        )
    }
}