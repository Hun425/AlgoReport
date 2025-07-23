package com.algoreport.collector

import com.algoreport.collector.dto.*
import org.springframework.web.client.RestTemplate

/**
 * solved.ac API 클라이언트 인터페이스
 * 
 * TDD Red 단계: 인터페이스만 정의하고 구현은 없음
 */
interface SolvedacApiClient {
    
    /**
     * 사용자 정보 조회
     * 
     * @param handle 사용자 핸들
     * @return 사용자 정보
     */
    fun getUserInfo(handle: String): UserInfo
    
    /**
     * 사용자 제출 이력 조회
     * 
     * @param handle 사용자 핸들
     * @param page 페이지 번호 (1부터 시작)
     * @return 제출 이력 리스트
     */
    fun getSubmissions(handle: String, page: Int = 1): SubmissionList
    
    /**
     * 문제 상세 정보 조회
     * 
     * @param problemId 문제 번호
     * @return 문제 상세 정보
     */
    fun getProblemInfo(problemId: Int): ProblemInfo
}

/**
 * solved.ac API 클라이언트 구현체
 * 
 * TDD Red 단계: 빈 구현으로 테스트 실패 유도
 */
class SolvedacApiClient(
    private val restTemplate: RestTemplate
) : SolvedacApiClient {
    
    override fun getUserInfo(handle: String): UserInfo {
        TODO("Not yet implemented - TDD Red 단계")
    }
    
    override fun getSubmissions(handle: String, page: Int): SubmissionList {
        TODO("Not yet implemented - TDD Red 단계")
    }
    
    override fun getProblemInfo(problemId: Int): ProblemInfo {
        TODO("Not yet implemented - TDD Red 단계")
    }
}