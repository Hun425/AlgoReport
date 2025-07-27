package com.algoreport.collector

import java.time.LocalDateTime
import java.util.*

/**
 * 제출 동기화 서비스 인터페이스
 */
interface SubmissionSyncService {
    /**
     * 활성 사용자 ID 목록을 조회한다.
     */
    fun getActiveUserIds(): List<UUID>
    
    /**
     * 사용자 ID에 해당하는 solved.ac 핸들을 조회한다.
     */
    fun getUserHandle(userId: UUID): String
    
    /**
     * 마지막 동기화 시점을 조회한다.
     */
    fun getLastSyncTime(userId: UUID): LocalDateTime
    
    /**
     * 마지막 동기화 시점을 업데이트한다.
     */
    fun updateLastSyncTime(userId: UUID, syncTime: LocalDateTime)
}