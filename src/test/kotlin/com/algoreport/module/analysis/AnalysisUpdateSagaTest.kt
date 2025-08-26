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
 * ANALYSIS_UPDATE_SAGA 테스트
 * TDD Red 단계: ANALYSIS_UPDATE_SAGA 관련 클래스들이 존재하지 않으므로 컴파일 실패 예상
 * 
 * 비즈니스 요구사항:
 * - 매일 자정에 자동 실행되는 분석 업데이트 SAGA (@Scheduled)
 * - 개인 통계 분석: solved.ac 제출 데이터 기반 개인별 태그 숙련도, 티어 변화 등
 * - 그룹 통계 분석: 그룹별 활동 통계, 그룹원 성과 분석 등
 * - Kotlin Coroutines 병렬 처리: 사용자 100명씩 배치 처리
 * - Elasticsearch 집계 쿼리: 대용량 제출 데이터 집계 분석
 * - Redis 캐시 업데이트: 분석 결과를 캐시하여 대시보드 성능 최적화
 * - 실패 시 보상 트랜잭션: 부분 실패 시 일관성 보장
 */
@SpringBootTest(classes = [com.algoreport.AlgoReportApplication::class, com.algoreport.config.TestConfiguration::class])
@ActiveProfiles("test")
@Transactional
class AnalysisUpdateSagaTest(
    private val analysisUpdateSaga: AnalysisUpdateSaga,
    private val userService: UserService,
    private val studyGroupService: StudyGroupService,
    private val analysisService: AnalysisService,
    private val outboxService: OutboxService
) : BehaviorSpec() {
    
    override fun extensions() = listOf(SpringExtension)
    
    init {
        beforeEach {
            // 각 테스트 전에 상태 초기화
            // userService.clear() // JPA Repository 사용으로 불필요
            studyGroupService.clear()
            analysisService.clear()
        }
        
        given("ANALYSIS_UPDATE_SAGA가 매일 자정에 실행될 때") {
            val analysisDate = LocalDateTime.now()
            
            `when`("분석할 사용자들이 존재하면") {
                then("개인 및 그룹 통계 분석이 성공적으로 완료되어야 한다") {
                    // 테스트 사용자 생성
                    val user1 = userService.createUser(UserCreateRequest("user1@test.com", "사용자1", AuthProvider.GOOGLE))
                    val user2 = userService.createUser(UserCreateRequest("user2@test.com", "사용자2", AuthProvider.GOOGLE))
                    
                    // 테스트 그룹 생성
                    val group = studyGroupService.createGroup(CreateGroupRequest(user1.id, "알고리즘 스터디", "매일 문제 풀이"))
                    studyGroupService.addMember(group.id, user2.id)
                    
                    val request = AnalysisUpdateRequest(
                        analysisDate = analysisDate,
                        batchSize = 100,
                        enablePersonalAnalysis = true,
                        enableGroupAnalysis = true
                    )
                    
                    val result = analysisUpdateSaga.start(request)
                    
                    // 검증
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.totalUsersProcessed shouldBe 2
                    result.totalGroupsProcessed shouldBe 1
                    result.personalAnalysisCompleted shouldBe true
                    result.groupAnalysisCompleted shouldBe true
                    result.cacheUpdateCompleted shouldBe true
                    result.errorMessage shouldBe null
                    
                    // 분석 결과 검증
                    analysisService.hasPersonalAnalysis(user1.id.toString()) shouldBe true
                    analysisService.hasPersonalAnalysis(user2.id.toString()) shouldBe true
                    analysisService.hasGroupAnalysis(group.id) shouldBe true
                }
            }
            
            `when`("분석할 사용자가 없으면") {
                then("성공 상태로 완료되지만 처리된 사용자 수는 0이어야 한다") {
                    val request = AnalysisUpdateRequest(
                        analysisDate = analysisDate,
                        batchSize = 100,
                        enablePersonalAnalysis = true,
                        enableGroupAnalysis = true
                    )
                    
                    val result = analysisUpdateSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.totalUsersProcessed shouldBe 0
                    result.totalGroupsProcessed shouldBe 0
                    result.personalAnalysisCompleted shouldBe true
                    result.groupAnalysisCompleted shouldBe true
                    result.errorMessage shouldBe null
                }
            }
            
            `when`("개인 분석 단계에서 실패하면") {
                then("보상 트랜잭션이 실행되고 실패 상태로 완료되어야 한다") {
                    // 테스트 사용자 생성
                    val user = userService.createUser(UserCreateRequest("user@test.com", "사용자", AuthProvider.GOOGLE))
                    
                    // 개인 분석 실패 시뮬레이션
                    analysisService.simulatePersonalAnalysisFailure = true
                    
                    val request = AnalysisUpdateRequest(
                        analysisDate = analysisDate,
                        batchSize = 100,
                        enablePersonalAnalysis = true,
                        enableGroupAnalysis = true
                    )
                    
                    val result = analysisUpdateSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldNotBe null
                    result.compensationExecuted shouldBe true
                    
                    // 보상 트랜잭션으로 상태가 롤백되었는지 확인
                    analysisService.hasPersonalAnalysis(user.id.toString()) shouldBe false
                }
            }
            
            `when`("그룹 분석 단계에서 실패하면") {
                then("보상 트랜잭션이 실행되고 실패 상태로 완료되어야 한다") {
                    // 테스트 데이터 생성
                    val user = userService.createUser(UserCreateRequest("user@test.com", "사용자", AuthProvider.GOOGLE))
                    val group = studyGroupService.createGroup(CreateGroupRequest(user.id, "테스트 그룹", "설명"))
                    
                    // 그룹 분석 실패 시뮬레이션
                    analysisService.simulateGroupAnalysisFailure = true
                    
                    val request = AnalysisUpdateRequest(
                        analysisDate = analysisDate,
                        batchSize = 100,
                        enablePersonalAnalysis = true,
                        enableGroupAnalysis = true
                    )
                    
                    val result = analysisUpdateSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.FAILED
                    result.errorMessage shouldNotBe null
                    result.compensationExecuted shouldBe true
                    
                    // 보상 트랜잭션으로 상태가 롤백되었는지 확인
                    analysisService.hasGroupAnalysis(group.id) shouldBe false
                }
            }
            
            `when`("대용량 사용자 데이터를 병렬 처리할 때") {
                then("배치 크기에 따라 적절히 분할되어 처리되어야 한다") {
                    // 대용량 테스트 사용자 생성 (10명)
                    val users = (1..10).map { i ->
                        userService.createUser(UserCreateRequest("user$i@test.com", "사용자$i", AuthProvider.GOOGLE))
                    }
                    
                    val request = AnalysisUpdateRequest(
                        analysisDate = analysisDate,
                        batchSize = 3, // 3명씩 배치 처리
                        enablePersonalAnalysis = true,
                        enableGroupAnalysis = false
                    )
                    
                    val result = analysisUpdateSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.totalUsersProcessed shouldBe 10
                    result.batchesProcessed shouldBe 4 // 10명을 3명씩 나누면 4배치 (3+3+3+1)
                    result.personalAnalysisCompleted shouldBe true
                    
                    // 모든 사용자의 개인 분석이 완료되었는지 확인
                    users.forEach { user ->
                        analysisService.hasPersonalAnalysis(user.id.toString()) shouldBe true
                    }
                }
            }
            
            `when`("분석 완료 후 이벤트가 발행되어야 할 때") {
                then("ANALYSIS_UPDATE_COMPLETED 이벤트가 발행되어야 한다") {
                    val request = AnalysisUpdateRequest(
                        analysisDate = analysisDate,
                        batchSize = 100,
                        enablePersonalAnalysis = true,
                        enableGroupAnalysis = true
                    )
                    
                    val result = analysisUpdateSaga.start(request)
                    
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.eventPublished shouldBe true
                    
                    // OutboxService를 통해 이벤트가 발행되었는지 확인
                    // (실제 구현에서는 Outbox 테이블 확인 또는 Mock 검증)
                }
            }
        }
    }
}