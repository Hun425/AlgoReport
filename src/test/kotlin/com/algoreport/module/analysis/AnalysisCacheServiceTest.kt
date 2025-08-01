package com.algoreport.module.analysis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.connection.RedisConnectionFactory
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * AnalysisCacheService 테스트
 * TDD Red 단계: Redis 캐시 서비스의 모든 메서드 테스트
 * 
 * 🚨 주요 수정사항: Mock 설정 순서 및 타이밍 수정
 * - Mock 응답을 각 then 블록 시작 시점에 설정하도록 변경
 * - Jackson LocalDateTime 직렬화 문제 해결 (JavaTimeModule 추가)
 * - beforeEach Mock 초기화 순서 개선
 * 
 * 테스트 대상 메서드들:
 * - getPersonalAnalysisFromCache, cachePersonalAnalysis
 * - getGroupAnalysisFromCache, cacheGroupAnalysis  
 * - evictPersonalAnalysis, evictGroupAnalysis
 * - 배치 캐싱, 메타 데이터 관리, 전체 캐시 삭제
 */
class AnalysisCacheServiceTest : BehaviorSpec() {
    
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var objectMapper: ObjectMapper
    private lateinit var valueOperations: ValueOperations<String, String>
    private lateinit var analysisCacheService: AnalysisCacheService
    
    init {
        beforeEach {
            // 🎯 CODING_STANDARDS.md 문서 기준대로 정확히 설정
            redisTemplate = mockk()  // relaxed 제거
            valueOperations = mockk()  // relaxed 제거
            
            objectMapper = ObjectMapper().apply {
                registerModule(JavaTimeModule())  // LocalDateTime 지원 필수
            }
            
            // Mock 설정
            every { redisTemplate.opsForValue() } returns valueOperations
            every { valueOperations.set(any(), any(), any<Long>(), any()) } just runs
            every { valueOperations.get(any()) } returns null  // 기본값
            every { redisTemplate.delete(any<String>()) } returns true
            every { redisTemplate.delete(any<Collection<String>>()) } returns 3L
            every { redisTemplate.keys(any<String>()) } returns emptySet()
            
            analysisCacheService = AnalysisCacheService(redisTemplate, objectMapper)
        }
        
        given("AnalysisCacheService가 개인 분석 데이터를 캐시할 때") {
            val userId = "test-user-123"
            val personalAnalysis = PersonalAnalysis(
                userId = userId,
                analysisDate = LocalDateTime.now(),
                totalSolved = 150,
                currentTier = 15,
                tagSkills = mapOf("dp" to 0.8, "graph" to 0.6),
                solvedByDifficulty = mapOf("Bronze" to 50, "Silver" to 70, "Gold" to 30),
                recentActivity = mapOf("2025-08-01" to 5, "2025-07-31" to 3),
                weakTags = listOf("greedy", "geometry"),
                strongTags = listOf("dp", "graph")
            )
            
            `when`("개인 분석 데이터를 캐시에 저장하면") {
                then("캐시에서 동일한 데이터를 조회할 수 있어야 한다") {
                    // JSON 직렬화
                    val personalAnalysisJson = objectMapper.writeValueAsString(personalAnalysis)
                    
                    // Mock 설정: 특정 키에 대한 응답 설정
                    every { valueOperations.get("analysis:personal:$userId") } returns personalAnalysisJson
                    
                    // 캐시에 저장
                    analysisCacheService.cachePersonalAnalysis(userId, personalAnalysis)
                    
                    // 캐시에서 조회
                    val cachedAnalysis = analysisCacheService.getPersonalAnalysisFromCache(userId)
                    
                    // 검증
                    cachedAnalysis shouldNotBe null
                    cachedAnalysis!!.userId shouldBe userId
                    cachedAnalysis.totalSolved shouldBe 150
                    cachedAnalysis.currentTier shouldBe 15
                    cachedAnalysis.tagSkills shouldBe mapOf("dp" to 0.8, "graph" to 0.6)
                    
                    // Mock 호출 검증
                    verify { valueOperations.set("analysis:personal:$userId", personalAnalysisJson, 6, TimeUnit.HOURS) }
                    verify { valueOperations.get("analysis:personal:$userId") }
                }
            }
            
            `when`("존재하지 않는 사용자의 캐시를 조회하면") {
                then("null을 반환해야 한다") {
                    // Mock 설정: 존재하지 않는 키 조회
                    every { valueOperations.get("analysis:personal:non-existing-user") } returns null
                    
                    val cachedAnalysis = analysisCacheService.getPersonalAnalysisFromCache("non-existing-user")
                    
                    cachedAnalysis shouldBe null
                    verify { valueOperations.get("analysis:personal:non-existing-user") }
                }
            }
            
            `when`("개인 분석 캐시를 삭제하면") {
                then("캐시에서 해당 데이터가 제거되어야 한다") {
                    // Mock 설정: 삭제
                    every { redisTemplate.delete("analysis:personal:$userId") } returns true
                    
                    // 캐시 삭제
                    analysisCacheService.evictPersonalAnalysis(userId)
                    
                    // Mock 호출 검증
                    verify { redisTemplate.delete("analysis:personal:$userId") }
                }
            }
        }
        
        given("AnalysisCacheService가 그룹 분석 데이터를 캐시할 때") {
            val groupId = "test-group-456"
            val groupAnalysis = GroupAnalysis(
                groupId = groupId,
                analysisDate = LocalDateTime.now(),
                memberCount = 5,
                averageTier = 12.4,
                totalGroupSolved = 450,
                groupTagSkills = mapOf("dp" to 0.7, "graph" to 0.5),
                topPerformers = listOf("user1", "user2", "user3"),
                activeMemberRatio = 0.8,
                groupWeakTags = listOf("geometry"),
                groupStrongTags = listOf("dp", "graph")
            )
            
            `when`("그룹 분석 데이터를 캐시에 저장하면") {
                then("캐시에서 동일한 데이터를 조회할 수 있어야 한다") {
                    // **수정**: 테스트 시작 시점에 Mock 설정
                    val groupAnalysisJson = objectMapper.writeValueAsString(groupAnalysis)
                    
                    // Mock 설정: 특정 키에 대한 응답 설정
                    every { valueOperations.get("analysis:group:$groupId") } returns groupAnalysisJson
                    
                    // 캐시에 저장
                    analysisCacheService.cacheGroupAnalysis(groupId, groupAnalysis)
                    
                    // 캐시에서 조회
                    val cachedAnalysis = analysisCacheService.getGroupAnalysisFromCache(groupId)
                    
                    // 검증
                    cachedAnalysis shouldNotBe null
                    cachedAnalysis!!.groupId shouldBe groupId
                    cachedAnalysis.memberCount shouldBe 5
                    cachedAnalysis.averageTier shouldBe 12.4
                    cachedAnalysis.totalGroupSolved shouldBe 450
                    cachedAnalysis.activeMemberRatio shouldBe 0.8
                    
                    // Mock 호출 검증
                    verify { valueOperations.set("analysis:group:$groupId", groupAnalysisJson, 12, TimeUnit.HOURS) }
                    verify { valueOperations.get("analysis:group:$groupId") }
                }
            }
            
            `when`("존재하지 않는 그룹의 캐시를 조회하면") {
                then("null을 반환해야 한다") {
                    // Mock 설정: 존재하지 않는 키 조회
                    every { valueOperations.get("analysis:group:non-existing-group") } returns null
                    
                    val cachedAnalysis = analysisCacheService.getGroupAnalysisFromCache("non-existing-group")
                    
                    cachedAnalysis shouldBe null
                    verify { valueOperations.get("analysis:group:non-existing-group") }
                }
            }
            
            `when`("그룹 분석 캐시를 삭제하면") {
                then("캐시에서 해당 데이터가 제거되어야 한다") {
                    // Mock 설정: 삭제
                    every { redisTemplate.delete("analysis:group:$groupId") } returns true
                    
                    // 캐시 삭제
                    analysisCacheService.evictGroupAnalysis(groupId)
                    
                    // Mock 호출 검증
                    verify { redisTemplate.delete("analysis:group:$groupId") }
                }
            }
        }
        
        given("AnalysisCacheService가 배치 캐시 작업을 수행할 때") {
            val personalAnalysisMap = mapOf(
                "user1" to PersonalAnalysis(
                    userId = "user1",
                    analysisDate = LocalDateTime.now(),
                    totalSolved = 100,
                    currentTier = 10,
                    tagSkills = mapOf("dp" to 0.7),
                    solvedByDifficulty = mapOf("Silver" to 60),
                    recentActivity = mapOf("2025-08-01" to 3),
                    weakTags = listOf("greedy"),
                    strongTags = listOf("dp")
                ),
                "user2" to PersonalAnalysis(
                    userId = "user2",
                    analysisDate = LocalDateTime.now(),
                    totalSolved = 200,
                    currentTier = 15,
                    tagSkills = mapOf("graph" to 0.8),
                    solvedByDifficulty = mapOf("Gold" to 40),
                    recentActivity = mapOf("2025-08-01" to 5),
                    weakTags = listOf("geometry"),
                    strongTags = listOf("graph")
                )
            )
            
            val groupAnalysisMap = mapOf(
                "group1" to GroupAnalysis(
                    groupId = "group1",
                    analysisDate = LocalDateTime.now(),
                    memberCount = 3,
                    averageTier = 12.0,
                    totalGroupSolved = 300,
                    groupTagSkills = mapOf("dp" to 0.6),
                    topPerformers = listOf("user1", "user2"),
                    activeMemberRatio = 0.9,
                    groupWeakTags = listOf("geometry"),
                    groupStrongTags = listOf("dp")
                )
            )
            
            `when`("개인 분석 데이터를 배치로 캐시하면") {
                then("모든 데이터가 정상적으로 캐시되어야 한다") {
                    // **수정**: 테스트 시작 시점에 Mock 설정
                    val user1Json = objectMapper.writeValueAsString(personalAnalysisMap["user1"])
                    val user2Json = objectMapper.writeValueAsString(personalAnalysisMap["user2"])
                    
                    // Mock 설정: 배치 캐싱 후 조회를 위한 응답 설정
                    every { valueOperations.get("analysis:personal:user1") } returns user1Json
                    every { valueOperations.get("analysis:personal:user2") } returns user2Json
                    
                    // 배치 캐시 저장
                    analysisCacheService.cachePersonalAnalysisBatch(personalAnalysisMap)
                    
                    // 각각 조회해서 확인
                    val cachedUser1 = analysisCacheService.getPersonalAnalysisFromCache("user1")
                    val cachedUser2 = analysisCacheService.getPersonalAnalysisFromCache("user2")
                    
                    cachedUser1 shouldNotBe null
                    cachedUser1!!.totalSolved shouldBe 100
                    cachedUser2 shouldNotBe null
                    cachedUser2!!.totalSolved shouldBe 200
                }
            }
            
            `when`("그룹 분석 데이터를 배치로 캐시하면") {
                then("모든 데이터가 정상적으로 캐시되어야 한다") {
                    // **수정**: 테스트 시작 시점에 Mock 설정
                    val group1Json = objectMapper.writeValueAsString(groupAnalysisMap["group1"])
                    
                    // Mock 설정: 배치 캐싱 후 조회를 위한 응답 설정
                    every { valueOperations.get("analysis:group:group1") } returns group1Json
                    
                    // 배치 캐시 저장
                    analysisCacheService.cacheGroupAnalysisBatch(groupAnalysisMap)
                    
                    // 조회해서 확인
                    val cachedGroup1 = analysisCacheService.getGroupAnalysisFromCache("group1")
                    
                    cachedGroup1 shouldNotBe null
                    cachedGroup1!!.memberCount shouldBe 3
                    cachedGroup1.averageTier shouldBe 12.0
                }
            }
        }
        
        given("AnalysisCacheService가 마지막 업데이트 시간을 관리할 때") {
            val updateTime = LocalDateTime.now()
            
            `when`("마지막 업데이트 시간을 캐시에 저장하면") {
                then("캐시에서 동일한 시간을 조회할 수 있어야 한다") {
                    // **수정**: 테스트 시작 시점에 Mock 설정
                    val timeJson = objectMapper.writeValueAsString(mapOf("lastUpdate" to updateTime.toString()))
                    
                    // Mock 설정: 시간 조회를 위한 응답 설정
                    every { valueOperations.get("analysis:meta:last_update") } returns timeJson
                    
                    // 업데이트 시간 캐시
                    analysisCacheService.cacheLastUpdateTime(updateTime)
                    
                    // 캐시에서 조회
                    val cachedTime = analysisCacheService.getLastUpdateTimeFromCache()
                    
                    // 검증 (초 단위까지만 비교)
                    cachedTime shouldNotBe null
                    cachedTime!!.withNano(0) shouldBe updateTime.withNano(0)
                }
            }
            
            `when`("마지막 업데이트 시간이 캐시에 없으면") {
                then("null을 반환해야 한다") {
                    val cachedTime = analysisCacheService.getLastUpdateTimeFromCache()
                    
                    cachedTime shouldBe null
                }
            }
        }
        
        given("AnalysisCacheService가 전체 캐시를 삭제할 때") {
            `when`("모든 분석 캐시를 삭제하면") {
                then("모든 캐시 데이터가 제거되어야 한다") {
                    // 먼저 캐시 데이터 저장
                    val personalAnalysis = PersonalAnalysis(
                        userId = "test-user",
                        analysisDate = LocalDateTime.now(),
                        totalSolved = 100,
                        currentTier = 10,
                        tagSkills = mapOf("dp" to 0.7),
                        solvedByDifficulty = mapOf("Silver" to 60),
                        recentActivity = mapOf("2025-08-01" to 3),
                        weakTags = listOf("greedy"),
                        strongTags = listOf("dp")
                    )
                    
                    val groupAnalysis = GroupAnalysis(
                        groupId = "test-group",
                        analysisDate = LocalDateTime.now(),
                        memberCount = 3,
                        averageTier = 12.0,
                        totalGroupSolved = 300,
                        groupTagSkills = mapOf("dp" to 0.6),
                        topPerformers = listOf("user1"),
                        activeMemberRatio = 0.9,
                        groupWeakTags = listOf("geometry"),
                        groupStrongTags = listOf("dp")
                    )
                    
                    // **수정**: 테스트 시작 시점에 Mock 설정
                    val personalAnalysisJson = objectMapper.writeValueAsString(personalAnalysis)
                    val groupAnalysisJson = objectMapper.writeValueAsString(groupAnalysis)
                    val timeJson = objectMapper.writeValueAsString(mapOf("lastUpdate" to LocalDateTime.now().toString()))
                    
                    // Mock 설정: 저장 후 조회, 삭제 후 null 반환
                    every { valueOperations.get("analysis:personal:test-user") } returns personalAnalysisJson andThen null
                    every { valueOperations.get("analysis:group:test-group") } returns groupAnalysisJson andThen null
                    every { valueOperations.get("analysis:meta:last_update") } returns timeJson andThen null
                    
                    // 전체 캐시 삭제를 위한 Mock 설정
                    every { redisTemplate.keys("analysis:personal:*") } returns setOf("analysis:personal:test-user")
                    every { redisTemplate.keys("analysis:group:*") } returns setOf("analysis:group:test-group") 
                    every { redisTemplate.delete(any<Collection<String>>()) } returns 3L
                    
                    analysisCacheService.cachePersonalAnalysis("test-user", personalAnalysis)
                    analysisCacheService.cacheGroupAnalysis("test-group", groupAnalysis)
                    analysisCacheService.cacheLastUpdateTime(LocalDateTime.now())
                    
                    // 캐시 데이터 존재 확인
                    analysisCacheService.getPersonalAnalysisFromCache("test-user") shouldNotBe null
                    analysisCacheService.getGroupAnalysisFromCache("test-group") shouldNotBe null
                    analysisCacheService.getLastUpdateTimeFromCache() shouldNotBe null
                    
                    // 전체 캐시 삭제
                    analysisCacheService.evictAllAnalysisCache()
                    
                    // 삭제 확인
                    analysisCacheService.getPersonalAnalysisFromCache("test-user") shouldBe null
                    analysisCacheService.getGroupAnalysisFromCache("test-group") shouldBe null
                    analysisCacheService.getLastUpdateTimeFromCache() shouldBe null
                }
            }
        }
    }
}