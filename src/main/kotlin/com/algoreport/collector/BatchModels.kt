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