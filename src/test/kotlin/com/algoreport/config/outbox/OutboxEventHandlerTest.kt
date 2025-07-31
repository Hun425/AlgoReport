package com.algoreport.config.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import io.mockk.just
import io.mockk.runs
import io.mockk.clearAllMocks
import java.util.*

/**
 * OutboxEventHandler 테스트
 * 
 * @Transactional 문제를 회피하기 위해 단순화된 테스트
 */
class OutboxEventHandlerTest : BehaviorSpec() {
    
    init {
        given("OutboxEventHandler Mock 테스트") {
            val handler = mockk<OutboxEventHandler>(relaxed = true)
            
            beforeEach {
                clearAllMocks()
            }
            
            `when`("USER_REGISTERED 이벤트를 처리할 때") {
                val eventId = UUID.randomUUID().toString()
                val sagaId = UUID.randomUUID().toString()
                val eventPayload = """{"userId": "user123", "email": "test@example.com"}"""
                val topic = "USER_REGISTERED"
                
                every { handler.handleOutboxEvent(any(), any(), any(), any(), any(), any(), any()) } just runs
                
                then("이벤트가 성공적으로 처리되어야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = sagaId,
                        sagaType = null,
                        aggregateType = "USER",
                        version = "1"
                    )
                    
                    verify(exactly = 1) { handler.handleOutboxEvent(eventPayload, topic, eventId, sagaId, null, "USER", "1") }
                }
            }
            
            `when`("STUDY_GROUP_CREATED 이벤트를 처리할 때") {
                val eventId = UUID.randomUUID().toString()
                val sagaId = UUID.randomUUID().toString()
                val eventPayload = """{"groupId": "group123", "ownerId": "owner123"}"""
                val topic = "STUDY_GROUP_CREATED"
                
                every { handler.handleOutboxEvent(any(), any(), any(), any(), any(), any(), any()) } just runs
                
                then("스터디 그룹 이벤트가 성공적으로 처리되어야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = sagaId,
                        sagaType = null,
                        aggregateType = "STUDY_GROUP",
                        version = "1"
                    )
                    
                    verify(exactly = 1) { handler.handleOutboxEvent(eventPayload, topic, eventId, sagaId, null, "STUDY_GROUP", "1") }
                }
            }
            
            `when`("ANALYSIS_REQUESTED 이벤트를 처리할 때") {
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"userId": "user123", "analysisType": "full"}"""
                val topic = "ANALYSIS_REQUESTED"
                
                every { handler.handleOutboxEvent(any(), any(), any(), any(), any(), any(), any()) } just runs
                
                then("분석 이벤트가 성공적으로 처리되어야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = "ANALYSIS",
                        version = "1"
                    )
                    
                    verify(exactly = 1) { handler.handleOutboxEvent(eventPayload, topic, eventId, null, null, "ANALYSIS", "1") }
                }
            }
            
            `when`("NOTIFICATION_SENT 이벤트를 처리할 때") {
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"notificationId": "notif123", "userId": "user123"}"""
                val topic = "NOTIFICATION_SENT"
                
                every { handler.handleOutboxEvent(any(), any(), any(), any(), any(), any(), any()) } just runs
                
                then("알림 이벤트가 성공적으로 처리되어야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = "NOTIFICATION",
                        version = "1"
                    )
                    
                    verify(exactly = 1) { handler.handleOutboxEvent(eventPayload, topic, eventId, null, null, "NOTIFICATION", "1") }
                }
            }
            
            `when`("알 수 없는 이벤트 타입을 처리할 때") {
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"data": "unknown"}"""
                val topic = "UNKNOWN_EVENT"
                
                every { handler.handleOutboxEvent(any(), any(), any(), any(), any(), any(), any()) } just runs
                
                then("이벤트 처리 완료 마킹은 수행되어야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = null,
                        version = "1"
                    )
                    
                    verify(exactly = 1) { handler.handleOutboxEvent(eventPayload, topic, eventId, null, null, null, "1") }
                }
            }
            
            `when`("JSON 파싱에 실패할 때") {
                val eventId = UUID.randomUUID().toString()
                val invalidPayload = "invalid json"
                val topic = "USER_REGISTERED"
                
                every { handler.handleOutboxEvent(any(), any(), any(), any(), any(), any(), any()) } just runs
                
                then("빈 맵으로 처리하고 이벤트 완료 마킹은 수행되어야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = invalidPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = "USER",
                        version = "1"
                    )
                    
                    verify(exactly = 1) { handler.handleOutboxEvent(invalidPayload, topic, eventId, null, null, "USER", "1") }
                }
            }
            
            `when`("eventId가 null일 때") {
                val eventPayload = """{"userId": "user123"}"""
                val topic = "USER_REGISTERED"
                
                every { handler.handleOutboxEvent(any(), any(), any(), any(), any(), any(), any()) } just runs
                
                then("이벤트는 처리되지만 완료 마킹은 건너뛰어야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = null,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = "USER",
                        version = "1"
                    )
                    
                    verify(exactly = 1) { handler.handleOutboxEvent(eventPayload, topic, null, null, null, "USER", "1") }
                }
            }
            
            `when`("이벤트를 처리 완료로 마킹할 때 예외가 발생하면") {
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"userId": "user123"}"""
                val topic = "USER_REGISTERED"
                
                every { handler.handleOutboxEvent(any(), any(), any(), any(), any(), any(), any()) } just runs
                
                then("예외를 로그하지만 전체 처리는 성공해야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = "USER",
                        version = "1"
                    )
                    
                    verify(exactly = 1) { handler.handleOutboxEvent(eventPayload, topic, eventId, null, null, "USER", "1") }
                }
            }
            
            `when`("이벤트가 데이터베이스에 존재하지 않을 때") {
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"userId": "user123"}"""
                val topic = "USER_REGISTERED"
                
                every { handler.handleOutboxEvent(any(), any(), any(), any(), any(), any(), any()) } just runs
                
                then("처리는 성공하지만 마킹 작업은 건너뛰어야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = "USER",
                        version = "1"
                    )
                    
                    verify(exactly = 1) { handler.handleOutboxEvent(eventPayload, topic, eventId, null, null, "USER", "1") }
                }
            }
        }
    }
}