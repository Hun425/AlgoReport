package com.algoreport.collector

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
    }
})