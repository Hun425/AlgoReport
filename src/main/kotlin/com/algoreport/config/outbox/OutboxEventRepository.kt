package com.algoreport.config.outbox

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * CDC 기반 OutboxEvent 데이터 접근 레포지토리
 * 
 * CDC 방식에서는 INSERT 시점에 즉시 Kafka로 발행되므로:
 * - 폴링 관련 쿼리 제거
 * - 재시도 로직 제거 (Kafka Consumer 재시도 활용)
 * - 조회 및 정리 작업에 집중
 */
@Repository
interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID> {
    
    /**
     * 처리되지 않은 이벤트 조회 (모니터링용)
     * CDC에서는 INSERT 즉시 발행되므로 미처리 이벤트는 거의 없어야 함
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.processed = false ORDER BY e.createdAt ASC")
    fun findUnprocessedEvents(pageable: Pageable): List<OutboxEvent>
    
    /**
     * 특정 집합체의 모든 이벤트 조회
     * 
     * @param aggregateType 집합체 타입
     * @param aggregateId 집합체 ID
     * @return 해당 집합체의 이벤트 목록
     */
    fun findByAggregateTypeAndAggregateId(
        aggregateType: String, 
        aggregateId: String
    ): List<OutboxEvent>
    
    /**
     * 특정 SAGA의 모든 이벤트 조회
     * 
     * @param sagaId SAGA ID
     * @return 해당 SAGA의 이벤트 목록
     */
    fun findBySagaId(sagaId: UUID): List<OutboxEvent>
    
    /**
     * 특정 SAGA와 이벤트 타입으로 이벤트 조회
     * 
     * @param sagaId SAGA ID
     * @param eventType 이벤트 타입
     * @return 해당 조건의 이벤트 목록
     */
    fun findBySagaIdAndEventType(sagaId: UUID, eventType: String): List<OutboxEvent>
    
    /**
     * 처리 완료로 표시
     * 
     * @param eventId 이벤트 ID
     * @param processedAt 처리 완료 시각
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE OutboxEvent e 
        SET e.processed = true, e.processedAt = :processedAt 
        WHERE e.eventId = :eventId
    """)
    fun markAsProcessed(
        @Param("eventId") eventId: UUID,
        @Param("processedAt") processedAt: LocalDateTime
    )
    
    
    /**
     * 오래된 처리 완료 이벤트 삭제 (정리 작업용)
     * 
     * @param cutoffDate 삭제 기준 날짜
     * @return 삭제된 이벤트 수
     */
    @Modifying
    @Transactional
    @Query("""
        DELETE FROM OutboxEvent e 
        WHERE e.processed = true 
        AND e.processedAt < :cutoffDate
    """)
    fun deleteProcessedEventsBefore(@Param("cutoffDate") cutoffDate: LocalDateTime): Int
    
    /**
     * 오래된 미처리 이벤트 삭제 (CDC 문제로 발행되지 않은 이벤트)
     * 
     * @param cutoffDate 삭제 기준 날짜
     * @return 삭제된 이벤트 수
     */
    @Modifying
    @Transactional
    @Query("""
        DELETE FROM OutboxEvent e 
        WHERE e.processed = false 
        AND e.createdAt < :cutoffDate
    """)
    fun deleteStaleUnprocessedEventsBefore(@Param("cutoffDate") cutoffDate: LocalDateTime): Int
    
    /**
     * 통계용: 미처리 이벤트 개수 조회
     */
    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.processed = false")
    fun countUnprocessedEvents(): Long
    
    /**
     * 통계용: 처리 완료된 이벤트 개수 조회  
     */
    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.processed = true")
    fun countProcessedEvents(): Long
    
    /**
     * 통계용: 특정 시간 이후 생성된 이벤트 개수
     */
    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.createdAt >= :since")
    fun countEventsCreatedSince(@Param("since") since: LocalDateTime): Long
}