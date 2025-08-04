package com.algoreport.module.analysis

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.module.user.UserRepository
import java.time.LocalDateTime

/**
 * 개인 대시보드 응답 데이터 모델
 */
data class PersonalDashboardResponse(
    val userId: String,
    val totalSolved: Int,
    val currentTier: Int,
    val heatmapData: Map<String, Int>, // "2024-01-01" -> 문제해결수
    val tagSkillsRadar: Map<String, Double>, // 태그 -> 숙련도 (0.0-1.0)
    val difficultyDistribution: Map<String, Int>, // 난이도 -> 문제수
    val recentActivity: Map<String, Int>, // "last7days", "last30days" -> 문제수
    val cacheHit: Boolean = false,
    val responseTimeMs: Long = 0,
    val dataSource: String = "LIVE",
    val lastUpdated: LocalDateTime = LocalDateTime.now(),
    val isNewUser: Boolean = false,
    val message: String? = null
)

/**
 * 개인 대시보드 서비스
 * 
 * 사용자별 학습 현황 분석 및 대시보드 데이터 제공 서비스입니다.
 * 캐시 우선 전략으로 빠른 응답 속도를 보장하며, Elasticsearch 집계를 통한
 * 실시간 데이터 분석을 지원합니다.
 * 
 * @property userRepository 사용자 데이터 접근을 위한 리포지토리
 * @property analysisCacheService Redis 기반 분석 결과 캐시 서비스
 * @property elasticsearchService 검색 및 집계 서비스
 * 
 * @author 채기훈
 * @since 2025-08-04
 */
class PersonalDashboardService(
    private val userRepository: UserRepository,
    private val analysisCacheService: AnalysisCacheService,
    private val elasticsearchService: ElasticsearchService
) {
    
    companion object {
        /** 잔디밭 히트맵 표시 기간 (일) */
        private const val HEATMAP_DAYS = 365
        
        /** 최근 활동 요약 기간 - 1주일 (일) */
        private const val RECENT_ACTIVITY_WEEK_DAYS = 7
        
        /** 최근 활동 요약 기간 - 1개월 (일) */
        private const val RECENT_ACTIVITY_MONTH_DAYS = 30
        
        /** 골드 티어 기준 - 최소 해결 문제 수 */
        private const val GOLD_TIER_MIN_SOLVED = 100
        
        /** 실버 티어 기준 - 최소 해결 문제 수 */
        private const val SILVER_TIER_MIN_SOLVED = 50
        
        /** 브론즈 티어 기준 - 최소 해결 문제 수 */
        private const val BRONZE_TIER_MIN_SOLVED = 20
        
        /** 고숙련도 기준 임계값 */
        private const val HIGH_SKILL_THRESHOLD = 0.7
        
        /** 목표 응답 시간 (밀리초) */
        private const val TARGET_RESPONSE_TIME_MS = 50L
    }
    
    /**
     * solved.ac 티어 레벨 정의
     */
    private enum class TierLevel(val value: Int) {
        UNRATED(0),
        BRONZE(6),
        SILVER(8),
        GOLD(12)
    }
    
    /**
     * 개인 대시보드 데이터 조회
     * 
     * 사용자의 개인 학습 현황을 종합적으로 분석하여 대시보드 데이터를 제공합니다.
     * 캐시 우선 전략을 사용하여 빠른 응답 속도를 보장하며, 필요시 Elasticsearch에서
     * 실시간 데이터를 수집합니다.
     * 
     * 처리 과정:
     * 1. 사용자 존재 여부 검증
     * 2. 캐시 확인 (forceRefresh=false 시)
     * 3. Elasticsearch 실시간 데이터 수집
     * 4. 신규 사용자 감지 및 기본값 처리
     * 5. 종합 대시보드 데이터 생성
     * 
     * @param userId 조회할 사용자 ID (비어있으면 안됨)
     * @param forceRefresh 강제 갱신 플래그 (true: 캐시 무시, false: 캐시 우선)
     * @return 개인 대시보드 응답 데이터 (잔디밭, 태그 숙련도, 티어 정보 등 포함)
     * @throws CustomException 사용자를 찾을 수 없는 경우 (Error.USER_NOT_FOUND)
     */
    fun getPersonalDashboard(userId: String, forceRefresh: Boolean = false): PersonalDashboardResponse {
        val startTime = System.currentTimeMillis()
        
        // Step 1: 사용자 존재 여부 검증
        val activeUserIds = userRepository.findAllActiveUserIds()
        if (!activeUserIds.contains(userId)) {
            throw CustomException(Error.USER_NOT_FOUND)
        }
        
        // Step 2: 캐시 확인 (forceRefresh가 false일 때만)
        if (!forceRefresh) {
            val cachedAnalysis = analysisCacheService.getPersonalAnalysisFromCache(userId)
            if (cachedAnalysis != null) {
                return createResponseFromCache(cachedAnalysis, startTime)
            }
        }
        
        // Step 3: Elasticsearch에서 최신 데이터 수집
        val tagSkills = elasticsearchService.aggregateTagSkills(userId)
        val solvedByDifficulty = elasticsearchService.aggregateSolvedByDifficulty(userId)
        val recentActivity = elasticsearchService.aggregateRecentActivity(userId)
        
        // Step 4: 신규 사용자 감지
        val isNewUser = tagSkills.isEmpty() && solvedByDifficulty.isEmpty() && recentActivity.isEmpty()
        if (isNewUser) {
            return createNewUserResponse(userId, startTime)
        }
        
        // Step 5: 대시보드 데이터 생성
        val totalSolved = solvedByDifficulty.values.sum()
        val currentTier = estimateCurrentTier(totalSolved, tagSkills)
        val heatmapData = generateHeatmapData(recentActivity)
        val difficultyDistribution = transformDifficultyData(solvedByDifficulty)
        val recentActivitySummary = summarizeRecentActivity(recentActivity)
        
        val responseTime = System.currentTimeMillis() - startTime
        
        return PersonalDashboardResponse(
            userId = userId,
            totalSolved = totalSolved,
            currentTier = currentTier,
            heatmapData = heatmapData,
            tagSkillsRadar = tagSkills,
            difficultyDistribution = difficultyDistribution,
            recentActivity = recentActivitySummary,
            cacheHit = false,
            responseTimeMs = responseTime,
            dataSource = "LIVE",
            lastUpdated = LocalDateTime.now(),
            isNewUser = false
        )
    }
    
    /**
     * 캐시된 분석 데이터로부터 대시보드 응답을 생성합니다.
     * 
     * @param cachedAnalysis 캐시된 개인 분석 데이터
     * @param startTime 처리 시작 시각 (응답 시간 계산용)
     * @return 캐시 기반 개인 대시보드 응답
     */
    private fun createResponseFromCache(cachedAnalysis: PersonalAnalysis, startTime: Long): PersonalDashboardResponse {
        val responseTime = System.currentTimeMillis() - startTime
        val heatmapData = generateHeatmapData(cachedAnalysis.recentActivity)
        val difficultyDistribution = transformDifficultyData(cachedAnalysis.solvedByDifficulty)
        
        return PersonalDashboardResponse(
            userId = cachedAnalysis.userId,
            totalSolved = cachedAnalysis.totalSolved,
            currentTier = cachedAnalysis.currentTier,
            heatmapData = heatmapData,
            tagSkillsRadar = cachedAnalysis.tagSkills,
            difficultyDistribution = difficultyDistribution,
            recentActivity = cachedAnalysis.recentActivity,
            cacheHit = true,
            responseTimeMs = responseTime,
            dataSource = "CACHE",
            lastUpdated = cachedAnalysis.analysisDate
        )
    }
    
    /**
     * 신규 사용자를 위한 기본 대시보드 응답을 생성합니다.
     * 
     * @param userId 신규 사용자 ID
     * @param startTime 처리 시작 시각 (응답 시간 계산용)
     * @return 신규 사용자용 기본 대시보드 응답 (안내 메시지 포함)
     */
    private fun createNewUserResponse(userId: String, startTime: Long): PersonalDashboardResponse {
        val responseTime = System.currentTimeMillis() - startTime
        
        return PersonalDashboardResponse(
            userId = userId,
            totalSolved = 0,
            currentTier = 0,
            heatmapData = emptyMap(),
            tagSkillsRadar = emptyMap(),
            difficultyDistribution = emptyMap(),
            recentActivity = emptyMap(),
            cacheHit = false,
            responseTimeMs = responseTime,
            dataSource = "NEW_USER",
            lastUpdated = LocalDateTime.now(),
            isNewUser = true,
            message = "solved.ac 계정을 연동하여 개인 통계를 확인해보세요!"
        )
    }
    
    /**
     * 해결한 문제 수와 태그별 숙련도를 기반으로 현재 티어를 추정합니다.
     * 
     * @param totalSolved 총 해결한 문제 수
     * @param tagSkills 태그별 숙련도 맵 (0.0-1.0)
     * @return 추정된 티어 (0: Unrated, 6: Bronze, 8: Silver, 12: Gold)
     */
    private fun estimateCurrentTier(totalSolved: Int, tagSkills: Map<String, Double>): Int {
        return when {
            totalSolved >= GOLD_TIER_MIN_SOLVED && TagSkills.hasHighSkillLevel(tagSkills) -> TierLevel.GOLD.value
            totalSolved >= SILVER_TIER_MIN_SOLVED -> TierLevel.SILVER.value
            totalSolved >= BRONZE_TIER_MIN_SOLVED -> TierLevel.BRONZE.value
            else -> TierLevel.UNRATED.value
        }
    }
    
    /**
     * 최근 365일간의 잔디밭 히트맵 데이터를 생성합니다.
     * 
     * @param recentActivity 날짜별 문제 해결 수 맵
     * @return 365일간의 히트맵 데이터 ("YYYY-MM-DD" -> 문제해결수)
     */
    private fun generateHeatmapData(recentActivity: Map<String, Int>): Map<String, Int> {
        val heatmap = mutableMapOf<String, Int>()
        val today = LocalDateTime.now().toLocalDate()
        
        for (i in 0 until HEATMAP_DAYS) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.toString()
            heatmap[dateStr] = recentActivity[dateStr] ?: 0
        }
        
        return heatmap
    }
    
    /**
     * Elasticsearch 결과를 프론트엔드에서 사용할 수 있는 형식으로 변환합니다.
     * 
     * @param solvedByDifficulty 난이도별 해결 문제 수 맵
     * @return 변환된 난이도 분포 데이터
     */
    private fun transformDifficultyData(solvedByDifficulty: Map<String, Int>): Map<String, Int> {
        return solvedByDifficulty.toMap()
    }
    
    /**
     * 최근 활동 데이터를 요약하여 주간/월간 통계를 생성합니다.
     * 
     * @param recentActivity 날짜별 문제 해결 수 맵
     * @return 요약된 최근 활동 통계 ("last7days", "last30days" 키 포함)
     */
    private fun summarizeRecentActivity(recentActivity: Map<String, Int>): Map<String, Int> {
        val now = LocalDateTime.now()
        
        val last7days = (0 until RECENT_ACTIVITY_WEEK_DAYS).sumOf { days ->
            recentActivity[now.minusDays(days.toLong()).toLocalDate().toString()] ?: 0
        }
        
        val last30days = (0 until RECENT_ACTIVITY_MONTH_DAYS).sumOf { days ->
            recentActivity[now.minusDays(days.toLong()).toLocalDate().toString()] ?: 0
        }
        
        return mapOf(
            "last7days" to last7days,
            "last30days" to last30days
        )
    }
    
    /**
     * 태그별 숙련도 관련 유틸리티 객체
     */
    private object TagSkills {
        /**
         * 태그 숙련도 맵에서 고숙련도 태그가 있는지 확인합니다.
         * 
         * @param tagSkills 태그별 숙련도 맵 (0.0-1.0)
         * @return 0.7 이상의 숙련도를 가진 태그가 하나라도 있으면 true
         */
        fun hasHighSkillLevel(tagSkills: Map<String, Double>): Boolean {
            return tagSkills.values.any { it >= HIGH_SKILL_THRESHOLD }
        }
    }
}