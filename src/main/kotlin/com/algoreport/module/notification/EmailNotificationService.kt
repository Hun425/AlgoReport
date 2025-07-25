package com.algoreport.module.user

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 이메일 알림 서비스
 * TDD Green 단계: 기본 기능만 구현
 */
@Service
class EmailNotificationService {
    
    // 테스트용 인메모리 저장소
    private val sentEmails = ConcurrentHashMap<String, Boolean>() // userId -> welcomeEmailSent
    
    fun sendWelcomeEmail(userId: String) {
        // 실제로는 이메일 발송 로직이 들어감
        // 현재는 테스트용으로 발송 기록만 저장
        sentEmails[userId] = true
    }
    
    fun wasWelcomeEmailSent(userId: String): Boolean {
        return sentEmails[userId] == true
    }
    
    // 테스트용 메서드
    fun clear() {
        sentEmails.clear()
    }
}