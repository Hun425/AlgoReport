package com.algoreport.config.outbox

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.context.ActiveProfiles
import java.util.*

/**
 * OutboxService 테스트
 * TDD Red 단계: OutboxService 클래스가 존재하지 않으므로 컴파일 실패 예상
 */
@ActiveProfiles("test")
class OutboxServiceTest : BehaviorSpec({
    
    val outboxEventRepository = mockk<OutboxEventRepository>()
    val outboxService = OutboxService(outboxEventRepository)
    
    given("OutboxService가 이벤트를 발행할 때") {
        
        `when`("기본 이벤트 정보로 발행하면") {
            val eventId = UUID.randomUUID()
            val mockEvent = OutboxEvent(
                eventId = eventId,
                aggregateType = "USER",
                aggregateId = "user-123",
                eventType = "USER_REGISTERED",
                eventData = """{"userId": "user-123", "email": "test@example.com"}"""
            )
            
            every { outboxEventRepository.save(any<OutboxEvent>()) } returns mockEvent
            
            val result = outboxService.publishEvent(
                aggregateType = "USER",
                aggregateId = "user-123",
                eventType = "USER_REGISTERED",
                eventData = mapOf(
                    "userId" to "user-123",
                    "email" to "test@example.com"
                )
            )
            
            then("이벤트가 저장되고 ID가 반환되어야 한다") {
                result shouldBe eventId
                
                verify {
                    outboxEventRepository.save(
                        match<OutboxEvent> { event ->
                            event.aggregateType == "USER" &&
                            event.aggregateId == "user-123" &&
                            event.eventType == "USER_REGISTERED" &&
                            event.eventData.contains("user-123") &&
                            event.processed == false &&
                            event.retryCount == 0
                        }
                    )
                }
            }
        }
        
        `when`("SAGA 정보와 함께 이벤트를 발행하면") {
            val sagaId = UUID.randomUUID()
            val eventId = UUID.randomUUID()
            val mockEvent = OutboxEvent(
                eventId = eventId,
                aggregateType = "STUDY_GROUP",
                aggregateId = "group-456",
                eventType = "GROUP_CREATED",
                eventData = """{"groupId": "group-456", "ownerId": "user-123"}""",
                sagaId = sagaId,
                sagaType = "CREATE_GROUP_SAGA"
            )
            
            every { outboxEventRepository.save(any<OutboxEvent>()) } returns mockEvent
            
            val result = outboxService.publishEventWithSaga(
                aggregateType = "STUDY_GROUP",
                aggregateId = "group-456", 
                eventType = "GROUP_CREATED",
                eventData = mapOf(
                    "groupId" to "group-456",
                    "ownerId" to "user-123"
                ),
                sagaId = sagaId,
                sagaType = "CREATE_GROUP_SAGA"
            )
            
            then("SAGA 정보가 포함된 이벤트가 저장되어야 한다") {
                result shouldBe eventId
                
                verify {
                    outboxEventRepository.save(
                        match<OutboxEvent> { event ->
                            event.aggregateType == "STUDY_GROUP" &&
                            event.aggregateId == "group-456" &&
                            event.eventType == "GROUP_CREATED" &&
                            event.sagaId == sagaId &&
                            event.sagaType == "CREATE_GROUP_SAGA"
                        }
                    )
                }
            }
        }
    }
    
    given("OutboxService가 이벤트 상태를 조회할 때") {
        
        `when`("특정 집합체의 이벤트를 조회하면") {
            val mockEvents = listOf(
                OutboxEvent(
                    aggregateType = "USER",
                    aggregateId = "user-789",
                    eventType = "USER_REGISTERED", 
                    eventData = """{"userId": "user-789"}"""
                ),
                OutboxEvent(
                    aggregateType = "USER",
                    aggregateId = "user-789",
                    eventType = "USER_PROFILE_UPDATED",
                    eventData = """{"userId": "user-789"}"""
                )
            )
            
            every { 
                outboxEventRepository.findByAggregateTypeAndAggregateId("USER", "user-789")
            } returns mockEvents
            
            val result = outboxService.getEventsByAggregate("USER", "user-789")
            
            then("해당 집합체의 모든 이벤트가 반환되어야 한다") {
                result.size shouldBe 2
                result.forEach { event ->
                    event.aggregateType shouldBe "USER"
                    event.aggregateId shouldBe "user-789"
                }
            }
        }
        
        `when`("특정 SAGA의 이벤트를 조회하면") {
            val sagaId = UUID.randomUUID()
            val mockEvents = listOf(
                OutboxEvent(
                    aggregateType = "USER",
                    aggregateId = "user-100",
                    eventType = "USER_REGISTRATION_STARTED",
                    eventData = """{"userId": "user-100"}""",
                    sagaId = sagaId,
                    sagaType = "USER_REGISTRATION_SAGA"
                ),
                OutboxEvent(
                    aggregateType = "USER",
                    aggregateId = "user-100", 
                    eventType = "USER_REGISTRATION_COMPLETED",
                    eventData = """{"userId": "user-100"}""",
                    sagaId = sagaId,
                    sagaType = "USER_REGISTRATION_SAGA"
                )
            )
            
            every { outboxEventRepository.findBySagaId(sagaId) } returns mockEvents
            
            val result = outboxService.getEventsBySaga(sagaId)
            
            then("해당 SAGA의 모든 이벤트가 반환되어야 한다") {
                result.size shouldBe 2
                result.forEach { event ->
                    event.sagaId shouldBe sagaId
                    event.sagaType shouldBe "USER_REGISTRATION_SAGA"
                }
            }
        }
    }
    
    given("OutboxService가 이벤트 데이터를 처리할 때") {
        
        `when`("Map 형태의 데이터를 JSON으로 변환하면") {
            val eventData = mapOf(
                "userId" to "user-456",
                "email" to "user456@example.com",
                "isActive" to true,
                "score" to 1250
            )
            
            val jsonString = outboxService.convertToJson(eventData)
            
            then("올바른 JSON 문자열이 생성되어야 한다") {
                jsonString shouldNotBe null
                jsonString.contains("user-456") shouldBe true
                jsonString.contains("user456@example.com") shouldBe true
                jsonString.contains("true") shouldBe true
                jsonString.contains("1250") shouldBe true
            }
        }
        
        `when`("빈 데이터를 JSON으로 변환하면") {
            val emptyData = emptyMap<String, Any>()
            val jsonString = outboxService.convertToJson(emptyData)
            
            then("빈 JSON 객체가 생성되어야 한다") {
                jsonString shouldBe "{}"
            }
        }
    }
})