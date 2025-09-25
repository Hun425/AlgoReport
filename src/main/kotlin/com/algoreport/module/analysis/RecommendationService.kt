package com.algoreport.module.analysis

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.config.properties.AlgoreportProperties
import com.algoreport.module.user.UserRepository
import java.time.LocalDateTime
import java.util.UUID

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
    private val elasticsearchService: ElasticsearchService,
    private val algoreportProperties: AlgoreportProperties
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
        
        /** 추천 결과 캐시 TTL (분) */
        private const val RECOMMENDATION_CACHE_TTL_MINUTES = 60
        
        /** 최소 숙련도 임계값 (취약 태그 판정 기준) */
        private const val WEAK_THRESHOLD = 0.5
        
        /** 목표 응답 시간 (밀리초) */
        private const val TARGET_RESPONSE_TIME_MS = 100L
    }
    
    /**
     * 개인화된 문제 추천
     * 
     * TDD REFACTOR 단계: 캐시 최적화 및 성능 향상
     * 
     * 처리 과정:
     * 1. 사용자 존재 여부 검증
     * 2. 추천 결과 캐시 확인 (forceRefresh=false 시)
     * 3. 개인 분석 데이터 조회
     * 4. 신규 사용자 → 기본 추천 / 기존 사용자 → 취약 태그 기반 추천
     * 5. 추천 결과 캐싱
     * 
     * @param request 추천 요청 데이터
     * @return 추천 문제 응답 데이터
     * @throws CustomException 사용자를 찾을 수 없는 경우
     */
    fun getPersonalizedRecommendations(request: RecommendationRequest): RecommendationResponse {
        val startTime = System.currentTimeMillis()

        // Step 1: 사용자 존재 여부 검증
        validateUserExists(request.userId)

        // Step 2: 추천 결과 캐시 확인 (성능 최적화)
        if (!request.forceRefresh) {
            val cachedRecommendation = getCachedRecommendation(request.userId)
            if (cachedRecommendation != null) {
                return updateResponseMetadata(cachedRecommendation, startTime, cacheHit = true)
            }
        }

        // Step 3: 개인 분석 데이터 조회
        val cachedAnalysis = analysisCacheService.getPersonalAnalysisFromCache(request.userId.toString())

        // Step 4: 추천 생성
        val recommendation = if (cachedAnalysis == null || isBeginnerUser(cachedAnalysis)) {
            createBeginnerRecommendations(request.userId, startTime)
        } else {
            createWeakTagBasedRecommendations(cachedAnalysis, startTime)
        }

        // Step 5: 추천 결과 캐싱 (다음 요청 성능 향상)
        cacheRecommendation(request.userId, recommendation)

        return recommendation
    }
    
    /**
     * 신규 사용자를 위한 기본 추천 생성
     */
    private fun createBeginnerRecommendations(userId: UUID, startTime: Long): RecommendationResponse {
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
                url = "${algoreportProperties.external.baekjoonProblemBaseUrl}${problem.problemId}"
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
     * 취약 태그 기반 문제 추천 생성 (REFACTOR: 성능 및 품질 개선)
     */
    private fun createWeakTagBasedRecommendations(analysis: PersonalAnalysis, startTime: Long): RecommendationResponse {
        // Step 1: 가장 취약한 태그 선택 (개선된 알고리즘)
        val weakTags = findWeakestTags(analysis.tagSkills, WEAK_TAG_COUNT)
        
        // Step 2: 적응적 난이도 범위 계산
        val (minTier, maxTier) = calculateAdaptiveDifficulty(analysis.currentTier, analysis.totalSolved)
        
        // Step 3: 병렬 데이터 수집 (성능 최적화)
        val candidateProblems = elasticsearchService.searchProblemsByTags(weakTags, minTier, maxTier)
        val solvedProblems = elasticsearchService.getUserSolvedProblems(analysis.userId)
        
        // Step 4: 스마트 필터링 및 다양성 보장
        val finalProblems = selectDiverseProblems(candidateProblems, solvedProblems, weakTags)
        
        // Step 5: 개선된 추천 문제 객체 생성
        val recommendedProblems = createRecommendedProblemList(finalProblems, weakTags, analysis.tagSkills)
        
        val responseTime = System.currentTimeMillis() - startTime
        val isOptimalResponse = responseTime <= TARGET_RESPONSE_TIME_MS
        
        val userId = runCatching { UUID.fromString(analysis.userId) }
            .getOrElse { throw CustomException(Error.USER_NOT_FOUND) }

        return RecommendationResponse(
            userId = userId,
            recommendedProblems = recommendedProblems,
            totalRecommendations = recommendedProblems.size,
            weakTags = weakTags,
            userCurrentTier = analysis.currentTier,
            recommendationStrategy = "WEAK_TAG_BASED",
            cacheHit = false,
            responseTimeMs = responseTime,
            dataSource = "LIVE",
            lastUpdated = LocalDateTime.now(),
            message = if (weakTags.isEmpty()) "모든 태그에서 우수한 성과를 보이고 있습니다!" else null
        )
    }
    
    /**
     * 적응적 난이도 범위 계산 (사용자 실력에 맞춤)
     */
    private fun calculateAdaptiveDifficulty(currentTier: Int, totalSolved: Int): Pair<Int, Int> {
        val baseRange = DIFFICULTY_RANGE
        
        // 경험이 많은 사용자는 더 넓은 범위에서 추천
        val adaptiveRange = if (totalSolved > 500) baseRange + 1 else baseRange
        
        val minTier = (currentTier - adaptiveRange).coerceAtLeast(1)
        val maxTier = (currentTier + adaptiveRange).coerceAtMost(30)
        
        return minTier to maxTier
    }
    
    /**
     * 다양성을 보장하는 문제 선택
     */
    private fun selectDiverseProblems(
        candidateProblems: List<ProblemMetadata>,
        solvedProblems: Set<String>,
        weakTags: List<String>
    ): List<ProblemMetadata> {
        val unsolvedProblems = candidateProblems.filter { it.problemId !in solvedProblems }
        
        // 태그별로 고르게 분배
        val problemsByTag = weakTags.associateWith { tag ->
            unsolvedProblems.filter { problem -> problem.tags.contains(tag) }
        }
        
        val selectedProblems = mutableListOf<ProblemMetadata>()
        
        // 각 취약 태그에서 번갈아가며 선택
        var tagIndex = 0
        while (selectedProblems.size < DEFAULT_RECOMMENDATIONS && tagIndex < weakTags.size * 5) {
            val currentTag = weakTags[tagIndex % weakTags.size]
            val availableProblems = problemsByTag[currentTag] ?: emptyList()
            
            val unusedProblems = availableProblems.filter { problem ->
                selectedProblems.none { it.problemId == problem.problemId }
            }
            
            if (unusedProblems.isNotEmpty()) {
                selectedProblems.add(unusedProblems.first())
            }
            tagIndex++
        }
        
        // 부족한 경우 일반 문제로 채움
        val remainingCount = DEFAULT_RECOMMENDATIONS - selectedProblems.size
        if (remainingCount > 0) {
            val additionalProblems = unsolvedProblems
                .filter { problem -> selectedProblems.none { it.problemId == problem.problemId } }
                .take(remainingCount)
            selectedProblems.addAll(additionalProblems)
        }
        
        return selectedProblems.take(DEFAULT_RECOMMENDATIONS)
    }
    
    /**
     * 추천 문제 목록 생성 (향상된 추천 이유)
     */
    private fun createRecommendedProblemList(
        problems: List<ProblemMetadata>,
        weakTags: List<String>,
        tagSkills: Map<String, Double>
    ): List<RecommendedProblem> {
        return problems.mapIndexed { index, problem ->
            val primaryWeakTag = problem.tags.firstOrNull { it in weakTags } ?: weakTags.firstOrNull() ?: "general"
            val skillLevel = tagSkills[primaryWeakTag] ?: 0.0
            val skillPercentage = String.format("%.1f", skillLevel * 100)
            
            val customReason = when {
                skillLevel < 0.3 -> "$primaryWeakTag 태그 기초 실력 향상을 위한 핵심 문제입니다. (현재 숙련도: $skillPercentage%)"
                skillLevel < 0.6 -> "$primaryWeakTag 태그 중급 단계로 발전하기 위한 문제입니다. (현재 숙련도: $skillPercentage%)"
                else -> "$primaryWeakTag 태그 고급 실력 완성을 위한 도전 문제입니다. (현재 숙련도: $skillPercentage%)"
            }
            
            RecommendedProblem(
                problemId = problem.problemId,
                title = problem.title,
                difficulty = problem.difficulty,
                tags = problem.tags,
                recommendationReason = customReason,
                weakTag = primaryWeakTag,
                estimatedDifficulty = problem.tier,
                url = "${algoreportProperties.external.baekjoonProblemBaseUrl}${problem.problemId}"
            )
        }
    }
    
    /**
     * 가장 취약한 태그들을 찾아 반환 (REFACTOR: 개선된 취약점 분석)
     */
    private fun findWeakestTags(tagSkills: Map<String, Double>, count: Int): List<String> {
        return tagSkills.entries
            .filter { it.value < WEAK_THRESHOLD } // 임계값 이하만 취약 태그로 판정
            .sortedBy { it.value }
            .take(count)
            .map { it.key }
            .ifEmpty { 
                // 모두 숙련도가 높으면 상대적으로 낮은 태그 선택
                tagSkills.entries.sortedBy { it.value }.take(count).map { it.key }
            }
    }
    
    /**
     * 사용자 존재 여부 검증 (메서드 분리로 가독성 향상)
     */
    private fun validateUserExists(userId: UUID) {
        if (!userRepository.existsById(userId)) {
            throw CustomException(Error.USER_NOT_FOUND)
        }
    }
    
    /**
     * 초보자 사용자 판정
     */
    private fun isBeginnerUser(analysis: PersonalAnalysis): Boolean {
        return analysis.totalSolved < BEGINNER_THRESHOLD
    }
    
    /**
     * 캐시된 추천 결과 조회 (REFACTOR: 실제 구현 완료)
     */
    private fun getCachedRecommendation(userId: UUID): RecommendationResponse? {
        return analysisCacheService.getRecommendationFromCache(userId.toString())
    }
    
    /**
     * 추천 결과 캐싱 (REFACTOR: 실제 구현 완료)
     */
    private fun cacheRecommendation(userId: UUID, recommendation: RecommendationResponse) {
        analysisCacheService.cacheRecommendation(userId.toString(), recommendation, RECOMMENDATION_CACHE_TTL_MINUTES)
    }
    
    /**
     * 응답 메타데이터 업데이트 (캐시 히트 시)
     */
    private fun updateResponseMetadata(
        cachedRecommendation: RecommendationResponse, 
        startTime: Long, 
        cacheHit: Boolean
    ): RecommendationResponse {
        val responseTime = System.currentTimeMillis() - startTime
        return cachedRecommendation.copy(
            cacheHit = cacheHit,
            responseTimeMs = responseTime,
            dataSource = if (cacheHit) "CACHE" else "LIVE",
            lastUpdated = if (cacheHit) cachedRecommendation.lastUpdated else LocalDateTime.now()
        )
    }
}