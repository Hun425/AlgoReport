package com.algoreport.config.outbox

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

/**
 * Outbox Pattern을 구현하기 위한 이벤트 엔티티
 * 
 * SAGA 패턴에서 도메인 이벤트를 안전하게 발행하기 위해 사용됩니다.
 * 비즈니스 트랜잭션과 동일한 트랜잭션 내에서 이벤트를 저장하고,
 * 별도의 프로세스에서 이벤트를 Kafka로 발행합니다.
 */
@Entity
@Table(
    name = "OUTBOX_EVENTS",
    indexes = [
        Index(name = "idx_outbox_processed", columnList = "processed"),
        Index(name = "idx_outbox_retry", columnList = "retryCount, nextRetryAt"),
        Index(name = "idx_outbox_aggregate", columnList = "aggregateType, aggregateId"),
        Index(name = "idx_outbox_saga", columnList = "sagaId"),
        Index(name = "idx_outbox_created", columnList = "createdAt")
    ]
)
data class OutboxEvent(
    
    @Id
    @Column(name = "event_id")
    val eventId: UUID = UUID.randomUUID(),
    
    /**
     * 집합체 타입 (예: USER, STUDY_GROUP, ANALYSIS)
     */
    @Column(name = "aggregate_type", nullable = false, length = 50)
    val aggregateType: String,
    
    /**
     * 집합체 식별자
     */
    @Column(name = "aggregate_id", nullable = false, length = 100)
    val aggregateId: String,
    
    /**
     * 이벤트 타입 (예: USER_REGISTERED, GROUP_CREATED)
     */
    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,
    
    /**
     * 이벤트 페이로드 (JSON 형태)
     */
    @Column(name = "event_data", nullable = false, columnDefinition = "TEXT")
    val eventData: String,
    
    /**
     * SAGA 식별자 (선택적)
     */
    @Column(name = "saga_id")
    val sagaId: UUID? = null,
    
    /**
     * SAGA 타입 (선택적, 예: USER_REGISTRATION_SAGA)
     */
    @Column(name = "saga_type", length = 50)
    val sagaType: String? = null,
    
    /**
     * 이벤트 생성 시각
     */
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    /**
     * 처리 완료 여부
     */
    @Column(name = "processed", nullable = false)
    var processed: Boolean = false,
    
    /**
     * 처리 완료 시각
     */
    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null,
    
    /**
     * 재시도 횟수
     */
    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,
    
    /**
     * 최대 재시도 횟수
     */
    @Column(name = "max_retries", nullable = false)
    val maxRetries: Int = 3,
    
    /**
     * 다음 재시도 시각
     */
    @Column(name = "next_retry_at")
    var nextRetryAt: LocalDateTime? = null,
    
    /**
     * 오류 메시지
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,
    
    /**
     * 이벤트 스키마 버전
     */
    @Column(name = "version", nullable = false)
    val version: Int = 1
) {
    
    /**
     * 재시도 가능 여부 확인
     */
    fun isRetryable(): Boolean {
        return !processed && retryCount < maxRetries
    }
    
    /**
     * 재시도 시간이 되었는지 확인
     */
    fun isRetryTimeReached(now: LocalDateTime): Boolean {
        return nextRetryAt?.let { it.isBefore(now) || it.isEqual(now) } ?: true
    }
    
    /**
     * 최대 재시도 횟수에 도달했는지 확인
     */
    fun hasReachedMaxRetries(): Boolean {
        return retryCount >= maxRetries
    }
}