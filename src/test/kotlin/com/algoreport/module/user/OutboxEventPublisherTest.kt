package com.algoreport.module.user

import com.algoreport.config.outbox.OutboxEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldContain
import java.time.LocalDateTime
import java.util.*

/**
 * OutboxEventPublisher 테스트
 * 
 * 커버리지 향상을 위한 누락된 테스트 추가
 */
class OutboxEventPublisherTest : BehaviorSpec({
    
    given("OutboxEventPublisher") {
        val publisher = OutboxEventPublisher()
        
        beforeEach {
            publisher.clear()
        }
        
        `when`("이벤트를 발행할 때") {
            val event = OutboxEvent(
                aggregateType = "USER",
                aggregateId = UUID.randomUUID().toString(),
                eventType = "USER_REGISTERED",
                eventData = """{"userId": "user123", "email": "test@example.com"}""",
                sagaId = UUID.randomUUID(),
                sagaType = "USER_REGISTRATION"
            )
            
            publisher.publish(event)
            
            then("발행된 이벤트 목록에 추가되어야 한다") {
                val publishedEvents = publisher.getPublishedEvents()
                
                publishedEvents shouldHaveSize 1
                publishedEvents shouldContain event
            }
        }
        
        `when`("여러 개의 이벤트를 발행할 때") {
            val event1 = OutboxEvent(
                aggregateType = "USER",
                aggregateId = UUID.randomUUID().toString(),
                eventType = "USER_REGISTERED",
                eventData = """{"userId": "user1"}""",
                sagaId = UUID.randomUUID(),
                sagaType = "USER_REGISTRATION"
            )
            
            val event2 = OutboxEvent(
                aggregateType = "USER",
                aggregateId = UUID.randomUUID().toString(),
                eventType = "SOLVEDAC_LINKED",
                eventData = """{"userId": "user2", "handle": "testhandle"}""",
                sagaId = UUID.randomUUID(),
                sagaType = "SOLVEDAC_LINK"
            )
            
            publisher.publish(event1)
            publisher.publish(event2)
            
            then("모든 이벤트가 순서대로 저장되어야 한다") {
                val publishedEvents = publisher.getPublishedEvents()
                
                publishedEvents shouldHaveSize 2
                publishedEvents shouldContain event1
                publishedEvents shouldContain event2
                
                // 발행 순서가 유지되어야 함
                publishedEvents[0] shouldBe event1
                publishedEvents[1] shouldBe event2
            }
        }
        
        `when`("동일한 이벤트를 여러 번 발행할 때") {
            val event = OutboxEvent(
                aggregateType = "USER",
                aggregateId = UUID.randomUUID().toString(),
                eventType = "USER_REGISTERED",
                eventData = """{"userId": "user123"}""",
                sagaId = UUID.randomUUID(),
                sagaType = "USER_REGISTRATION"
            )
            
            publisher.publish(event)
            publisher.publish(event)
            publisher.publish(event)
            
            then("중복 제거 없이 모든 발행이 기록되어야 한다") {
                val publishedEvents = publisher.getPublishedEvents()
                
                publishedEvents shouldHaveSize 3
                publishedEvents.all { it == event } shouldBe true
            }
        }
        
        `when`("초기 상태에서 발행된 이벤트 목록을 조회할 때") {
            then("빈 목록을 반환해야 한다") {
                val publishedEvents = publisher.getPublishedEvents()
                publishedEvents.shouldBeEmpty()
            }
        }
        
        `when`("clear() 메서드를 호출할 때") {
            val event1 = OutboxEvent(
                aggregateType = "USER",
                aggregateId = UUID.randomUUID().toString(),
                eventType = "USER_REGISTERED",
                eventData = """{"userId": "user1"}""",
                sagaId = UUID.randomUUID(),
                sagaType = "USER_REGISTRATION"
            )
            
            val event2 = OutboxEvent(
                aggregateType = "USER",
                aggregateId = UUID.randomUUID().toString(),
                eventType = "SOLVEDAC_LINKED",
                eventData = """{"userId": "user2"}""",
                sagaId = UUID.randomUUID(),
                sagaType = "SOLVEDAC_LINK"
            )
            
            // 이벤트 발행 후 clear
            publisher.publish(event1)
            publisher.publish(event2)
            publisher.clear()
            
            then("모든 발행된 이벤트가 제거되어야 한다") {
                val publishedEvents = publisher.getPublishedEvents()
                publishedEvents.shouldBeEmpty()
            }
        }
        
        `when`("clear 후 새로운 이벤트를 발행할 때") {
            val initialEvent = OutboxEvent(
                aggregateType = "USER",
                aggregateId = UUID.randomUUID().toString(),
                eventType = "INITIAL_EVENT",
                eventData = """{"initial": "true"}""",
                sagaId = UUID.randomUUID(),
                sagaType = "INITIAL"
            )
            
            val newEvent = OutboxEvent(
                aggregateType = "USER",
                aggregateId = UUID.randomUUID().toString(),
                eventType = "NEW_EVENT",
                eventData = """{"new": "true"}""",
                sagaId = UUID.randomUUID(),
                sagaType = "NEW"
            )
            
            publisher.publish(initialEvent)
            publisher.clear()
            publisher.publish(newEvent)
            
            then("새로운 이벤트만 저장되어야 한다") {
                val publishedEvents = publisher.getPublishedEvents()
                
                publishedEvents shouldHaveSize 1
                publishedEvents shouldContain newEvent
                publishedEvents.contains(initialEvent) shouldBe false
            }
        }
        
        `when`("대량의 이벤트를 발행할 때") {
            val events = (1..1000).map { index ->
                OutboxEvent(
                    aggregateType = "USER",
                    aggregateId = UUID.randomUUID().toString(),
                    eventType = "BULK_EVENT_$index",
                    eventData = """{"index": $index}""",
                    sagaId = UUID.randomUUID(),
                    sagaType = "BULK_TEST"
                )
            }
            
            events.forEach { publisher.publish(it) }
            
            then("모든 이벤트가 정상적으로 저장되어야 한다") {
                val publishedEvents = publisher.getPublishedEvents()
                
                publishedEvents shouldHaveSize 1000
                events.forEach { event ->
                    publishedEvents shouldContain event
                }
            }
        }
    }
})