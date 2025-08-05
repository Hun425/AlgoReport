package com.algoreport.module.analysis

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 분석 결과 캐시 서비스
 * TDD Refactor 단계: Redis 캐시 구현으로 대시보드 성능 최적화
 * 
 * 캐시 키 구조:
 * - analysis:personal:{userId} - 개인 분석 결과 (TTL: 6시간)
 * - analysis:group:{groupId} - 그룹 분석 결과 (TTL: 12시간)
 * - analysis:meta:last_update - 마지막 분석 업데이트 시간 (TTL: 24시간)
 */
@Service
class AnalysisCacheService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    
    private val logger = LoggerFactory.getLogger(AnalysisCacheService::class.java)
    
    companion object {
        // 캐시 TTL 설정 (성능 vs 데이터 신선도 균형)
        private const val PERSONAL_ANALYSIS_TTL_HOURS = 6L  // 개인 분석: 6시간 (하루 4번 갱신)
        private const val GROUP_ANALYSIS_TTL_HOURS = 12L   // 그룹 분석: 12시간 (하루 2번 갱신)
        private const val RECOMMENDATION_TTL_HOURS = 1L    // 추천 결과: 1시간 (자주 갱신)
        private const val META_DATA_TTL_HOURS = 24L        // 메타 데이터: 24시간
        
        // 캐시 키 패턴
        private const val PERSONAL_ANALYSIS_KEY_PREFIX = "analysis:personal:"
        private const val GROUP_ANALYSIS_KEY_PREFIX = "analysis:group:"
        private const val RECOMMENDATION_KEY_PREFIX = "recommendation:personal:"
        private const val META_LAST_UPDATE_KEY = "analysis:meta:last_update"
    }
    
    /**
     * 개인 분석 결과 캐시 저장
     */
    fun cachePersonalAnalysis(userId: String, analysis: PersonalAnalysis) {
        try {
            val key = "$PERSONAL_ANALYSIS_KEY_PREFIX$userId"
            val value = objectMapper.writeValueAsString(analysis)
            
            redisTemplate.opsForValue().set(key, value, PERSONAL_ANALYSIS_TTL_HOURS, TimeUnit.HOURS)
            logger.debug("Cached personal analysis for user: {}", userId)
        } catch (e: Exception) {
            logger.error("Failed to cache personal analysis for user {}: {}", userId, e.message, e)
        }
    }
    
    /**
     * 개인 분석 결과 캐시 조회
     */
    fun getPersonalAnalysisFromCache(userId: String): PersonalAnalysis? {
        return try {
            val key = "$PERSONAL_ANALYSIS_KEY_PREFIX$userId"
            val value = redisTemplate.opsForValue().get(key)
            
            if (value != null) {
                val analysis = objectMapper.readValue(value, PersonalAnalysis::class.java)
                logger.debug("Retrieved personal analysis from cache for user: {}", userId)
                analysis
            } else {
                logger.debug("No cached personal analysis found for user: {}", userId)
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to retrieve personal analysis from cache for user {}: {}", userId, e.message, e)
            null
        }
    }
    
    /**
     * 그룹 분석 결과 캐시 저장
     */
    fun cacheGroupAnalysis(groupId: String, analysis: GroupAnalysis) {
        try {
            val key = "$GROUP_ANALYSIS_KEY_PREFIX$groupId"
            val value = objectMapper.writeValueAsString(analysis)
            
            redisTemplate.opsForValue().set(key, value, GROUP_ANALYSIS_TTL_HOURS, TimeUnit.HOURS)
            logger.debug("Cached group analysis for group: {}", groupId)
        } catch (e: Exception) {
            logger.error("Failed to cache group analysis for group {}: {}", groupId, e.message, e)
        }
    }
    
    /**
     * 그룹 분석 결과 캐시 조회
     */
    fun getGroupAnalysisFromCache(groupId: String): GroupAnalysis? {
        return try {
            val key = "$GROUP_ANALYSIS_KEY_PREFIX$groupId"
            val value = redisTemplate.opsForValue().get(key)
            
            if (value != null) {
                val analysis = objectMapper.readValue(value, GroupAnalysis::class.java)
                logger.debug("Retrieved group analysis from cache for group: {}", groupId)
                analysis
            } else {
                logger.debug("No cached group analysis found for group: {}", groupId)
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to retrieve group analysis from cache for group {}: {}", groupId, e.message, e)
            null
        }
    }
    
    /**
     * 마지막 분석 업데이트 시간 캐시 저장
     */
    fun cacheLastUpdateTime(updateTime: LocalDateTime) {
        try {
            val value = objectMapper.writeValueAsString(mapOf("lastUpdate" to updateTime.toString()))
            redisTemplate.opsForValue().set(META_LAST_UPDATE_KEY, value, META_DATA_TTL_HOURS, TimeUnit.HOURS)
            logger.debug("Cached last update time: {}", updateTime)
        } catch (e: Exception) {
            logger.error("Failed to cache last update time: {}", e.message, e)
        }
    }
    
    /**
     * 마지막 분석 업데이트 시간 캐시에서 조회
     */
    fun getLastUpdateTimeFromCache(): LocalDateTime? {
        return try {
            val value = redisTemplate.opsForValue().get(META_LAST_UPDATE_KEY)
            if (value != null) {
                val data = objectMapper.readValue(value, Map::class.java)
                val timeString = data["lastUpdate"] as String
                LocalDateTime.parse(timeString)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to retrieve last update time from cache: {}", e.message, e)
            null
        }
    }
    
    /**
     * 개인 분석 결과 캐시 삭제 (보상 트랜잭션용)
     */
    fun evictPersonalAnalysis(userId: String) {
        try {
            val key = "$PERSONAL_ANALYSIS_KEY_PREFIX$userId"
            redisTemplate.delete(key)
            logger.debug("Evicted personal analysis cache for user: {}", userId)
        } catch (e: Exception) {
            logger.error("Failed to evict personal analysis cache for user {}: {}", userId, e.message, e)
        }
    }
    
    /**
     * 그룹 분석 결과 캐시 삭제 (보상 트랜잭션용)  
     */
    fun evictGroupAnalysis(groupId: String) {
        try {
            val key = "$GROUP_ANALYSIS_KEY_PREFIX$groupId"
            redisTemplate.delete(key)
            logger.debug("Evicted group analysis cache for group: {}", groupId)
        } catch (e: Exception) {
            logger.error("Failed to evict group analysis cache for group {}: {}", groupId, e.message, e)
        }
    }
    
    /**
     * 개인 분석 결과 일괄 캐시 저장 (배치 처리용)
     */
    fun cachePersonalAnalysisBatch(analyses: Map<String, PersonalAnalysis>) {
        if (analyses.isEmpty()) return
        
        try {
            // Pipeline을 사용해 여러 키를 한 번에 저장 (성능 최적화)
            redisTemplate.executePipelined { connection ->
                analyses.forEach { (userId, analysis) ->
                    val key = "$PERSONAL_ANALYSIS_KEY_PREFIX$userId"
                    val value = objectMapper.writeValueAsString(analysis)
                    connection.stringCommands().setEx(
                        key.toByteArray(), 
                        PERSONAL_ANALYSIS_TTL_HOURS * 3600, 
                        value.toByteArray()
                    )
                }
                null
            }
            
            logger.info("Cached {} personal analyses in batch", analyses.size)
        } catch (e: Exception) {
            logger.error("Failed to cache personal analyses in batch: {}", e.message, e)
        }
    }
    
    /**
     * 그룹 분석 결과 일괄 캐시 저장 (배치 처리용)
     */
    fun cacheGroupAnalysisBatch(analyses: Map<String, GroupAnalysis>) {
        if (analyses.isEmpty()) return
        
        try {
            // Pipeline을 사용해 여러 키를 한 번에 저장 (성능 최적화)
            redisTemplate.executePipelined { connection ->
                analyses.forEach { (groupId, analysis) ->
                    val key = "$GROUP_ANALYSIS_KEY_PREFIX$groupId"
                    val value = objectMapper.writeValueAsString(analysis)
                    connection.stringCommands().setEx(
                        key.toByteArray(), 
                        GROUP_ANALYSIS_TTL_HOURS * 3600, 
                        value.toByteArray()
                    )
                }
                null
            }
            
            logger.info("Cached {} group analyses in batch", analyses.size)
        } catch (e: Exception) {
            logger.error("Failed to cache group analyses in batch: {}", e.message, e)
        }
    }
    
    /**
     * 전체 분석 캐시 삭제 (보상 트랜잭션용)
     */
    fun evictAllAnalysisCache() {
        try {
            // 패턴을 사용해 관련 키들을 찾아서 삭제
            val personalKeys = redisTemplate.keys("$PERSONAL_ANALYSIS_KEY_PREFIX*")
            val groupKeys = redisTemplate.keys("$GROUP_ANALYSIS_KEY_PREFIX*")
            val metaKeys = setOf(META_LAST_UPDATE_KEY)
            
            val allKeys = personalKeys + groupKeys + metaKeys
            if (allKeys.isNotEmpty()) {
                redisTemplate.delete(allKeys)
                logger.info("Evicted {} analysis cache entries", allKeys.size)
            }
        } catch (e: Exception) {
            logger.error("Failed to evict all analysis cache: {}", e.message, e)
        }
    }
    
    /**
     * 개인 추천 결과 캐시 저장 (REFACTOR: 실제 구현)
     */
    fun cacheRecommendation(userId: String, recommendation: RecommendationResponse, ttlMinutes: Int = 60) {
        try {
            val key = "$RECOMMENDATION_KEY_PREFIX$userId"
            val value = objectMapper.writeValueAsString(recommendation)
            redisTemplate.opsForValue().set(key, value, ttlMinutes.toLong(), TimeUnit.MINUTES)
            logger.debug("Cached recommendation for user: {}", userId)
        } catch (e: Exception) {
            logger.error("Failed to cache recommendation for user {}: {}", userId, e.message, e)
        }
    }
    
    /**
     * 개인 추천 결과 캐시 조회 (REFACTOR: 실제 구현)
     */
    fun getRecommendationFromCache(userId: String): RecommendationResponse? {
        return try {
            val key = "$RECOMMENDATION_KEY_PREFIX$userId"
            val cachedValue = redisTemplate.opsForValue().get(key)
            
            if (cachedValue != null) {
                val recommendation = objectMapper.readValue(cachedValue, RecommendationResponse::class.java)
                logger.debug("Retrieved recommendation from cache for user: {}", userId)
                recommendation
            } else {
                logger.debug("No cached recommendation found for user: {}", userId)
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to retrieve recommendation from cache for user {}: {}", userId, e.message, e)
            null
        }
    }
    
    /**
     * 개인 추천 결과 캐시 삭제
     */
    fun evictRecommendation(userId: String) {
        try {
            val key = "$RECOMMENDATION_KEY_PREFIX$userId"
            redisTemplate.delete(key)
            logger.debug("Evicted recommendation cache for user: {}", userId)
        } catch (e: Exception) {
            logger.error("Failed to evict recommendation cache for user {}: {}", userId, e.message, e)
        }
    }
}