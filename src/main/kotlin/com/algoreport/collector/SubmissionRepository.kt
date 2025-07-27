package com.algoreport.collector

import com.algoreport.collector.dto.Submission

/**
 * 제출 리포지토리 인터페이스
 */
interface SubmissionRepository {
    /**
     * 제출 ID로 중복 여부를 확인한다.
     */
    fun existsBySubmissionId(submissionId: Long): Boolean
    
    /**
     * 제출 데이터를 저장한다.
     */
    fun save(submission: Submission): Submission
}