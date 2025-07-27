package com.algoreport.collector

import com.algoreport.config.outbox.OutboxService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

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
    private val submissionRepository: SubmissionRepository
) {
    
    /**
     * 스케줄링된 제출 동기화 실행
     */
    @Scheduled(fixedRate = 300000) // 5분마다 실행
    fun scheduledSubmissionSync() {
        // 스케줄링 구현은 GREEN 단계에서
    }
    
    /**
     * 제출 동기화 실행
     */
    suspend fun executeSync(): SyncResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val activeUserIds = submissionSyncService.getActiveUserIds()
            var newSubmissionsCount = 0
            var duplicatesSkipped = 0
            var failedUsers = 0
            
            for (userId in activeUserIds) {
                try {
                    val handle = submissionSyncService.getUserHandle(userId)
                    val lastSyncTime = submissionSyncService.getLastSyncTime(userId)
                    
                    // solved.ac API 호출 (기본 페이지 1)
                    val submissionList = solvedacApiClient.getSubmissions(handle, 1)
                    
                    // 새로운 제출만 처리
                    for (submission in submissionList.items) {
                        if (!submissionRepository.existsBySubmissionId(submission.submissionId)) {
                            submissionRepository.save(submission)
                            newSubmissionsCount++
                            
                            // 이벤트 발행
                            outboxService.publishEvent(
                                aggregateType = "SUBMISSION",
                                aggregateId = submission.submissionId.toString(),
                                eventType = "SUBMISSION_PROCESSED",
                                eventData = mapOf("submissionId" to submission.submissionId)
                            )
                        } else {
                            duplicatesSkipped++
                        }
                    }
                    
                    // 마지막 동기화 시점 업데이트
                    submissionSyncService.updateLastSyncTime(userId, LocalDateTime.now())
                    
                } catch (e: Exception) {
                    failedUsers++
                    
                    // 에러 이벤트 발행
                    outboxService.publishEvent(
                        aggregateType = "SYNC_JOB",
                        aggregateId = userId.toString(),
                        eventType = "SUBMISSION_SYNC_ERROR",
                        eventData = mapOf("error" to (e.message ?: "Unknown error"), "userId" to userId.toString())
                    )
                }
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            val successful = failedUsers == 0
            val syncStatus = when {
                successful -> SyncStatus.COMPLETED
                failedUsers < activeUserIds.size -> SyncStatus.PARTIAL_FAILURE
                else -> SyncStatus.FAILED
            }
            
            SyncResult(
                successful = successful,
                processedUsers = activeUserIds.size,
                newSubmissionsCount = newSubmissionsCount,
                duplicatesSkipped = duplicatesSkipped,
                failedUsers = failedUsers,
                executionTimeMs = executionTime,
                syncStatus = syncStatus,
                errorMessage = if (!successful) "일부 사용자 동기화 실패" else null
            )
            
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
}