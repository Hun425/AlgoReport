package com.algoreport.collector

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.mockk.mockk
import org.springframework.web.client.RestTemplate

/**
 * solved.ac API 클라이언트 테스트
 * 
 * TDD Red 단계: 실패하는 테스트를 먼저 작성
 */
class SolvedacApiClientTest : BehaviorSpec({
    
    given("SolvedacApiClient") {
        val restTemplate = mockk<RestTemplate>()
        val solvedacApiClient = SolvedacApiClient(restTemplate)
        
        `when`("사용자 정보를 조회할 때") {
            val handle = "testuser"
            
            then("UserInfo 객체를 반환해야 한다") {
                val result = solvedacApiClient.getUserInfo(handle)
                
                result shouldNotBe null
                result.handle shouldBe handle
            }
        }
        
        `when`("사용자의 제출 이력을 조회할 때") {
            val handle = "testuser"
            val page = 1
            
            then("SubmissionList 객체를 반환해야 한다") {
                val result = solvedacApiClient.getSubmissions(handle, page)
                
                result shouldNotBe null
                result.items.size shouldBe 0 // 초기에는 빈 리스트
            }
        }
        
        `when`("문제 정보를 조회할 때") {
            val problemId = 1000
            
            then("ProblemInfo 객체를 반환해야 한다") {
                val result = solvedacApiClient.getProblemInfo(problemId)
                
                result shouldNotBe null
                result.problemId shouldBe problemId
            }
        }
        
        `when`("대용량 배치 수집을 위한 배치 계획을 수립할 때") {
            val handle = "testuser"
            val syncPeriodMonths = 6
            val batchSize = 100
            
            then("배치 계획이 올바르게 생성되어야 한다") {
                // RED 단계: 아직 구현되지 않은 기능에 대한 테스트
                val dataSyncService = DataSyncBatchService(solvedacApiClient, mockk())
                
                val batchPlan = dataSyncService.createBatchPlan(
                    userId = java.util.UUID.randomUUID(),
                    handle = handle,
                    syncPeriodMonths = syncPeriodMonths,
                    batchSize = batchSize
                )
                
                // 이 테스트는 실패해야 함 (구현체가 없으므로)
                batchPlan shouldNotBe null
                batchPlan.batchSize shouldBe batchSize
                batchPlan.totalBatches shouldBeGreaterThan 0
            }
        }
    }
})