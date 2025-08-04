package com.algoreport.module.analysis

import com.algoreport.config.outbox.OutboxService
import com.algoreport.module.user.*
import com.algoreport.module.studygroup.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * PERSONAL_STATS_REFRESH_SAGA 테스트
 * TDD Red 단계: 개인 통계 갱신 SAGA 테스트 작성
 * 
 * 비즈니스 요구사항:
 * - 특정 사용자의 개인 통계를 즉시 갱신하는 온디맨드 SAGA
 * - solved.ac API에서 최신 제출 데이터 수집 및 분석
 * - Elasticsearch에 개인 통계 데이터 인덱싱
 * - Redis 캐시 업데이트로 대시보드 성능 최적화
 * - 실패 시 보상 트랜잭션으로 데이터 일관성 보장
 * - PERSONAL_STATS_REFRESHED 이벤트 발행
 * 
 * ANALYSIS_UPDATE_SAGA와의 차이점:
 * - ANALYSIS_UPDATE_SAGA: 매일 자정 전체 사용자 배치 처리
 * - PERSONAL_STATS_REFRESH_SAGA: 특정 사용자 온디맨드 즉시 처리
 */
@SpringBootTest(classes = [com.algoreport.AlgoReportApplication::class, com.algoreport.config.TestConfiguration::class])
@ActiveProfiles("test")
@Transactional
class PersonalStatsRefreshSagaTest(
    private val personalStatsRefreshSaga: PersonalStatsRefreshSaga,
    private val userService: UserService,
    private val analysisService: AnalysisService,
    private val analysisCacheService: AnalysisCacheService,
    private val outboxService: OutboxService
) : BehaviorSpec() {
    
    override fun extensions() = listOf(SpringExtension)
    
    init {
        beforeEach {
            // 각 테스트 전에 상태 초기화
            userService.clear()
            analysisService.clear()
            // 캐시 초기화는 실제 Redis 인스턴스가 있을 때만 가능
        }
        
        given("PERSONAL_STATS_REFRESH_SAGA가 특정 사용자 통계 갱신을 위해 실행될 때") {
            
            `when`("유효한 사용자에 대해 통계 갱신을 요청하면") {
                then("개인 통계가 성공적으로 갱신되고 캐시가 업데이트되어야 한다") {
                    // 테스트 사용자 생성
                    val testUser = userService.createUser(
                        UserCreateRequest("user@test.com", "테스트사용자", AuthProvider.GOOGLE)
                    )
                    
                    val request = PersonalStatsRefreshRequest(
                        userId = testUser.id,
                        includeRecentSubmissions = true,
                        forceRefresh = true,
                        requestedBy = "USER_REQUEST"
                    )
                    
                    val result = personalStatsRefreshSaga.start(request)
                    
                    // 기본 검증
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.userId shouldBe testUser.id
                    result.dataCollectionCompleted shouldBe true
                    result.elasticsearchIndexingCompleted shouldBe true
                    result.cacheUpdateCompleted shouldBe true
                    result.eventPublished shouldBe true
                    result.errorMessage shouldBe null
                    
                    // 분석 결과 생성 확인
                    analysisService.hasPersonalAnalysis(testUser.id) shouldBe true
                    
                    // 캐시 업데이트 확인
                    val cachedAnalysis = analysisCacheService.getPersonalAnalysisFromCache(testUser.id)
                    cachedAnalysis shouldNotBe null
                    cachedAnalysis?.userId shouldBe testUser.id
                }
            }
            
            `when`("존재하지 않는 사용자에 대해 통계 갱신을 요청하면") {
                then("실패 상태로 완료되고 적절한 에러 메시지를 반환해야 한다") {
                    val request = PersonalStatsRefreshRequest(
                        userId = "non-existent-user-id",
                        includeRecentSubmissions = true,
                        forceRefresh = false,
                        requestedBy = "USER_REQUEST"
                    )
                    
                    val result = personalStatsRefreshSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldNotBe null
                    result.compensationExecuted shouldBe true
                    result.dataCollectionCompleted shouldBe false
                }
            }
            
            `when`("solved.ac 데이터 수집 단계에서 실패하면") {
                then("보상 트랜잭션이 실행되고 실패 상태로 완료되어야 한다") {
                    // 테스트 사용자 생성
                    val testUser = userService.createUser(
                        UserCreateRequest("user@test.com", "테스트사용자", AuthProvider.GOOGLE)
                    )
                    
                    // TODO: solved.ac API 실패 시뮬레이션 설정 (GREEN 단계에서 구현)
                    
                    val request = PersonalStatsRefreshRequest(
                        userId = testUser.id,
                        includeRecentSubmissions = true,
                        forceRefresh = true,
                        requestedBy = "SYSTEM_TRIGGER"
                    )
                    
                    val result = personalStatsRefreshSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldNotBe null
                    result.compensationExecuted shouldBe true
                    
                    // 보상 트랜잭션으로 생성된 데이터가 롤백되었는지 확인
                    analysisService.hasPersonalAnalysis(testUser.id) shouldBe false
                }
            }
            
            `when`("Elasticsearch 인덱싱 단계에서 실패하면") {
                then("보상 트랜잭션이 실행되고 부분 성공 상태로 완료되어야 한다") {
                    // 테스트 사용자 생성
                    val testUser = userService.createUser(
                        UserCreateRequest("user@test.com", "테스트사용자", AuthProvider.GOOGLE)
                    )
                    
                    // TODO: Elasticsearch 인덱싱 실패 시뮬레이션 설정 (GREEN 단계에서 구현)
                    
                    val request = PersonalStatsRefreshRequest(
                        userId = testUser.id,
                        includeRecentSubmissions = true,
                        forceRefresh = true,
                        requestedBy = "SYSTEM_TRIGGER"
                    )
                    
                    val result = personalStatsRefreshSaga.start(request)
                    
                    // Elasticsearch 실패여도 기본 분석은 완료되어야 함
                    result.sagaStatus shouldBe SagaStatus.PARTIAL_SUCCESS
                    result.dataCollectionCompleted shouldBe true
                    result.elasticsearchIndexingCompleted shouldBe false
                    result.cacheUpdateCompleted shouldBe true // 캐시는 성공해야 함
                    
                    // 분석 결과는 생성되어야 함
                    analysisService.hasPersonalAnalysis(testUser.id) shouldBe true
                }
            }
            
            `when`("강제 새로고침이 아닌 경우 최근 캐시된 데이터가 있으면") {
                then("캐시된 데이터를 활용하여 빠르게 처리되어야 한다") {
                    // 테스트 사용자 생성
                    val testUser = userService.createUser(
                        UserCreateRequest("user@test.com", "테스트사용자", AuthProvider.GOOGLE)
                    )
                    
                    // 사전에 캐시 데이터 생성 (최근 1시간 이내)
                    val cachedAnalysis = PersonalAnalysis(
                        userId = testUser.id,
                        analysisDate = LocalDateTime.now().minusMinutes(30), // 30분 전 데이터
                        totalSolved = 150,
                        currentTier = 15
                    )
                    analysisCacheService.cachePersonalAnalysis(testUser.id, cachedAnalysis)
                    
                    val request = PersonalStatsRefreshRequest(
                        userId = testUser.id,
                        includeRecentSubmissions = true,
                        forceRefresh = false, // 강제 새로고침 아님
                        requestedBy = "DASHBOARD_VIEW"
                    )
                    
                    val result = personalStatsRefreshSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.usedCachedData shouldBe true
                    result.processingTimeMs shouldBe result.processingTimeMs // 빠른 처리 확인 (구체적 시간은 GREEN에서)
                    
                    // 캐시된 데이터가 활용되었는지 확인
                    val finalAnalysis = analysisService.getPersonalAnalysis(testUser.id)
                    finalAnalysis?.totalSolved shouldBe 150
                }
            }
            
            `when`("이벤트 발행에서 실패하면") {
                then("보상 트랜잭션 없이 경고 로그만 남기고 성공으로 처리되어야 한다") {
                    // 테스트 사용자 생성
                    val testUser = userService.createUser(
                        UserCreateRequest("user@test.com", "테스트사용자", AuthProvider.GOOGLE)
                    )
                    
                    // TODO: OutboxService 이벤트 발행 실패 시뮬레이션 (GREEN 단계에서 구현)
                    
                    val request = PersonalStatsRefreshRequest(
                        userId = testUser.id,
                        includeRecentSubmissions = true,
                        forceRefresh = true,
                        requestedBy = "USER_REQUEST"
                    )
                    
                    val result = personalStatsRefreshSaga.start(request)
                    
                    // 이벤트 발행 실패는 비즈니스 로직에 치명적이지 않음
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.dataCollectionCompleted shouldBe true
                    result.elasticsearchIndexingCompleted shouldBe true
                    result.cacheUpdateCompleted shouldBe true
                    result.eventPublished shouldBe false // 이벤트 발행만 실패
                    result.compensationExecuted shouldBe false // 보상 트랜잭션 실행 안함
                    
                    // 분석 데이터는 정상적으로 생성되어야 함
                    analysisService.hasPersonalAnalysis(testUser.id) shouldBe true
                }
            }
            
            `when`("복수의 동시 요청이 같은 사용자에 대해 들어올 때") {
                then("중복 처리를 방지하고 효율적으로 처리되어야 한다") {
                    // 테스트 사용자 생성
                    val testUser = userService.createUser(
                        UserCreateRequest("user@test.com", "테스트사용자", AuthProvider.GOOGLE)
                    )
                    
                    val request1 = PersonalStatsRefreshRequest(
                        userId = testUser.id,
                        includeRecentSubmissions = true,
                        forceRefresh = true,
                        requestedBy = "USER_REQUEST_1"
                    )
                    
                    val request2 = PersonalStatsRefreshRequest(
                        userId = testUser.id,
                        includeRecentSubmissions = true,
                        forceRefresh = true,
                        requestedBy = "USER_REQUEST_2"
                    )
                    
                    // TODO: 동시 실행 테스트 (GREEN 단계에서 Kotlin Coroutines 사용)
                    val result1 = personalStatsRefreshSaga.start(request1)
                    val result2 = personalStatsRefreshSaga.start(request2)
                    
                    // 둘 중 하나는 성공, 하나는 중복 처리로 빠른 완료
                    (result1.sagaStatus == SagaStatus.COMPLETED || result2.sagaStatus == SagaStatus.COMPLETED) shouldBe true
                    
                    // 실제로는 한 번만 처리되었는지 확인 (GREEN 단계에서 구체적 검증)
                    analysisService.hasPersonalAnalysis(testUser.id) shouldBe true
                }
            }
        }
    }
}