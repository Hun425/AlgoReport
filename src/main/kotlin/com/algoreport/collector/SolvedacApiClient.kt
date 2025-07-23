package com.algoreport.collector

import com.algoreport.collector.dto.*
import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
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
 * TDD Refactor 단계: 코드 구조 개선 및 에러 처리 강화
 */
@Component
class SolvedacApiClientImpl(
    private val restTemplate: RestTemplate
) : SolvedacApiClient {
    
    private val logger = LoggerFactory.getLogger(SolvedacApiClientImpl::class.java)
    
    companion object {
        private const val SOLVEDAC_API_BASE_URL = "https://solved.ac/api/v3"
    }
    
    override fun getUserInfo(handle: String): UserInfo {
        validateHandle(handle)
        
        return try {
            val url = "$SOLVEDAC_API_BASE_URL/user/show?handle=$handle"
            logger.debug("Fetching user info for handle: {}", handle)
            
            val result = restTemplate.getForObject(url, UserInfo::class.java)
                ?: throw CustomException(Error.SOLVEDAC_USER_NOT_FOUND)
            
            logger.info("Successfully fetched user info for handle: {}", handle)
            result
        } catch (e: RestClientException) {
            logger.error("Failed to fetch user info for handle: {}", handle, e)
            throw CustomException(Error.SOLVEDAC_USER_NOT_FOUND)
        }
    }
    
    override fun getSubmissions(handle: String, page: Int): SubmissionList {
        validateHandle(handle)
        validatePage(page)
        
        return try {
            val url = "$SOLVEDAC_API_BASE_URL/search/submission?query=user:$handle&page=$page"
            logger.debug("Fetching submissions for handle: {}, page: {}", handle, page)
            
            val result = restTemplate.getForObject(url, SubmissionList::class.java)
                ?: SubmissionList(count = 0, items = emptyList())
            
            logger.info("Successfully fetched {} submissions for handle: {}, page: {}", 
                       result.items.size, handle, page)
            result
        } catch (e: RestClientException) {
            logger.error("Failed to fetch submissions for handle: {}, page: {}", handle, page, e)
            throw CustomException(Error.SOLVEDAC_API_ERROR)
        }
    }
    
    override fun getProblemInfo(problemId: Int): ProblemInfo {
        validateProblemId(problemId)
        
        return try {
            val url = "$SOLVEDAC_API_BASE_URL/problem/show?problemId=$problemId"
            logger.debug("Fetching problem info for problemId: {}", problemId)
            
            val result = restTemplate.getForObject(url, ProblemInfo::class.java)
                ?: throw CustomException(Error.PROBLEM_NOT_FOUND)
            
            logger.info("Successfully fetched problem info for problemId: {}", problemId)
            result
        } catch (e: RestClientException) {
            logger.error("Failed to fetch problem info for problemId: {}", problemId, e)
            throw CustomException(Error.PROBLEM_NOT_FOUND)
        }
    }
    
    private fun validateHandle(handle: String) {
        if (handle.isBlank()) {
            throw CustomException(Error.INVALID_INPUT)
        }
        if (handle.length > 50) {
            throw CustomException(Error.INVALID_INPUT)
        }
    }
    
    private fun validatePage(page: Int) {
        if (page < 1 || page > 1000) {
            throw CustomException(Error.INVALID_INPUT)
        }
    }
    
    private fun validateProblemId(problemId: Int) {
        if (problemId < 1) {
            throw CustomException(Error.INVALID_INPUT)
        }
    }
}