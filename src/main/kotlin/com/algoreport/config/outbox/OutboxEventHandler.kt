package com.algoreport.config.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.util.*

/**
 * CDC 기반 Outbox 이벤트 처리 핸들러
 * 
 * Debezium이 WAL에서 감지한 Outbox 이벤트를 처리합니다.
 * 각 이벤트 타입별로 적절한 토픽으로 라우팅하고 처리 완료를 마킹합니다.
 */
@Component
class OutboxEventHandler(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper
) {
    
    private val logger = LoggerFactory.getLogger(OutboxEventHandler::class.java)
    
    /**
     * CDC에서 발행된 Outbox 이벤트 수신 및 후처리
     * 
     * Debezium Outbox Event Router에 의해 이벤트 타입별 토픽으로 라우팅된 메시지를 수신합니다.
     * 
     * @param eventPayload 이벤트 페이로드 (JSON)
     * @param eventId 이벤트 ID (헤더)
     * @param sagaId SAGA ID (헤더, 선택적)
     * @param sagaType SAGA 타입 (헤더, 선택적)
     * @param aggregateType 집합체 타입 (헤더)
     * @param topic 토픽명 (이벤트 타입과 동일)
     */
    @KafkaListener(
        topicPattern = "USER_.*|STUDY_GROUP_.*|ANALYSIS_.*|NOTIFICATION_.*",
        groupId = "algoreport-outbox-handler",
        concurrency = "3"
    )
    fun handleOutboxEvent(
        @Payload eventPayload: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header("eventId", required = false) eventId: String?,
        @Header("sagaId", required = false) sagaId: String?,
        @Header("sagaType", required = false) sagaType: String?,
        @Header("aggregateType", required = false) aggregateType: String?,
        @Header("version", required = false) version: String?
    ) {
        logger.debug("Processing outbox event: topic={}, eventId={}, sagaId={}", topic, eventId, sagaId)

        val eventData = parseEventPayload(eventPayload)

        try {
            processBusinessLogic(topic, eventData, sagaId, aggregateType)
        } catch (ex: Exception) {
            logger.error("Error processing business logic for event: topic={}, eventId={}", topic, eventId, ex)
        }

        logger.info("Successfully processed outbox event: topic={}, eventId={}", topic, eventId)

        // Outbox 테이블에서 처리 완료 마킹 (실패해도 전체 처리에는 영향 없음)
        eventId?.let { id ->
            try {
                markEventAsProcessed(UUID.fromString(id))
            } catch (ex: Exception) {
                logger.warn("Failed to process event ID: {}", id, ex)
            }
        }
    }
    
    /**
     * 이벤트 페이로드 파싱
     */
    private fun parseEventPayload(payload: String): Map<String, Any> {
        return try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(payload, Map::class.java) as Map<String, Any>
        } catch (ex: Exception) {
            logger.warn("Failed to parse event payload, using empty map: {}", payload, ex)
            emptyMap()
        }
    }
    
    /**
     * 이벤트 타입별 비즈니스 로직 처리
     */
    private fun processBusinessLogic(
        eventType: String,
        eventData: Map<String, Any>,
        sagaId: String?,
        aggregateType: String?
    ) {
        when {
            eventType.startsWith("USER_") -> processUserEvent(eventType, eventData, sagaId)
            eventType.startsWith("STUDY_GROUP_") -> processStudyGroupEvent(eventType, eventData, sagaId)
            eventType.startsWith("ANALYSIS_") -> processAnalysisEvent(eventType, eventData, sagaId)
            eventType.startsWith("NOTIFICATION_") -> processNotificationEvent(eventType, eventData, sagaId)
            else -> logger.warn("Unknown event type: {}", eventType)
        }
    }
    
    /**
     * 사용자 관련 이벤트 처리
     */
    private fun processUserEvent(eventType: String, eventData: Map<String, Any>, sagaId: String?) {
        logger.debug("Processing user event: type={}, sagaId={}", eventType, sagaId)
        
        when (eventType) {
            "USER_REGISTERED" -> {
                val userId = eventData["userId"] as? String
                logger.info("User registered: userId={}, sagaId={}", userId, sagaId)
                // 추가 처리 로직 (예: 웰컴 이메일, 프로필 초기화 등)
            }
            "USER_PROFILE_UPDATED" -> {
                val userId = eventData["userId"] as? String
                logger.info("User profile updated: userId={}", userId)
                // 캐시 무효화, 검색 인덱스 업데이트 등
            }
            // 다른 사용자 이벤트들...
        }
    }
    
    /**
     * 스터디 그룹 관련 이벤트 처리
     */
    private fun processStudyGroupEvent(eventType: String, eventData: Map<String, Any>, sagaId: String?) {
        logger.debug("Processing study group event: type={}, sagaId={}", eventType, sagaId)
        
        when (eventType) {
            "STUDY_GROUP_CREATED" -> {
                val groupId = eventData["groupId"] as? String
                val ownerId = eventData["ownerId"] as? String
                logger.info("Study group created: groupId={}, ownerId={}, sagaId={}", groupId, ownerId, sagaId)
            }
            "MEMBER_JOINED" -> {
                val groupId = eventData["groupId"] as? String
                val userId = eventData["userId"] as? String
                logger.info("Member joined group: groupId={}, userId={}", groupId, userId)
            }
            // 다른 그룹 이벤트들...
        }
    }
    
    /**
     * 분석 관련 이벤트 처리
     */
    private fun processAnalysisEvent(eventType: String, eventData: Map<String, Any>, sagaId: String?) {
        logger.debug("Processing analysis event: type={}, sagaId={}", eventType, sagaId)
        
        when (eventType) {
            "ANALYSIS_REQUESTED" -> {
                val userId = eventData["userId"] as? String
                logger.info("Analysis requested: userId={}", userId)
            }
            "ANALYSIS_COMPLETED" -> {
                val analysisId = eventData["analysisId"] as? String
                logger.info("Analysis completed: analysisId={}", analysisId)
            }
            // 다른 분석 이벤트들...
        }
    }
    
    /**
     * 알림 관련 이벤트 처리
     */
    private fun processNotificationEvent(eventType: String, eventData: Map<String, Any>, sagaId: String?) {
        logger.debug("Processing notification event: type={}, sagaId={}", eventType, sagaId)
        
        when (eventType) {
            "NOTIFICATION_SENT" -> {
                val notificationId = eventData["notificationId"] as? String
                val userId = eventData["userId"] as? String
                logger.info("Notification sent: id={}, userId={}", notificationId, userId)
            }
            // 다른 알림 이벤트들...
        }
    }
    
    /**
     * Outbox 테이블에서 이벤트를 처리 완료로 마킹
     */
    private fun markEventAsProcessed(eventId: UUID) {
        try {
            outboxEventRepository.findById(eventId).ifPresent { event ->
                event.markAsProcessed()
                outboxEventRepository.save(event)
                logger.debug("Marked outbox event as processed: {}", eventId)
            }
        } catch (ex: Exception) {
            logger.warn("Failed to mark outbox event as processed: {}", eventId, ex)
            // 처리 완료 마킹 실패는 치명적이지 않으므로 예외를 삼킴
        }
    }
}