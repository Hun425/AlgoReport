package com.algoreport.config.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * OutboxEventHandler 테스트
 *
 * 커버리지 향상을 위한 누락된 테스트 추가
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OutboxEventHandlerTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        given("OutboxEventHandler") {
            `when`("USER_REGISTERED 이벤트를 처리할 때") {
                // 각 테스트마다 새로운 Mock 인스턴스 생성
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val sagaId = UUID.randomUUID().toString()
                val eventPayload = """{"userId": "user123", "email": "test@example.com"}"""
                val topic = "USER_REGISTERED"

                val mockEvent = mockk<OutboxEvent>()
                
                // Mock 설정을 정확히 맞춤
                every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
                every { mockEvent.markAsProcessed() } just runs
                every { outboxEventRepository.save(mockEvent) } returns mockEvent

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

                    // Repository 호출 검증
                    verify(exactly = 1) { outboxEventRepository.findById(UUID.fromString(eventId)) }
                    verify(exactly = 1) { mockEvent.markAsProcessed() }
                    verify(exactly = 1) { outboxEventRepository.save(mockEvent) }
                }
            }

            `when`("STUDY_GROUP_CREATED 이벤트를 처리할 때") {
                // 각 테스트마다 새로운 Mock 인스턴스 생성
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val sagaId = UUID.randomUUID().toString()
                val eventPayload = """{"groupId": "group123", "ownerId": "owner123"}"""
                val topic = "STUDY_GROUP_CREATED"

                val mockEvent = mockk<OutboxEvent>()
                
                every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
                every { mockEvent.markAsProcessed() } just runs
                every { outboxEventRepository.save(mockEvent) } returns mockEvent

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

                    verify(exactly = 1) { mockEvent.markAsProcessed() }
                }
            }

            `when`("ANALYSIS_REQUESTED 이벤트를 처리할 때") {
                // 각 테스트마다 새로운 Mock 인스턴스 생성
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"userId": "user123", "analysisType": "full"}"""
                val topic = "ANALYSIS_REQUESTED"

                val mockEvent = mockk<OutboxEvent>()
                
                every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
                every { mockEvent.markAsProcessed() } just runs
                every { outboxEventRepository.save(mockEvent) } returns mockEvent

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

                    verify(exactly = 1) { mockEvent.markAsProcessed() }
                }
            }

            `when`("NOTIFICATION_SENT 이벤트를 처리할 때") {
                // 각 테스트마다 새로운 Mock 인스턴스 생성
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"notificationId": "notif123", "userId": "user123"}"""
                val topic = "NOTIFICATION_SENT"

                val mockEvent = mockk<OutboxEvent>()
                
                every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
                every { mockEvent.markAsProcessed() } just runs
                every { outboxEventRepository.save(mockEvent) } returns mockEvent

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

                    verify(exactly = 1) { mockEvent.markAsProcessed() }
                }
            }

            `when`("알 수 없는 이벤트 타입을 처리할 때") {
                // 각 테스트마다 새로운 Mock 인스턴스 생성
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"data": "unknown"}"""
                val topic = "UNKNOWN_EVENT"

                val mockEvent = mockk<OutboxEvent>()
                
                every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
                every { mockEvent.markAsProcessed() } just runs
                every { outboxEventRepository.save(mockEvent) } returns mockEvent

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

                    verify(exactly = 1) { mockEvent.markAsProcessed() }
                }
            }

            `when`("JSON 파싱에 실패할 때") {
                // 각 테스트마다 새로운 Mock 인스턴스 생성
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val invalidPayload = "invalid json"
                val topic = "USER_REGISTERED"

                val mockEvent = mockk<OutboxEvent>()
                
                every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
                every { mockEvent.markAsProcessed() } just runs
                every { outboxEventRepository.save(mockEvent) } returns mockEvent

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

                    // JSON 파싱 실패해도 이벤트 마킹은 수행되어야 함
                    verify(exactly = 1) { mockEvent.markAsProcessed() }
                }
            }

            `when`("eventId가 null일 때") {
                // 각 테스트마다 새로운 Mock 인스턴스 생성
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventPayload = """{"userId": "user123"}"""
                val topic = "USER_REGISTERED"

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

                    // eventId가 null이면 repository 호출하지 않음
                    verify(exactly = 0) { outboxEventRepository.findById(any()) }
                }
            }

            `when`("이벤트를 처리 완료로 마킹할 때 예외가 발생하면") {
                // 각 테스트마다 새로운 Mock 인스턴스 생성
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"userId": "user123"}"""
                val topic = "USER_REGISTERED"

                // Repository에서 예외 발생하도록 설정
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

                    verify(exactly = 1) { outboxEventRepository.findById(UUID.fromString(eventId)) }
                    // 예외 발생으로 인해 save는 호출되지 않음
                    verify(exactly = 0) { outboxEventRepository.save(any()) }
                }
            }

            `when`("이벤트가 데이터베이스에 존재하지 않을 때") {
                // 각 테스트마다 새로운 Mock 인스턴스 생성
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"userId": "user123"}"""
                val topic = "USER_REGISTERED"

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

                    verify(exactly = 1) { outboxEventRepository.findById(UUID.fromString(eventId)) }
                    // Optional.empty()이므로 save는 호출되지 않음
                    verify(exactly = 0) { outboxEventRepository.save(any()) }
                }
            }

            `when`("USER_PROFILE_UPDATED 이벤트를 처리할 때") {
                // 각 테스트마다 새로운 Mock 인스턴스 생성
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"userId": "user123", "profileData": "updated"}"""
                val topic = "USER_PROFILE_UPDATED"

                val mockEvent = mockk<OutboxEvent>()
                
                every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
                every { mockEvent.markAsProcessed() } just runs
                every { outboxEventRepository.save(mockEvent) } returns mockEvent

                then("사용자 프로필 업데이트 이벤트가 성공적으로 처리되어야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = "USER",
                        version = "1"
                    )

                    verify(exactly = 1) { mockEvent.markAsProcessed() }
                }
            }

            `when`("MEMBER_JOINED 이벤트를 처리할 때") {
                // 각 테스트마다 새로운 Mock 인스턴스 생성
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"groupId": "group123", "userId": "user456"}"""
                val topic = "MEMBER_JOINED"

                val mockEvent = mockk<OutboxEvent>()
                
                every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
                every { mockEvent.markAsProcessed() } just runs
                every { outboxEventRepository.save(mockEvent) } returns mockEvent

                then("멤버 참여 이벤트가 성공적으로 처리되어야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = "STUDY_GROUP",
                        version = "1"
                    )

                    verify(exactly = 1) { mockEvent.markAsProcessed() }
                }
            }

            `when`("ANALYSIS_COMPLETED 이벤트를 처리할 때") {
                // 각 테스트마다 새로운 Mock 인스턴스 생성
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"analysisId": "analysis123", "result": "completed"}"""
                val topic = "ANALYSIS_COMPLETED"

                val mockEvent = mockk<OutboxEvent>()
                
                every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
                every { mockEvent.markAsProcessed() } just runs
                every { outboxEventRepository.save(mockEvent) } returns mockEvent

                then("분석 완료 이벤트가 성공적으로 처리되어야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = "ANALYSIS",
                        version = "1"
                    )

                    verify(exactly = 1) { mockEvent.markAsProcessed() }
                }
            }

            `when`("알려지지 않은 STUDY_GROUP 이벤트를 처리할 때") {
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"groupId": "group123", "action": "unknown"}"""
                val topic = "STUDY_GROUP_UNKNOWN_ACTION"

                val mockEvent = mockk<OutboxEvent>()
                
                every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
                every { mockEvent.markAsProcessed() } just runs
                every { outboxEventRepository.save(mockEvent) } returns mockEvent

                then("기본 처리만 수행하고 성공해야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = "STUDY_GROUP",
                        version = "1"
                    )

                    verify(exactly = 1) { mockEvent.markAsProcessed() }
                }
            }

            `when`("알려지지 않은 USER 이벤트를 처리할 때") {
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"userId": "user123", "action": "unknown"}"""
                val topic = "USER_UNKNOWN_ACTION"

                val mockEvent = mockk<OutboxEvent>()
                
                every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
                every { mockEvent.markAsProcessed() } just runs
                every { outboxEventRepository.save(mockEvent) } returns mockEvent

                then("기본 처리만 수행하고 성공해야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = "USER",
                        version = "1"
                    )

                    verify(exactly = 1) { mockEvent.markAsProcessed() }
                }
            }

            `when`("알려지지 않은 ANALYSIS 이벤트를 처리할 때") {
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"analysisId": "analysis123", "action": "unknown"}"""
                val topic = "ANALYSIS_UNKNOWN_ACTION"

                val mockEvent = mockk<OutboxEvent>()
                
                every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
                every { mockEvent.markAsProcessed() } just runs
                every { outboxEventRepository.save(mockEvent) } returns mockEvent

                then("기본 처리만 수행하고 성공해야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = "ANALYSIS",
                        version = "1"
                    )

                    verify(exactly = 1) { mockEvent.markAsProcessed() }
                }
            }

            `when`("알려지지 않은 NOTIFICATION 이벤트를 처리할 때") {
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val objectMapper = ObjectMapper()
                val handler = OutboxEventHandler(outboxEventRepository, objectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"notificationId": "notif123", "action": "unknown"}"""
                val topic = "NOTIFICATION_UNKNOWN_ACTION"

                val mockEvent = mockk<OutboxEvent>()
                
                every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
                every { mockEvent.markAsProcessed() } just runs
                every { outboxEventRepository.save(mockEvent) } returns mockEvent

                then("기본 처리만 수행하고 성공해야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = "NOTIFICATION",
                        version = "1"
                    )

                    verify(exactly = 1) { mockEvent.markAsProcessed() }
                }
            }

            `when`("비즈니스 로직 처리 중 예외가 발생할 때") {
                // 예외 발생을 위해 잘못된 ObjectMapper 사용
                val outboxEventRepository = mockk<OutboxEventRepository>()
                val faultyObjectMapper = mockk<ObjectMapper>()
                val handler = OutboxEventHandler(outboxEventRepository, faultyObjectMapper)
                
                val eventId = UUID.randomUUID().toString()
                val eventPayload = """{"userId": "user123"}"""
                val topic = "USER_REGISTERED"

                val mockEvent = mockk<OutboxEvent>()
                
                // parseEventPayload에서 예외 발생하도록 설정
                every { faultyObjectMapper.readValue(eventPayload, Map::class.java) } throws RuntimeException("JSON Parse Error")
                every { outboxEventRepository.findById(UUID.fromString(eventId)) } returns Optional.of(mockEvent)
                every { mockEvent.markAsProcessed() } just runs
                every { outboxEventRepository.save(mockEvent) } returns mockEvent

                then("예외를 처리하고 이벤트 마킹은 정상 수행되어야 한다") {
                    handler.handleOutboxEvent(
                        eventPayload = eventPayload,
                        topic = topic,
                        eventId = eventId,
                        sagaId = null,
                        sagaType = null,
                        aggregateType = "USER",
                        version = "1"
                    )

                    // JSON 파싱 실패로 인해 빈 맵으로 처리되지만 이벤트 마킹은 수행됨
                    verify(exactly = 1) { mockEvent.markAsProcessed() }
                }
            }
        }
    }
}