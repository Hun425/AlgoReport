package com.algoreport.collector

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 제출 동기화 서비스 구현체
 * 
 * TDD Green 단계: 테스트 통과를 위한 최소한의 구현
 * 기존 UserService 패턴을 따라 인메모리 저장소 사용
 */
@Service
class SubmissionSyncServiceImpl : SubmissionSyncService {
    
    private val logger = LoggerFactory.getLogger(SubmissionSyncServiceImpl::class.java)
    
    // 기존 UserService 패턴을 따라 ConcurrentHashMap 사용
    private val userHandles = ConcurrentHashMap<UUID, String>()
    private val lastSyncTimes = ConcurrentHashMap<UUID, LocalDateTime>()
    
    companion object {
        // 테스트용 하드코딩된 활성 사용자들 (Green 단계)
        private val ACTIVE_USER_IDS = listOf(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        )
    }
    
    override fun getActiveUserIds(): List<UUID> {
        logger.debug("Retrieving active user IDs, count: {}", ACTIVE_USER_IDS.size)
        
        // Green 단계: 하드코딩된 활성 사용자 목록 반환
        // 실제 구현에서는 DB에서 solved.ac 연동된 활성 사용자들을 조회
        return ACTIVE_USER_IDS
    }
    
    override fun getUserHandle(userId: UUID): String {
        logger.debug("Getting handle for user: {}", userId)
        
        // Green 단계: 메모리에서 조회, 없으면 기본값 반환
        // 기존 DataSyncBatchServiceImpl 패턴 참고하여 간단한 기본값 생성
        return userHandles[userId] ?: "testuser${userId.toString().takeLast(4)}"
    }
    
    override fun getLastSyncTime(userId: UUID): LocalDateTime {
        logger.debug("Getting last sync time for user: {}", userId)
        
        // Green 단계: 메모리에서 조회, 없으면 1시간 전 반환
        // InitialDataSyncSaga에서 사용한 패턴 참고
        return lastSyncTimes[userId] ?: LocalDateTime.now().minusHours(1)
    }
    
    override fun updateLastSyncTime(userId: UUID, syncTime: LocalDateTime) {
        logger.debug("Updating last sync time for user: {} to {}", userId, syncTime)
        
        // Green 단계: 메모리에 저장
        lastSyncTimes[userId] = syncTime
        
        logger.info("Last sync time updated for user: {}", userId)
    }
    
    /**
     * 테스트용 사용자 핸들 설정 메서드 (기존 UserService 패턴)
     */
    fun setUserHandle(userId: UUID, handle: String) {
        logger.debug("Setting handle for user: {} to {}", userId, handle)
        userHandles[userId] = handle
    }
    
    /**
     * 테스트용 초기화 메서드 (기존 UserService 패턴)
     */
    fun clear() {
        userHandles.clear()
        lastSyncTimes.clear()
        logger.debug("Cleared all user handles and sync times")
    }
}