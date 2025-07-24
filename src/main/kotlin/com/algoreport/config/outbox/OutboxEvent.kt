package com.algoreport.config.outbox

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

/**
 * CDC 기반 Outbox Pattern을 구현하기 위한 이벤트 엔티티
 * 
 * SAGA 패턴에서 도메인 이벤트를 안전하게 발행하기 위해 사용됩니다.
 * 비즈니스 트랜잭션과 동일한 트랜잭션 내에서 이벤트를 저장하고,
 * Debezium CDC가 PostgreSQL WAL을 감지하여 자동으로 Kafka에 발행합니다.
 * 
 * ⚡ CDC 최적화:
 * - INSERT 시점에 즉시 Kafka 발행 (폴링 없음)
 * - processed 필드로 발행 상태 추적
 * - DELETE로 정리 작업 수행 (UPDATE 대신)
 */
@Entity
@Table(
    name = "OUTBOX_EVENTS",
    indexes = [
        Index(name = "idx_outbox_processed", columnList = "processed"),
        Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id"),
        Index(name = "idx_outbox_saga", columnList = "saga_id"),
        Index(name = "idx_outbox_created", columnList = "created_at"),
        // CDC 최적화: WAL 기반이므로 재시도 인덱스는 제거 가능
        Index(name = "idx_outbox_cleanup", columnList = "processed_at") // 정리 작업용
    ]
)
class OutboxEvent {
    
    @Id
    @Column(name = "event_id")
    var eventId: UUID = UUID.randomUUID()
    
    /**
     * 집합체 타입 (예: USER, STUDY_GROUP, ANALYSIS)
     */
    @Column(name = "aggregate_type", nullable = false, length = 50)
    var aggregateType: String = ""
    
    /**
     * 집합체 식별자
     */
    @Column(name = "aggregate_id", nullable = false, length = 100)
    var aggregateId: String = ""
    
    /**
     * 이벤트 타입 (예: USER_REGISTERED, GROUP_CREATED)
     */
    @Column(name = "event_type", nullable = false, length = 100)
    var eventType: String = ""
    
    /**
     * 이벤트 페이로드 (JSON 형태)
     */
    @Column(name = "event_data", nullable = false, columnDefinition = "TEXT")
    var eventData: String = ""
    
    /**
     * SAGA 식별자 (선택적)
     */
    @Column(name = "saga_id")
    var sagaId: UUID? = null
    
    /**
     * SAGA 타입 (선택적, 예: USER_REGISTRATION_SAGA)
     */
    @Column(name = "saga_type", length = 50)
    var sagaType: String? = null
    
    /**
     * 이벤트 생성 시각
     */
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
    
    /**
     * 처리 완료 여부
     */
    @Column(name = "processed", nullable = false)
    var processed: Boolean = false
    
    /**
     * 처리 완료 시각 (CDC에서 Kafka 발행 완료 후 설정)
     */
    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null
    
    /**
     * 이벤트 스키마 버전
     */
    @Column(name = "version", nullable = false)
    var version: Int = 1
    
    constructor()
    
    constructor(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        eventData: String,
        sagaId: UUID? = null,
        sagaType: String? = null,
        createdAt: LocalDateTime = LocalDateTime.now()
    ) {
        this.aggregateType = aggregateType
        this.aggregateId = aggregateId
        this.eventType = eventType
        this.eventData = eventData
        this.sagaId = sagaId
        this.sagaType = sagaType
        this.createdAt = createdAt
    }
    
    /**
     * 처리 완료로 마킹 (CDC에서 Kafka 발행 성공 후 호출)
     */
    fun markAsProcessed() {
        this.processed = true
        this.processedAt = LocalDateTime.now()
    }
    
    /**
     * 처리 완료 여부 확인
     */
    fun isProcessed(): Boolean {
        return processed
    }
    
    /**
     * 이벤트 생성 후 경과 시간 (분 단위)
     */
    fun getAgeInMinutes(): Long {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toMinutes()
    }
}