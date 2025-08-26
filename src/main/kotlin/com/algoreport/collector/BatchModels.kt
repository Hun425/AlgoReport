package com.algoreport.collector

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.*

/**
 * 배치 계획 데이터 클래스
 */
data class BatchPlan(
    val userId: UUID,
    val handle: String,
    val batchSize: Int,
    val totalBatches: Int,
    val estimatedSubmissions: Int,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 배치 수집 결과 데이터 클래스
 */
data class BatchCollectionResult(
    val syncJobId: UUID,
    val batchNumber: Int,
    val collectedCount: Int,
    val successful: Boolean,
    val errorMessage: String? = null,
    val failedAt: LocalDateTime? = null,
    val retryAttempts: Int = 0
)

/**
 * 진행률 추적 데이터 클래스
 */
data class ProgressTracker(
    val syncJobId: UUID,
    val currentBatch: Int,
    val totalBatches: Int,
    val progressPercentage: Double,
    val isCompleted: Boolean
)

/**
 * 체크포인트 JPA Entity
 * Phase 1.1: 인메모리 DataSyncCheckpoint를 JPA Entity로 변환
 */
@Entity
@Table(name = "data_sync_checkpoints")
data class DataSyncCheckpoint(
    @Id
    val syncJobId: UUID,
    
    @Column(nullable = false)
    val userId: UUID,
    
    @Column(nullable = false)
    val currentBatch: Int,
    
    @Column(nullable = false)
    val totalBatches: Int,
    
    @Column(nullable = false)
    val lastProcessedSubmissionId: Long,
    
    @Column(nullable = false)
    val collectedCount: Int,
    
    @Column(nullable = false)
    val failedAttempts: Int = 0,
    
    @Column(nullable = false)
    val checkpointAt: LocalDateTime,
    
    @Column(nullable = false)
    val canResume: Boolean,
    
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 동기화 복구 결과 데이터 클래스
 */
data class SyncRecoveryResult(
    val syncJobId: UUID,
    val userId: UUID,
    val recoverySuccessful: Boolean,
    val resumedFromBatch: Int,
    val totalBatchesCompleted: Int,
    val failureReason: String? = null,
    val recoveredAt: LocalDateTime = LocalDateTime.now(),
    val recoveryDurationMinutes: Long = 0
)

/**
 * 동기화 작업 상태 열거형
 */
enum class SyncJobStatus {
    CREATED,
    IN_PROGRESS, 
    COMPLETED,
    FAILED,
    PARTIALLY_COMPLETED,
    RECOVERED
}

/**
 * 동기화 작업 데이터 클래스
 */
data class DataSyncJob(
    val syncJobId: UUID,
    val userId: UUID,
    val handle: String,
    val status: SyncJobStatus,
    val currentBatch: Int,
    val totalBatches: Int,
    val collectedCount: Int,
    val failedAttempts: Int = 0,
    val lastProcessedSubmissionId: Long = 0L,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val completedAt: LocalDateTime? = null
)