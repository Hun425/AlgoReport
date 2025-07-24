package com.algoreport.collector

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 체크포인트 리포지토리 구현체 (In-Memory)
 * 
 * TDD Green 단계: 테스트를 통과하기 위한 최소한의 구현
 * 실제 프로덕션에서는 JPA Entity와 DB 연동이 필요
 */
@Repository
class DataSyncCheckpointRepositoryImpl : DataSyncCheckpointRepository {
    
    private val logger = LoggerFactory.getLogger(DataSyncCheckpointRepositoryImpl::class.java)
    
    // In-Memory 저장소 (테스트용)
    private val checkpoints = ConcurrentHashMap<UUID, DataSyncCheckpoint>()
    private val userCheckpoints = ConcurrentHashMap<UUID, MutableList<DataSyncCheckpoint>>()
    
    override fun save(checkpoint: DataSyncCheckpoint): DataSyncCheckpoint {
        logger.debug("Saving checkpoint for syncJobId: {}", checkpoint.syncJobId)
        
        checkpoints[checkpoint.syncJobId] = checkpoint
        
        // 사용자별 체크포인트 목록 업데이트
        userCheckpoints.computeIfAbsent(checkpoint.userId) { mutableListOf() }
            .add(checkpoint)
        
        return checkpoint
    }
    
    override fun findBySyncJobId(syncJobId: UUID): DataSyncCheckpoint? {
        return checkpoints[syncJobId]
    }
    
    override fun findLatestByUserId(userId: UUID): DataSyncCheckpoint? {
        return userCheckpoints[userId]
            ?.maxByOrNull { it.checkpointAt }
    }
}