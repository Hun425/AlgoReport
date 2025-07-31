package com.algoreport.config.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import java.util.*

/**
 * OutboxEventHandler 테스트
 *
 * 커버리지 향상을 위한 누락된 테스트 추가
 */
class OutboxEventHandlerTest : BehaviorSpec({

    given("OutboxEventHandler") {
        val outboxEventRepository = mockk<OutboxEventRepository>()
        val objectMapper = mockk<ObjectMapper>()
        val handler = OutboxEventHandler(outboxEventRepository, objectMapper)

        `when`("USER_REGISTERED 이벤트를 처리할 때") {
            val eventId = UUID.randomUUID().toString()
            val sagaId = UUID.randomUUID().toString()
            val eventPayload = """{"userId": "user123", "email": "test@example.com"}"""
            val topic = "USER_REGISTERED"

            val mockEvent = mockk<OutboxEvent>()
            val eventSlot = slot<OutboxEvent>()

            every { objectMapper.readValue(eventPayload, Map::class.java) } returns mapOf(
                "userId" to "user123",
                "email" to "test@example.com"
            )
            every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
            every { mockEvent.markAsProcessed() } just runs
            every { outboxEventRepository.save(capture(eventSlot)) } returns mockEvent

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

                verify(exactly = 1) { objectMapper.readValue(eventPayload, Map::class.java) }
                verify(exactly = 1) { outboxEventRepository.findById(UUID.fromString(eventId)) }
                verify(exactly = 1) { mockEvent.markAsProcessed() }
                verify(exactly = 1) { outboxEventRepository.save(any()) }
            }
        }

        `when`("STUDY_GROUP_CREATED 이벤트를 처리할 때") {
            val eventId = UUID.randomUUID().toString()
            val sagaId = UUID.randomUUID().toString()
            val eventPayload = """{"groupId": "group123", "ownerId": "owner123"}"""
            val topic = "STUDY_GROUP_CREATED"

            val mockEvent = mockk<OutboxEvent>()

            every { objectMapper.readValue(eventPayload, Map::class.java) } returns mapOf(
                "groupId" to "group123",
                "ownerId" to "owner123"
            )
            every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
            every { mockEvent.markAsProcessed() } just runs
            every { outboxEventRepository.save(any()) } returns mockEvent

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

                verify(exactly = 1) { objectMapper.readValue(eventPayload, Map::class.java) }
                verify(exactly = 1) { mockEvent.markAsProcessed() }
            }
        }

        `when`("ANALYSIS_REQUESTED 이벤트를 처리할 때") {
            val eventId = UUID.randomUUID().toString()
            val eventPayload = """{"userId": "user123", "analysisType": "full"}"""
            val topic = "ANALYSIS_REQUESTED"

            val mockEvent = mockk<OutboxEvent>()

            every { objectMapper.readValue(eventPayload, Map::class.java) } returns mapOf(
                "userId" to "user123",
                "analysisType" to "full"
            )
            every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
            every { mockEvent.markAsProcessed() } just runs
            every { outboxEventRepository.save(any()) } returns mockEvent

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

                verify(exactly = 1) { objectMapper.readValue(eventPayload, Map::class.java) }
                verify(exactly = 1) { mockEvent.markAsProcessed() }
            }
        }

        `when`("NOTIFICATION_SENT 이벤트를 처리할 때") {
            val eventId = UUID.randomUUID().toString()
            val eventPayload = """{"notificationId": "notif123", "userId": "user123"}"""
            val topic = "NOTIFICATION_SENT"

            val mockEvent = mockk<OutboxEvent>()

            every { objectMapper.readValue(eventPayload, Map::class.java) } returns mapOf(
                "notificationId" to "notif123",
                "userId" to "user123"
            )
            every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
            every { mockEvent.markAsProcessed() } just runs
            every { outboxEventRepository.save(any()) } returns mockEvent

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

                verify(exactly = 1) { objectMapper.readValue(eventPayload, Map::class.java) }
                verify(exactly = 1) { mockEvent.markAsProcessed() }
            }
        }

        `when`("알 수 없는 이벤트 타입을 처리할 때") {
            val eventId = UUID.randomUUID().toString()
            val eventPayload = """{"data": "unknown"}"""
            val topic = "UNKNOWN_EVENT"

            val mockEvent = mockk<OutboxEvent>()

            every { objectMapper.readValue(eventPayload, Map::class.java) } returns mapOf(
                "data" to "unknown"
            )
            every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
            every { mockEvent.markAsProcessed() } just runs
            every { outboxEventRepository.save(any()) } returns mockEvent

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

                verify(exactly = 1) { objectMapper.readValue(eventPayload, Map::class.java) }
                verify(exactly = 1) { mockEvent.markAsProcessed() }
            }
        }

        `when`("JSON 파싱에 실패할 때") {
            val eventId = UUID.randomUUID().toString()
            val invalidPayload = "invalid json"
            val topic = "USER_REGISTERED"

            val mockEvent = mockk<OutboxEvent>()

            every { objectMapper.readValue(invalidPayload, Map::class.java) } throws RuntimeException("Invalid JSON")
            every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
            every { mockEvent.markAsProcessed() } just runs
            every { outboxEventRepository.save(any()) } returns mockEvent

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

                verify(exactly = 1) { objectMapper.readValue(invalidPayload, Map::class.java) }
                verify(exactly = 1) { mockEvent.markAsProcessed() }
            }
        }

        `when`("eventId가 null일 때") {
            val eventPayload = """{"userId": "user123"}"""
            val topic = "USER_REGISTERED"

            every { objectMapper.readValue(eventPayload, Map::class.java) } returns mapOf(
                "userId" to "user123"
            )

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

                verify(exactly = 1) { objectMapper.readValue(eventPayload, Map::class.java) }
                verify(exactly = 0) { outboxEventRepository.findById(any()) }
                verify(exactly = 0) { outboxEventRepository.save(any()) }
            }
        }

        `when`("이벤트를 처리 완료로 마킹할 때 예외가 발생하면") {
            val eventId = UUID.randomUUID().toString()
            val eventPayload = """{"userId": "user123"}"""
            val topic = "USER_REGISTERED"

            every { objectMapper.readValue(eventPayload, Map::class.java) } returns mapOf(
                "userId" to "user123"
            )
            every { outboxEventRepository.findById(UUID.fromString(eventId)) } throws RuntimeException("DB Error")

            then("예외를 로그하지만 전체 처리는 성공해야 한다") {
                // 예외가 발생해도 전체 처리는 성공해야 함
                handler.handleOutboxEvent(
                    eventPayload = eventPayload,
                    topic = topic,
                    eventId = eventId,
                    sagaId = null,
                    sagaType = null,
                    aggregateType = "USER",
                    version = "1"
                )

                verify(exactly = 1) { objectMapper.readValue(eventPayload, Map::class.java) }
                verify(exactly = 1) { outboxEventRepository.findById(UUID.fromString(eventId)) }
                verify(exactly = 0) { outboxEventRepository.save(any()) }
            }
        }

        `when`("이벤트가 데이터베이스에 존재하지 않을 때") {
            val eventId = UUID.randomUUID().toString()
            val eventPayload = """{"userId": "user123"}"""
            val topic = "USER_REGISTERED"

            every { objectMapper.readValue(eventPayload, Map::class.java) } returns mapOf(
                "userId" to "user123"
            )
            every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.empty()

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

                verify(exactly = 1) { objectMapper.readValue(eventPayload, Map::class.java) }
                verify(exactly = 1) { outboxEventRepository.findById(UUID.fromString(eventId)) }
                verify(exactly = 0) { outboxEventRepository.save(any()) }
            }
        }
    }
})