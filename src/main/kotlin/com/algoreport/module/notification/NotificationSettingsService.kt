package com.algoreport.module.user

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 알림 설정 관리 서비스
 * TDD Green 단계: 기본 기능만 구현
 */
@Service
class NotificationSettingsService {
    
    // 테스트용 인메모리 저장소
    private val settings = ConcurrentHashMap<String, Boolean>() // userId -> hasSettings
    
    var simulateFailure = false
    
    fun createSettings(userId: String) {
        if (simulateFailure) {
            throw RuntimeException("Simulated notification settings creation failure")
        }
        
        settings[userId] = true
    }
    
    fun hasSettings(userId: String): Boolean {
        return settings[userId] == true
    }
    
    fun deleteSettings(userId: String) {
        settings.remove(userId)
    }
    
    // 테스트용 메서드
    fun clear() {
        settings.clear()
        simulateFailure = false
    }
}