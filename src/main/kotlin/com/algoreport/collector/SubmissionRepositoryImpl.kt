package com.algoreport.collector

import com.algoreport.collector.dto.Submission
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * 제출 리포지토리 구현체
 * 
 * TDD Green 단계: 테스트 통과를 위한 최소한의 구현
 * 기존 UserService 패턴을 따라 인메모리 저장소 사용
 */
@Repository
class SubmissionRepositoryImpl : SubmissionRepository {
    
    private val logger = LoggerFactory.getLogger(SubmissionRepositoryImpl::class.java)
    
    // 기존 UserService 패턴을 따라 ConcurrentHashMap 사용
    private val submissions = ConcurrentHashMap<Long, Submission>()
    
    override fun existsBySubmissionId(submissionId: Long): Boolean {
        logger.debug("Checking existence for submission ID: {}", submissionId)
        
        val exists = submissions.containsKey(submissionId)
        logger.debug("Submission ID {} exists: {}", submissionId, exists)
        
        return exists
    }
    
    override fun save(submission: Submission): Submission {
        logger.debug("Saving submission ID: {}", submission.submissionId)
        
        submissions[submission.submissionId] = submission
        
        logger.info("Saved submission ID: {} for user: {}", 
                   submission.submissionId, submission.user.handle)
        
        return submission
    }
    
    /**
     * 테스트용 제출 조회 메서드 (기존 UserService 패턴)
     */
    fun findBySubmissionId(submissionId: Long): Submission? {
        return submissions[submissionId]
    }
    
    /**
     * 테스트용 전체 제출 수 조회
     */
    fun count(): Int {
        return submissions.size
    }
    
    /**
     * 테스트용 초기화 메서드 (기존 UserService 패턴)
     */
    fun clear() {
        submissions.clear()
        logger.debug("Cleared all submissions")
    }
}