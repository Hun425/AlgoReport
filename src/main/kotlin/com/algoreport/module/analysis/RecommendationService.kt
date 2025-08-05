package com.algoreport.module.analysis

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.module.user.UserRepository
import java.time.LocalDateTime

/**
 * 맞춤 문제 추천 서비스
 * 
 * 사용자의 취약점을 분석하여 개인화된 문제 추천을 제공하는 서비스입니다.
 * 가장 취약한 태그 2개를 기준으로 사용자 티어 ±2 범위의 문제를 5개 추천합니다.
 * 
 * @property userRepository 사용자 데이터 접근을 위한 리포지토리
 * @property analysisCacheService Redis 기반 분석 결과 캐시 서비스
 * @property elasticsearchService 문제 검색 및 메타데이터 서비스
 * 
 * @author 채기훈
 * @since 2025-08-05
 */
class RecommendationService(
    private val userRepository: UserRepository,
    private val analysisCacheService: AnalysisCacheService,
    private val elasticsearchService: ElasticsearchService
) {
    
    companion object {
        /** 기본 추천 문제 개수 */
        private const val DEFAULT_RECOMMENDATIONS = 5
        
        /** 난이도 범위 (현재 티어 ±2) */
        private const val DIFFICULTY_RANGE = 2
        
        /** 취약 태그 분석 개수 */
        private const val WEAK_TAG_COUNT = 2
        
        /** 초보자 기준 총 해결 문제 수 */
        private const val BEGINNER_THRESHOLD = 30
    }
    
    /**
     * 개인화된 문제 추천
     * 
     * TDD GREEN 단계: 테스트 통과를 위한 기본 구현
     * 
     * @param request 추천 요청 데이터
     * @return 추천 문제 응답 데이터
     * @throws CustomException 사용자를 찾을 수 없는 경우
     */
    fun getPersonalizedRecommendations(request: RecommendationRequest): RecommendationResponse {
        val startTime = System.currentTimeMillis()
        
        // Step 1: 사용자 존재 여부 검증
        val activeUserIds = userRepository.findAllActiveUserIds()
        if (!activeUserIds.contains(request.userId)) {
            throw CustomException(Error.USER_NOT_FOUND)
        }
        
        // Step 2: 캐시된 개인 분석 데이터 조회
        val cachedAnalysis = analysisCacheService.getPersonalAnalysisFromCache(request.userId)
        
        // Step 3: 신규 사용자 처리
        if (cachedAnalysis == null) {
            return createBeginnerRecommendations(request.userId, startTime)
        }
        
        // Step 4: 취약 태그 기반 문제 추천
        return createWeakTagBasedRecommendations(cachedAnalysis, startTime)
    }
    
    /**
     * 신규 사용자를 위한 기본 추천 생성
     */
    private fun createBeginnerRecommendations(userId: String, startTime: Long): RecommendationResponse {
        val beginnerProblems = elasticsearchService.getBeginnerRecommendations(DEFAULT_RECOMMENDATIONS)
        
        val recommendedProblems = beginnerProblems.map { problem ->
            RecommendedProblem(
                problemId = problem.problemId,
                title = problem.title,
                difficulty = problem.difficulty,
                tags = problem.tags,
                recommendationReason = "초보자에게 적합한 기본 문제입니다.",
                weakTag = "implementation",
                estimatedDifficulty = problem.tier,
                url = "https://www.acmicpc.net/problem/${problem.problemId}"
            )
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        
        return RecommendationResponse(
            userId = userId,
            recommendedProblems = recommendedProblems,
            totalRecommendations = recommendedProblems.size,
            weakTags = emptyList(),
            userCurrentTier = 0,
            recommendationStrategy = "BEGINNER_FRIENDLY",
            cacheHit = false,
            responseTimeMs = responseTime,
            dataSource = "LIVE",
            lastUpdated = LocalDateTime.now(),
            message = "초보자를 위한 기본 문제들을 추천드려요!"
        )
    }
    
    /**
     * 취약 태그 기반 문제 추천 생성
     */
    private fun createWeakTagBasedRecommendations(analysis: PersonalAnalysis, startTime: Long): RecommendationResponse {
        // Step 1: 가장 취약한 태그 2개 선택
        val weakTags = findWeakestTags(analysis.tagSkills, WEAK_TAG_COUNT)
        
        // Step 2: 난이도 범위 계산 (현재 티어 ±2)
        val minTier = (analysis.currentTier - DIFFICULTY_RANGE).coerceAtLeast(1)
        val maxTier = analysis.currentTier + DIFFICULTY_RANGE
        
        // Step 3: 문제 검색
        val candidateProblems = elasticsearchService.searchProblemsByTags(weakTags, minTier, maxTier)
        val solvedProblems = elasticsearchService.getUserSolvedProblems(analysis.userId)
        
        // Step 4: 이미 푼 문제 제외 및 추천 개수 제한
        val unsolvedProblems = candidateProblems.filter { it.problemId !in solvedProblems }
        val finalProblems = unsolvedProblems.take(DEFAULT_RECOMMENDATIONS)
        
        // Step 5: 추천 문제 객체 생성
        val recommendedProblems = finalProblems.mapIndexed { index, problem ->
            val weakTag = if (index % 2 == 0) weakTags.getOrElse(0) { "unknown" } else weakTags.getOrElse(1) { "unknown" }
            val skillLevel = analysis.tagSkills[weakTag] ?: 0.0
            
            RecommendedProblem(
                problemId = problem.problemId,
                title = problem.title,
                difficulty = problem.difficulty,
                tags = problem.tags,
                recommendationReason = "${weakTag} 태그 숙련도 향상을 위한 문제입니다. (현재 숙련도: ${String.format("%.1f", skillLevel * 100)}%)",
                weakTag = weakTag,
                estimatedDifficulty = problem.tier,
                url = "https://www.acmicpc.net/problem/${problem.problemId}"
            )
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        
        return RecommendationResponse(
            userId = analysis.userId,
            recommendedProblems = recommendedProblems,
            totalRecommendations = recommendedProblems.size,
            weakTags = weakTags,
            userCurrentTier = analysis.currentTier,
            recommendationStrategy = "WEAK_TAG_BASED",
            cacheHit = true,
            responseTimeMs = responseTime,
            dataSource = "CACHE",
            lastUpdated = LocalDateTime.now()
        )
    }
    
    /**
     * 가장 취약한 태그들을 찾아 반환
     */
    private fun findWeakestTags(tagSkills: Map<String, Double>, count: Int): List<String> {
        return tagSkills.entries
            .sortedBy { it.value }
            .take(count)
            .map { it.key }
    }
}