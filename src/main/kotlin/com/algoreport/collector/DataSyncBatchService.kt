package com.algoreport.collector

import java.time.LocalDateTime
import java.util.*

/**
 * 대용량 배치 수집 서비스 인터페이스
 * 
 * TDD Red 단계: 인터페이스만 정의하고 구현은 없음
 * Task 1-1-4: 대용량 배치 수집 기본 구조
 */
interface DataSyncBatchService {
    
    /**
     * 배치 작업 계획 수립
     * 
     * @param userId 사용자 ID
     * @param handle solved.ac 핸들
     * @param syncPeriodMonths 동기화할 기간 (개월)
     * @param batchSize 배치 크기
     * @return 배치 계획
     */
    fun createBatchPlan(
        userId: UUID,
        handle: String,
        syncPeriodMonths: Int,
        batchSize: Int
    ): BatchPlan
    
    /**
     * 단일 배치 데이터 수집
     * 
     * @param syncJobId 동기화 작업 ID
     * @param handle solved.ac 핸들
     * @param batchNumber 배치 번호
     * @param batchSize 배치 크기
     * @return 배치 수집 결과
     */
    fun collectBatch(
        syncJobId: UUID,
        handle: String,
        batchNumber: Int,
        batchSize: Int
    ): BatchCollectionResult
    
    /**
     * 수집 진행률 계산
     * 
     * @param syncJobId 동기화 작업 ID
     * @param currentBatch 현재 배치
     * @param totalBatches 전체 배치 수
     * @return 진행률 정보
     */
    fun calculateProgress(
        syncJobId: UUID,
        currentBatch: Int,
        totalBatches: Int
    ): ProgressTracker
    
    /**
     * 체크포인트 저장
     * 
     * @param syncJobId 동기화 작업 ID
     * @param userId 사용자 ID
     * @param currentBatch 현재 배치
     * @param totalBatches 전체 배치 수
     * @param lastProcessedSubmissionId 마지막 처리된 제출 ID
     * @param collectedCount 수집된 개수
     * @return 저장된 체크포인트
     */
    fun saveCheckpoint(
        syncJobId: UUID,
        userId: UUID,
        currentBatch: Int,
        totalBatches: Int,
        lastProcessedSubmissionId: Long,
        collectedCount: Int
    ): DataSyncCheckpoint
    
    /**
     * Kotlin Coroutines를 이용한 병렬 배치 처리
     * Virtual Thread보다 메모리 효율적이고 수천만 개의 동시 처리 가능
     * 
     * @param syncJobId 동기화 작업 ID
     * @param handle solved.ac 핸들
     * @param totalBatches 전체 배치 수
     * @param batchSize 배치 크기
     * @return 모든 배치 수집 결과
     */
    suspend fun collectBatchesInParallel(
        syncJobId: UUID,
        handle: String,
        totalBatches: Int,
        batchSize: Int
    ): List<BatchCollectionResult>
    
    /**
     * 에러 처리가 포함된 배치 수집
     * 
     * @param syncJobId 동기화 작업 ID
     * @param handle solved.ac 핸들
     * @param batchNumber 배치 번호
     * @param batchSize 배치 크기
     * @param simulateError 에러 시뮬레이션 여부 (테스트용)
     * @return 배치 수집 결과
     */
    fun collectBatchWithErrorHandling(
        syncJobId: UUID,
        handle: String,
        batchNumber: Int,
        batchSize: Int,
        simulateError: Boolean = false
    ): BatchCollectionResult
    
    /**
     * 체크포인트로부터 배치 수집 재시작
     * 
     * @param syncJobId 동기화 작업 ID
     * @return 재시작 성공 여부
     */
    fun resumeFromCheckpoint(syncJobId: UUID): Boolean
    
    /**
     * 실패한 동기화 작업 복구
     * 
     * @param userId 사용자 ID
     * @param handle solved.ac 핸들
     * @param maxRetryAttempts 최대 재시도 횟수
     * @return 복구 결과
     */
    fun recoverFailedSync(
        userId: UUID,
        handle: String,
        maxRetryAttempts: Int = 3
    ): SyncRecoveryResult
}

/**
 * 체크포인트 리포지토리 인터페이스
 * 
 * TDD Red 단계: 인터페이스만 정의
 */
interface DataSyncCheckpointRepository {
    
    /**
     * 체크포인트 저장
     */
    fun save(checkpoint: DataSyncCheckpoint): DataSyncCheckpoint
    
    /**
     * 동기화 작업 ID로 체크포인트 조회
     */
    fun findBySyncJobId(syncJobId: UUID): DataSyncCheckpoint?
    
    /**
     * 사용자 ID로 최신 체크포인트 조회
     */
    fun findLatestByUserId(userId: UUID): DataSyncCheckpoint?
}