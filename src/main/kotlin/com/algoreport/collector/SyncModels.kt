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