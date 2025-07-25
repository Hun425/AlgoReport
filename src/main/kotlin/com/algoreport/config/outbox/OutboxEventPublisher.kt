package com.algoreport.module.user

import com.algoreport.config.outbox.OutboxEvent
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Outbox 이벤트 발행자 (테스트용)
 * TDD Green 단계: 기본 기능만 구현
 */
@Service
class OutboxEventPublisher {
    
    // 테스트용 이벤트 저장소
    private val publishedEvents = ConcurrentLinkedQueue<OutboxEvent>()
    
    fun publish(event: OutboxEvent) {
        publishedEvents.add(event)
    }
    
    fun getPublishedEvents(): List<OutboxEvent> {
        return publishedEvents.toList()
    }
    
    // 테스트용 메서드
    fun clear() {
        publishedEvents.clear()
    }
}