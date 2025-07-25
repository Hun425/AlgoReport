package com.algoreport.config.outbox

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * OutboxEvent 엔티티 테스트
 * TDD Red 단계: OutboxEvent 엔티티 클래스가 존재하지 않으므로 컴파일 실패 예상
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional
class OutboxEventTest(
    private val testEntityManager: TestEntityManager
) : BehaviorSpec() {
    
    override fun extensions() = listOf(SpringExtension)
    
    init {
        given("OutboxEvent 엔티티가 생성될 때") {
            val eventData = """{"userId": "user-123", "email": "test@example.com"}"""
            val sagaId = UUID.randomUUID()
            
            `when`("필수 필드만 제공하면") {
                val outboxEvent = OutboxEvent(
                    aggregateType = "USER",
                    aggregateId = "user-123", 
                    eventType = "USER_REGISTERED",
                    eventData = eventData,
                    createdAt = LocalDateTime.now()
                )
                
                then("기본값이 올바르게 설정되어야 한다") {
                    outboxEvent.eventId shouldNotBe null
                    outboxEvent.aggregateType shouldBe "USER"
                    outboxEvent.aggregateId shouldBe "user-123"
                    outboxEvent.eventType shouldBe "USER_REGISTERED"
                    outboxEvent.eventData shouldBe eventData
                    outboxEvent.sagaId shouldBe null
                    outboxEvent.sagaType shouldBe null
                    outboxEvent.processed shouldBe false
                    outboxEvent.processedAt shouldBe null
                    outboxEvent.version shouldBe 1
                    outboxEvent.createdAt shouldNotBe null
                }
            }
            
            `when`("모든 필드를 제공하면") {
                val outboxEvent = OutboxEvent(
                    aggregateType = "STUDY_GROUP",
                    aggregateId = "group-456",
                    eventType = "GROUP_CREATED", 
                    eventData = eventData,
                    sagaId = sagaId,
                    sagaType = "CREATE_GROUP_SAGA"
                )
                
                then("모든 값이 올바르게 설정되어야 한다") {
                    outboxEvent.aggregateType shouldBe "STUDY_GROUP"
                    outboxEvent.aggregateId shouldBe "group-456"
                    outboxEvent.eventType shouldBe "GROUP_CREATED"
                    outboxEvent.sagaId shouldBe sagaId
                    outboxEvent.sagaType shouldBe "CREATE_GROUP_SAGA"
                }
            }
        }
        
        given("OutboxEvent가 데이터베이스에 저장될 때") {
            val outboxEvent = OutboxEvent(
                aggregateType = "USER",
                aggregateId = "user-789",
                eventType = "USER_UPDATED",
                eventData = """{"userId": "user-789", "status": "ACTIVE"}"""
            )
            
            `when`("엔티티를 저장하면") {
                val savedEvent = testEntityManager.persistAndFlush(outboxEvent)
                testEntityManager.clear()
                
                then("데이터베이스에서 조회할 수 있어야 한다") {
                    val foundEvent = testEntityManager.find(OutboxEvent::class.java, savedEvent.eventId)
                    
                    foundEvent shouldNotBe null
                    foundEvent.eventId shouldBe savedEvent.eventId
                    foundEvent.aggregateType shouldBe "USER"
                    foundEvent.aggregateId shouldBe "user-789"
                    foundEvent.eventType shouldBe "USER_UPDATED"
                    foundEvent.processed shouldBe false
                }
            }
            
            `when`("처리 상태를 업데이트하면") {
                val savedEvent = testEntityManager.persistAndFlush(outboxEvent)
                savedEvent.processed = true
                savedEvent.processedAt = LocalDateTime.now()
                
                testEntityManager.flush()
                testEntityManager.clear()
                
                then("변경사항이 저장되어야 한다") {
                    val updatedEvent = testEntityManager.find(OutboxEvent::class.java, savedEvent.eventId)
                    
                    updatedEvent.processed shouldBe true
                    updatedEvent.processedAt shouldNotBe null
                }
            }
        }
    }
}