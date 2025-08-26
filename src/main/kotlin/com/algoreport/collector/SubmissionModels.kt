package com.algoreport.collector

import com.algoreport.module.user.User
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

/**
 * Submission JPA Entity
 * Phase 1.4: solved.ac 제출 정보를 영속화하기 위한 Entity
 */
@Entity
@Table(name = "submissions")
data class Submission(
    @Id
    val submissionId: Long,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    
    @Column(nullable = false)
    val problemId: String,
    
    @Column(nullable = false)
    val problemTitle: String,
    
    @Column(nullable = false)
    val result: String,
    
    @Column(nullable = false)
    val language: String,
    
    @Column(nullable = false)
    val submittedAt: LocalDateTime,
    
    val codeLength: Int? = null,
    
    val runtime: Int? = null,
    
    val memory: Int? = null,
    
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * Submission 생성 요청 DTO
 */
data class SubmissionCreateRequest(
    val submissionId: Long,
    val userId: UUID,
    val problemId: String,
    val problemTitle: String,
    val result: String,
    val language: String,
    val submittedAt: LocalDateTime,
    val codeLength: Int? = null,
    val runtime: Int? = null,
    val memory: Int? = null
)