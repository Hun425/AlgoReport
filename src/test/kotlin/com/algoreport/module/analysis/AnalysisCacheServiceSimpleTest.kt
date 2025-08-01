package com.algoreport.module.analysis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * AnalysisCacheService 단순화된 테스트
 * 복잡한 Mock 설정을 최소화하여 핵심 기능만 테스트
 */
class AnalysisCacheServiceSimpleTest : BehaviorSpec() {
    
    init {
        given("간단한 AnalysisCacheService 테스트") {
            `when`("기본 Mock 설정으로 테스트하면") {
                then("정상 작동해야 한다") {
                    // 최소한의 Mock 설정
                    val redisTemplate = mockk<RedisTemplate<String, String>>(relaxed = true)
                    val valueOperations = mockk<ValueOperations<String, String>>(relaxed = true)
                    val objectMapper = ObjectMapper().apply {
                        registerModule(JavaTimeModule())
                    }
                    
                    // Mock 연결
                    every { redisTemplate.opsForValue() } returns valueOperations
                    
                    // 테스트 데이터
                    val personalAnalysis = PersonalAnalysis(
                        userId = "test-user",
                        analysisDate = LocalDateTime.now(),
                        totalSolved = 100,
                        currentTier = 10,
                        tagSkills = mapOf("dp" to 0.8),
                        solvedByDifficulty = mapOf("Silver" to 60),
                        recentActivity = mapOf("2025-08-01" to 3),
                        weakTags = listOf("greedy"),
                        strongTags = listOf("dp")
                    )
                    
                    // JSON 직렬화 테스트
                    val jsonString = objectMapper.writeValueAsString(personalAnalysis)
                    val deserializedAnalysis = objectMapper.readValue(jsonString, PersonalAnalysis::class.java)
                    
                    // 서비스 생성
                    val service = AnalysisCacheService(redisTemplate, objectMapper)
                    
                    // Mock 응답 설정
                    every { valueOperations.get("analysis:personal:test-user") } returns jsonString
                    
                    // 테스트 실행
                    service.cachePersonalAnalysis("test-user", personalAnalysis)
                    val cached = service.getPersonalAnalysisFromCache("test-user")
                    
                    // 검증
                    cached shouldNotBe null
                    cached!!.userId shouldBe "test-user"
                    cached.totalSolved shouldBe 100
                    
                    // Mock 호출 검증
                    verify { valueOperations.set("analysis:personal:test-user", any(), 6, TimeUnit.HOURS) }
                    verify { valueOperations.get("analysis:personal:test-user") }
                }
            }
        }
    }
}