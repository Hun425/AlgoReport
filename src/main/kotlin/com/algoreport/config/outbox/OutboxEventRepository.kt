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
 * OutboxEvent 데이터 접근 레포지토리
 * 
 * Outbox Pattern의 핵심 데이터 접근 기능을 제공합니다:
 * - 미처리 이벤트 조회
 * - 재시도 대상 이벤트 조회
 * - 이벤트 상태 업데이트
 * - 집합체/SAGA별 이벤트 조회
 */
@Repository
interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID> {
    
    /**
     * 미처리 이벤트 조회 (처리되지 않았고 최대 재시도 횟수를 초과하지 않은 이벤트)
     * 
     * @param pageable 페이징 정보
     * @return 미처리 이벤트 목록
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.processed = false 
        AND e.retryCount < e.maxRetries
        AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= CURRENT_TIMESTAMP)
        ORDER BY e.createdAt ASC
    """)
    fun findUnprocessedEvents(pageable: Pageable): List<OutboxEvent>
    
    /**
     * 재시도 대상 이벤트 조회
     * 
     * @param currentTime 현재 시간
     * @param pageable 페이징 정보
     * @return 재시도 가능한 이벤트 목록
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.processed = false 
        AND e.retryCount > 0 
        AND e.retryCount < e.maxRetries
        AND e.nextRetryAt <= :currentTime
        ORDER BY e.nextRetryAt ASC
    """)
    fun findRetryableEvents(
        @Param("currentTime") currentTime: LocalDateTime,
        pageable: Pageable
    ): List<OutboxEvent>
    
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
     * 재시도 정보 업데이트
     * 
     * @param eventId 이벤트 ID
     * @param retryCount 재시도 횟수
     * @param nextRetryAt 다음 재시도 시각
     * @param errorMessage 오류 메시지
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE OutboxEvent e 
        SET e.retryCount = :retryCount, 
            e.nextRetryAt = :nextRetryAt, 
            e.errorMessage = :errorMessage
        WHERE e.eventId = :eventId
    """)
    fun updateRetryInfo(
        @Param("eventId") eventId: UUID,
        @Param("retryCount") retryCount: Int,
        @Param("nextRetryAt") nextRetryAt: LocalDateTime,
        @Param("errorMessage") errorMessage: String
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
     * 실패한 이벤트 삭제 (최대 재시도 횟수 초과)
     * 
     * @param cutoffDate 삭제 기준 날짜
     * @return 삭제된 이벤트 수
     */
    @Modifying
    @Transactional
    @Query("""
        DELETE FROM OutboxEvent e 
        WHERE e.retryCount >= e.maxRetries 
        AND e.createdAt < :cutoffDate
    """)
    fun deleteFailedEventsBefore(@Param("cutoffDate") cutoffDate: LocalDateTime): Int
    
    /**
     * 통계용: 미처리 이벤트 개수 조회
     */
    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.processed = false")
    fun countUnprocessedEvents(): Long
    
    /**
     * 통계용: 재시도 중인 이벤트 개수 조회
     */
    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.retryCount > 0 AND e.processed = false")
    fun countRetryingEvents(): Long
}