package com.algoreport.config.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Outbox Pattern 비즈니스 서비스
 * 
 * 도메인 서비스에서 이벤트 발행을 위해 사용하는 서비스입니다.
 * 비즈니스 로직에서 직접 Kafka에 발행하는 대신, 
 * 이 서비스를 통해 이벤트를 Outbox 테이블에 저장합니다.
 */
@Service
@Transactional
class OutboxService(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper = ObjectMapper()
) {
    
    /**
     * 기본 이벤트 발행
     * 
     * @param aggregateType 집합체 타입 (예: USER, STUDY_GROUP)
     * @param aggregateId 집합체 식별자
     * @param eventType 이벤트 타입 (예: USER_REGISTERED)
     * @param eventData 이벤트 데이터 (Map 형태)
     * @return 생성된 이벤트 ID
     */
    fun publishEvent(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        eventData: Map<String, Any>
    ): UUID {
        val outboxEvent = OutboxEvent(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            eventData = convertToJson(eventData)
        )
        
        val savedEvent = outboxEventRepository.save(outboxEvent)
        return savedEvent.eventId
    }
    
    /**
     * SAGA와 함께 이벤트 발행
     * 
     * @param aggregateType 집합체 타입
     * @param aggregateId 집합체 식별자
     * @param eventType 이벤트 타입
     * @param eventData 이벤트 데이터
     * @param sagaId SAGA 식별자
     * @param sagaType SAGA 타입
     * @return 생성된 이벤트 ID
     */
    fun publishEventWithSaga(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        eventData: Map<String, Any>,
        sagaId: UUID,
        sagaType: String
    ): UUID {
        val outboxEvent = OutboxEvent(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            eventData = convertToJson(eventData),
            sagaId = sagaId,
            sagaType = sagaType
        )
        
        val savedEvent = outboxEventRepository.save(outboxEvent)
        return savedEvent.eventId
    }
    
    /**
     * 특정 집합체의 이벤트 조회
     * 
     * @param aggregateType 집합체 타입
     * @param aggregateId 집합체 식별자
     * @return 해당 집합체의 이벤트 목록
     */
    @Transactional(readOnly = true)
    fun getEventsByAggregate(aggregateType: String, aggregateId: String): List<OutboxEvent> {
        return outboxEventRepository.findByAggregateTypeAndAggregateId(aggregateType, aggregateId)
    }
    
    /**
     * 특정 SAGA의 이벤트 조회
     * 
     * @param sagaId SAGA 식별자
     * @return 해당 SAGA의 이벤트 목록
     */
    @Transactional(readOnly = true)
    fun getEventsBySaga(sagaId: UUID): List<OutboxEvent> {
        return outboxEventRepository.findBySagaId(sagaId)
    }
    
    /**
     * 특정 SAGA의 특정 타입 이벤트 조회
     * 
     * @param sagaId SAGA 식별자
     * @param eventType 이벤트 타입
     * @return 해당 조건의 이벤트 목록
     */
    @Transactional(readOnly = true)
    fun getEventsBySagaAndType(sagaId: UUID, eventType: String): List<OutboxEvent> {
        return outboxEventRepository.findBySagaIdAndEventType(sagaId, eventType)
    }
    
    /**
     * Map 데이터를 JSON 문자열로 변환
     * 
     * @param eventData 변환할 데이터
     * @return JSON 문자열
     */
    fun convertToJson(eventData: Map<String, Any>): String {
        return try {
            objectMapper.writeValueAsString(eventData)
        } catch (e: Exception) {
            // 변환 실패 시 빈 JSON 객체 반환
            "{}"
        }
    }
    
    /**
     * JSON 문자열을 Map으로 변환
     * 
     * @param jsonString JSON 문자열
     * @return Map 데이터
     */
    fun convertFromJson(jsonString: String): Map<String, Any> {
        return try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(jsonString, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            // 변환 실패 시 빈 Map 반환
            emptyMap()
        }
    }
    
    /**
     * 이벤트 통계 조회
     * 
     * @return 이벤트 통계 정보
     */
    @Transactional(readOnly = true)
    fun getEventStatistics(): EventStatistics {
        val unprocessedCount = outboxEventRepository.countUnprocessedEvents()
        val retryingCount = 0L // CDC 방식에서는 재시도 없음
        val totalCount = outboxEventRepository.count()
        
        return EventStatistics(
            totalEvents = totalCount,
            unprocessedEvents = unprocessedCount,
            retryingEvents = retryingCount,
            processedEvents = totalCount - unprocessedCount
        )
    }
    
    /**
     * 이벤트 통계 데이터 클래스
     */
    data class EventStatistics(
        val totalEvents: Long,
        val unprocessedEvents: Long,
        val retryingEvents: Long,
        val processedEvents: Long
    )
}