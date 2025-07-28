package com.algoreport.collector

/**
 * 동기화 상태
 */
enum class SyncStatus {
    STARTED,
    COMPLETED,
    PARTIAL_FAILURE,
    FAILED
}

/**
 * 동기화 결과
 */
data class SyncResult(
    val successful: Boolean,
    val processedUsers: Int,
    val newSubmissionsCount: Int,
    val duplicatesSkipped: Int = 0,
    val failedUsers: Int = 0,
    val executionTimeMs: Long,
    val syncStatus: SyncStatus,
    val errorMessage: String? = null
)

/**
 * 개별 사용자 동기화 결과 (Refactor 단계에서 추가)
 */
data class UserSyncResult(
    val userId: java.util.UUID,
    val successful: Boolean,
    val newSubmissionsCount: Int = 0,
    val duplicatesSkipped: Int = 0,
    val errorMessage: String? = null
)