package com.algoreport.collector

import com.algoreport.config.outbox.OutboxService
import com.algoreport.module.user.UserRepository
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.*
import com.algoreport.collector.Submission as EntitySubmission

/**
 * 실시간 제출 동기화 SAGA
 * 
 * 5분마다 활성 사용자들의 새로운 제출을 수집하고 처리한다.
 */
@Component
class SubmissionSyncSaga(
    private val solvedacApiClient: SolvedacApiClient,
    private val submissionSyncService: SubmissionSyncService,
    private val outboxService: OutboxService,
    private val submissionRepository: SubmissionRepository,
    private val userRepository: UserRepository,
    @param:Qualifier("schedulingCoroutineScope") private val coroutineScope: CoroutineScope
) {
    
    /**
     * 스케줄링된 제출 동기화 실행
     * 
     * TDD Refactor 단계: runBlocking 제거 및 안전한 코루틴 스코프 사용
     * - 스케줄링 전용 코루틴 스코프를 사용하여 블로킹 방지
     * - fire-and-forget 방식으로 비동기 실행
     */
    @Scheduled(fixedRate = 300000) // 5분마다 실행
    fun scheduledSubmissionSync() {
        // 안전한 코루틴 스코프 사용 (runBlocking 대신)
        coroutineScope.launch {
            try {
                val result = executeSync()
                if (result.successful) {
                    // 성공 시 이벤트 발행
                    outboxService.publishEvent(
                        aggregateType = "SUBMISSION_SYNC",
                        aggregateId = "scheduled-sync-${System.currentTimeMillis()}",
                        eventType = "SCHEDULED_SUBMISSION_SYNC_COMPLETED",
                        eventData = mapOf(
                            "processedUsers" to result.processedUsers,
                            "newSubmissions" to result.newSubmissionsCount,
                            "executionTimeMs" to result.executionTimeMs
                        )
                    )
                }
            } catch (e: Exception) {
                // 실패 시 에러 이벤트 발행
                outboxService.publishEvent(
                    aggregateType = "SUBMISSION_SYNC",
                    aggregateId = "scheduled-sync-error-${System.currentTimeMillis()}",
                    eventType = "SCHEDULED_SUBMISSION_SYNC_FAILED",
                    eventData = mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }
    
    /**
     * 제출 동기화 실행
     * 
     * TDD Refactor 단계: 성능 최적화
     * - Kotlin Coroutines를 활용한 병렬 처리 (InitialDataSyncSaga 패턴 적용)
     * - 배치 단위 중복 체크로 메모리 효율성 개선
     * - 에러 처리 및 레이트 리밋 고려
     */
    suspend fun executeSync(): SyncResult = kotlinx.coroutines.coroutineScope {
        val startTime = System.currentTimeMillis()
        
        return@coroutineScope try {
            val activeUserIds = submissionSyncService.getActiveUserIds()
            
            // InitialDataSyncSaga 패턴: Kotlin Coroutines를 사용한 병렬 처리
            val userResults = activeUserIds.map { userId ->
                async {
                    processSingleUser(userId)
                }
            }.awaitAll()
            
            // 결과 집계 (메모리 효율적으로)
            val aggregatedResult = aggregateResults(userResults, activeUserIds.size, startTime)
            
            aggregatedResult
            
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            SyncResult(
                successful = false,
                processedUsers = 0,
                newSubmissionsCount = 0,
                duplicatesSkipped = 0,
                failedUsers = 1,
                executionTimeMs = executionTime,
                syncStatus = SyncStatus.FAILED,
                errorMessage = e.message
            )
        }
    }
    
    /**
     * 단일 사용자 제출 처리 (Refactor: 병렬 처리를 위한 분리)
     */
    private suspend fun processSingleUser(userId: UUID): UserSyncResult {
        return try {
            val handle = submissionSyncService.getUserHandle(userId)
            
            // solved.ac API 호출 (기본 페이지 1)
            val submissionList = solvedacApiClient.getSubmissions(handle, 1)
            
            // 배치 단위 중복 체크 (메모리 최적화)
            val submissionIds = submissionList.items.map { it.submissionId }
            val existingIds = submissionIds.filter { submissionRepository.existsBySubmissionId(it) }.toSet()
            
            var newSubmissionsCount = 0
            var duplicatesSkipped = 0
            
            // 새로운 제출만 처리
            for (dtoSubmission in submissionList.items) {
                if (!existingIds.contains(dtoSubmission.submissionId)) {
                    // DTO를 Entity로 변환 - solved.ac 핸들로 사용자 찾기
                    val user = userRepository.findBySolvedacHandle(dtoSubmission.user.handle)
                        ?: continue // 사용자를 찾을 수 없으면 스킵
                    
                    val entitySubmission = EntitySubmission(
                        submissionId = dtoSubmission.submissionId,
                        user = user,
                        problemId = dtoSubmission.problem.problemId.toString(),
                        problemTitle = dtoSubmission.problem.titleKo,
                        result = dtoSubmission.result,
                        language = dtoSubmission.language,
                        submittedAt = dtoSubmission.timestamp,
                        codeLength = dtoSubmission.codeLength,
                        runtime = dtoSubmission.runtime,
                        memory = dtoSubmission.memory
                    )
                    
                    submissionRepository.save(entitySubmission)
                    newSubmissionsCount++
                    
                    // 이벤트 발행 (배치로 처리하지 않고 개별 발행)
                    outboxService.publishEvent(
                        aggregateType = "SUBMISSION",
                        aggregateId = dtoSubmission.submissionId.toString(),
                        eventType = "SUBMISSION_PROCESSED",
                        eventData = mapOf("submissionId" to dtoSubmission.submissionId)
                    )
                } else {
                    duplicatesSkipped++
                }
            }
            
            // 마지막 동기화 시점 업데이트
            submissionSyncService.updateLastSyncTime(userId, LocalDateTime.now())
            
            UserSyncResult(
                userId = userId,
                successful = true,
                newSubmissionsCount = newSubmissionsCount,
                duplicatesSkipped = duplicatesSkipped
            )
            
        } catch (e: Exception) {
            // 에러 이벤트 발행
            outboxService.publishEvent(
                aggregateType = "SYNC_JOB",
                aggregateId = userId.toString(),
                eventType = "SUBMISSION_SYNC_ERROR",
                eventData = mapOf("error" to (e.message ?: "Unknown error"), "userId" to userId.toString())
            )
            
            UserSyncResult(
                userId = userId,
                successful = false,
                errorMessage = e.message
            )
        }
    }
    
    /**
     * 결과 집계 (메모리 효율성을 위한 분리)
     */
    private fun aggregateResults(
        userResults: List<UserSyncResult>,
        totalUsers: Int,
        startTime: Long
    ): SyncResult {
        val successfulResults = userResults.filter { it.successful }
        val failedResults = userResults.filter { !it.successful }
        
        val newSubmissionsCount = successfulResults.sumOf { it.newSubmissionsCount }
        val duplicatesSkipped = successfulResults.sumOf { it.duplicatesSkipped }
        val failedUsers = failedResults.size
        
        val executionTime = System.currentTimeMillis() - startTime
        val successful = failedUsers == 0
        val syncStatus = when {
            successful -> SyncStatus.COMPLETED
            failedUsers < totalUsers -> SyncStatus.PARTIAL_FAILURE
            else -> SyncStatus.FAILED
        }
        
        return SyncResult(
            successful = successful,
            processedUsers = totalUsers,
            newSubmissionsCount = newSubmissionsCount,
            duplicatesSkipped = duplicatesSkipped,
            failedUsers = failedUsers,
            executionTimeMs = executionTime,
            syncStatus = syncStatus,
            errorMessage = if (!successful) "일부 사용자 동기화 실패" else null
        )
    }
}