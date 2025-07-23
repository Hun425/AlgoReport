package com.algoreport.config.outbox

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import java.time.LocalDateTime
import java.util.*

/**
 * OutboxEventRepository 테스트
 * TDD Red 단계: OutboxEventRepository 인터페이스가 존재하지 않으므로 컴파일 실패 예상
 */
@DataJpaTest
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OutboxEventRepositoryTest(
    private val outboxEventRepository: OutboxEventRepository
) : BehaviorSpec({
    
    given("OutboxEventRepository가 미처리 이벤트를 조회할 때") {
        
        beforeEach {
            // 테스트 데이터 초기화
            outboxEventRepository.deleteAll()
        }
        
        `when`("처리된 이벤트와 미처리 이벤트가 있으면") {
            // 미처리 이벤트 생성
            val unprocessedEvent1 = OutboxEvent(
                aggregateType = "USER",
                aggregateId = "user-1",
                eventType = "USER_REGISTERED",
                eventData = """{"userId": "user-1"}"""
            )
            val unprocessedEvent2 = OutboxEvent(
                aggregateType = "STUDY_GROUP", 
                aggregateId = "group-1",
                eventType = "GROUP_CREATED",
                eventData = """{"groupId": "group-1"}"""
            )
            
            // 처리된 이벤트 생성
            val processedEvent = OutboxEvent(
                aggregateType = "USER",
                aggregateId = "user-2", 
                eventType = "USER_UPDATED",
                eventData = """{"userId": "user-2"}"""
            ).apply {
                processed = true
                processedAt = LocalDateTime.now()
            }
            
            outboxEventRepository.saveAll(listOf(unprocessedEvent1, unprocessedEvent2, processedEvent))
            
            then("미처리 이벤트만 조회되어야 한다") {
                val unprocessedEvents = outboxEventRepository.findUnprocessedEvents(
                    PageRequest.of(0, 10)
                )
                
                unprocessedEvents shouldHaveSize 2
                unprocessedEvents.forEach { event ->
                    event.processed shouldBe false
                }
            }
        }
        
        `when`("재시도 대상 이벤트가 있으면") {
            val now = LocalDateTime.now()
            
            // 재시도 시간이 지난 이벤트
            val retryableEvent = OutboxEvent(
                aggregateType = "ANALYSIS",
                aggregateId = "analysis-1",
                eventType = "ANALYSIS_REQUESTED", 
                eventData = """{"analysisId": "analysis-1"}"""
            ).apply {
                retryCount = 1
                nextRetryAt = now.minusMinutes(5) // 5분 전
                errorMessage = "Temporary failure"
            }
            
            // 재시도 시간이 아직 안 된 이벤트
            val notYetRetryableEvent = OutboxEvent(
                aggregateType = "ANALYSIS",
                aggregateId = "analysis-2",
                eventType = "ANALYSIS_REQUESTED",
                eventData = """{"analysisId": "analysis-2"}"""
            ).apply {
                retryCount = 1
                nextRetryAt = now.plusMinutes(5) // 5분 후
                errorMessage = "Temporary failure"
            }
            
            outboxEventRepository.saveAll(listOf(retryableEvent, notYetRetryableEvent))
            
            then("재시도 시간이 지난 이벤트만 조회되어야 한다") {
                val retryableEvents = outboxEventRepository.findRetryableEvents(
                    now, PageRequest.of(0, 10)
                )
                
                retryableEvents shouldHaveSize 1
                retryableEvents[0].eventId shouldBe retryableEvent.eventId
                retryableEvents[0].retryCount shouldBe 1
            }
        }
        
        `when`("집합체별로 이벤트를 조회하면") {
            val userEvents = listOf(
                OutboxEvent(
                    aggregateType = "USER",
                    aggregateId = "user-123",
                    eventType = "USER_REGISTERED",
                    eventData = """{"userId": "user-123"}"""
                ),
                OutboxEvent(
                    aggregateType = "USER", 
                    aggregateId = "user-123",
                    eventType = "USER_UPDATED",
                    eventData = """{"userId": "user-123"}"""
                )
            )
            
            val groupEvent = OutboxEvent(
                aggregateType = "STUDY_GROUP",
                aggregateId = "group-456", 
                eventType = "GROUP_CREATED",
                eventData = """{"groupId": "group-456"}"""
            )
            
            outboxEventRepository.saveAll(userEvents + groupEvent)
            
            then("특정 집합체의 이벤트만 조회되어야 한다") {
                val userOnlyEvents = outboxEventRepository.findByAggregateTypeAndAggregateId(
                    "USER", "user-123"
                )
                
                userOnlyEvents shouldHaveSize 2
                userOnlyEvents.forEach { event ->
                    event.aggregateType shouldBe "USER"
                    event.aggregateId shouldBe "user-123"
                }
            }
        }
    }
    
    given("OutboxEventRepository가 이벤트를 업데이트할 때") {
        
        `when`("처리 완료로 표시하면") {
            val event = OutboxEvent(
                aggregateType = "NOTIFICATION",
                aggregateId = "notification-1",
                eventType = "NOTIFICATION_SENT", 
                eventData = """{"notificationId": "notification-1"}"""
            )
            val savedEvent = outboxEventRepository.save(event)
            
            val processedAt = LocalDateTime.now()
            outboxEventRepository.markAsProcessed(savedEvent.eventId, processedAt)
            
            then("처리 상태가 업데이트되어야 한다") {
                val updatedEvent = outboxEventRepository.findById(savedEvent.eventId).get()
                
                updatedEvent.processed shouldBe true
                updatedEvent.processedAt shouldBe processedAt
            }
        }
        
        `when`("재시도 정보를 업데이트하면") {
            val event = OutboxEvent(
                aggregateType = "NOTIFICATION",
                aggregateId = "notification-2", 
                eventType = "NOTIFICATION_FAILED",
                eventData = """{"notificationId": "notification-2"}"""
            )
            val savedEvent = outboxEventRepository.save(event)
            
            val nextRetryAt = LocalDateTime.now().plusMinutes(10)
            val errorMessage = "Connection timeout"
            
            outboxEventRepository.updateRetryInfo(
                savedEvent.eventId, 
                2, 
                nextRetryAt, 
                errorMessage
            )
            
            then("재시도 정보가 업데이트되어야 한다") {
                val updatedEvent = outboxEventRepository.findById(savedEvent.eventId).get()
                
                updatedEvent.retryCount shouldBe 2
                updatedEvent.nextRetryAt shouldBe nextRetryAt
                updatedEvent.errorMessage shouldBe errorMessage
            }
        }
    }
})