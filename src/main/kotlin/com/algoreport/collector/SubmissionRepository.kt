package com.algoreport.collector

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Submission JPA Repository
 * Phase 1.4: 인메모리 Repository를 JPA Repository로 변환
 */
@Repository
interface SubmissionRepository : JpaRepository<Submission, Long> {
    
    /**
     * 제출 ID로 중복 여부를 확인한다.
     */
    fun existsBySubmissionId(submissionId: Long): Boolean
    
    /**
     * 사용자 ID로 제출 목록 조회 (최신순)
     */
    fun findByUserIdOrderBySubmittedAtDesc(userId: UUID): List<Submission>
    
    /**
     * 사용자 ID와 날짜 범위로 제출 목록 조회
     */
    fun findByUserIdAndSubmittedAtBetween(
        userId: UUID, 
        startDate: java.time.LocalDateTime, 
        endDate: java.time.LocalDateTime
    ): List<Submission>
    
    // JpaRepository가 기본 제공하는 메서드들:
    // - save(submission: Submission): Submission
    // - findById(submissionId: Long): Optional<Submission>
    // - existsById(submissionId: Long): Boolean
    // - count(): Long
    // - delete(submission: Submission)
    // - findAll(): List<Submission>
}