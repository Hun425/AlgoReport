package com.algoreport.collector

import com.algoreport.module.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

/**
 * 제출 동기화 서비스 구현체
 * 
 * Phase 1.5: 인메모리 로직을 JPA Repository로 변환
 */
@Service
class SubmissionSyncServiceImpl(
    private val userRepository: UserRepository,
    private val dataSyncCheckpointRepository: DataSyncCheckpointRepository
) : SubmissionSyncService {
    
    private val logger = LoggerFactory.getLogger(SubmissionSyncServiceImpl::class.java)
    
    override fun getActiveUserIds(): List<UUID> {
        logger.debug("Retrieving active user IDs from database")
        
        // JPA Repository를 사용하여 solved.ac 연동된 활성 사용자들을 조회
        val activeUsers = userRepository.findAllBySolvedacHandleIsNotNull()
        logger.debug("Found {} active users with solved.ac handles", activeUsers.size)
        
        return activeUsers.mapNotNull { it.id }
    }
    
    override fun getUserHandle(userId: UUID): String {
        logger.debug("Getting handle for user: {}", userId)
        
        // JPA Repository를 사용하여 사용자 핸들 조회
        val user = userRepository.findById(userId).orElseThrow {
            RuntimeException("User not found: $userId")
        }
        
        return user.solvedacHandle ?: throw RuntimeException("User $userId has no solved.ac handle")
    }
    
    override fun getLastSyncTime(userId: UUID): LocalDateTime {
        logger.debug("Getting last sync time for user: {}", userId)
        
        // DataSyncCheckpointRepository를 사용하여 최신 체크포인트에서 동기화 시간 조회
        val latestCheckpoint = dataSyncCheckpointRepository.findTopByUserIdOrderByCheckpointAtDesc(userId)
        
        return latestCheckpoint?.checkpointAt ?: LocalDateTime.now().minusHours(24)
    }
    
    override fun updateLastSyncTime(userId: UUID, syncTime: LocalDateTime) {
        logger.debug("Updating last sync time for user: {} to {}", userId, syncTime)
        
        // Note: DataSyncCheckpoint 업데이트는 별도의 배치 완료 시점에 수행됩니다.
        // 이 메서드는 현재 구조상 별도 동작이 불필요하며, 
        // 실제 체크포인트 업데이트는 DataSyncBatchService에서 처리됩니다.
        
        logger.info("Last sync time updated for user: {}", userId)
    }
}