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
                aggregateType = "USER",
                aggregateId = "user-123",
                eventType = "USER_REGISTERED",
                eventData = """{"userId": "user-123", "email": "test@example.com"}"""
            ).apply {
                this.eventId = eventId
            }
            
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
                            event.processed == false
                        }
                    )
                }
            }
        }
        
        `when`("SAGA 정보와 함께 이벤트를 발행하면") {
            val sagaId = UUID.randomUUID()
            val eventId = UUID.randomUUID()
            val mockEvent = OutboxEvent(
                aggregateType = "STUDY_GROUP",
                aggregateId = "group-456",
                eventType = "GROUP_CREATED",
                eventData = """{"groupId": "group-456", "ownerId": "user-123"}""",
                sagaId = sagaId,
                sagaType = "CREATE_GROUP_SAGA"
            ).apply {
                this.eventId = eventId
            }
            
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
        
        `when`("getEventStatistics를 호출할 때") {
            every { outboxEventRepository.countUnprocessedEvents() } returns 5L
            every { outboxEventRepository.count() } returns 20L
            
            then("올바른 통계 정보를 반환해야 한다") {
                val statistics = outboxService.getEventStatistics()
                
                statistics.totalEvents shouldBe 20L
                statistics.unprocessedEvents shouldBe 5L
                statistics.retryingEvents shouldBe 0L
                statistics.processedEvents shouldBe 15L
                
                verify(exactly = 1) { outboxEventRepository.countUnprocessedEvents() }
                verify(exactly = 1) { outboxEventRepository.count() }
            }
        }
    }
    
    given("OutboxService.EventStatistics 데이터 클래스") {
        `when`("동일한 데이터로 객체를 생성할 때") {
            val statistics1 = OutboxService.EventStatistics(
                totalEvents = 100L,
                unprocessedEvents = 10L,
                retryingEvents = 5L,
                processedEvents = 85L
            )
            
            val statistics2 = OutboxService.EventStatistics(
                totalEvents = 100L,
                unprocessedEvents = 10L,
                retryingEvents = 5L,
                processedEvents = 85L
            )
            
            val statistics3 = OutboxService.EventStatistics(
                totalEvents = 200L,
                unprocessedEvents = 20L,
                retryingEvents = 10L,
                processedEvents = 170L
            )
            
            then("equals와 hashCode가 올바르게 동작해야 한다") {
                statistics1 shouldBe statistics2
                statistics1.hashCode() shouldBe statistics2.hashCode()
                statistics1 shouldNotBe statistics3
            }
        }
        
        `when`("객체의 필드를 확인할 때") {
            val statistics = OutboxService.EventStatistics(
                totalEvents = 100L,
                unprocessedEvents = 10L,
                retryingEvents = 5L,
                processedEvents = 85L
            )
            
            then("모든 필드가 올바르게 설정되어야 한다") {
                statistics.totalEvents shouldBe 100L
                statistics.unprocessedEvents shouldBe 10L
                statistics.retryingEvents shouldBe 5L
                statistics.processedEvents shouldBe 85L
            }
        }
        
        `when`("copy 메서드를 사용할 때") {
            val original = OutboxService.EventStatistics(
                totalEvents = 100L,
                unprocessedEvents = 10L,
                retryingEvents = 5L,
                processedEvents = 85L
            )
            
            val copied = original.copy(totalEvents = 150L)
            
            then("지정된 필드만 변경되어야 한다") {
                copied.totalEvents shouldBe 150L
                copied.unprocessedEvents shouldBe original.unprocessedEvents
                copied.retryingEvents shouldBe original.retryingEvents
                copied.processedEvents shouldBe original.processedEvents
            }
        }
    }
})