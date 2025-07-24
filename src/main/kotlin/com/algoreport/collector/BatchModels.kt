package com.algoreport.collector

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
 * 체크포인트 데이터 클래스
 */
data class DataSyncCheckpoint(
    val syncJobId: UUID,
    val userId: UUID,
    val currentBatch: Int,
    val totalBatches: Int,
    val lastProcessedSubmissionId: Long,
    val collectedCount: Int,
    val failedAttempts: Int = 0,
    val checkpointAt: LocalDateTime,
    val canResume: Boolean
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