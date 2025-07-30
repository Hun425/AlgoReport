package com.algoreport.module.analysis

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 분석 프로필 관리 서비스
 * TDD Green 단계: 기본 기능만 구현
 */
@Service
class AnalysisProfileService {
    
    // 테스트용 인메모리 저장소
    private val profiles = ConcurrentHashMap<String, Boolean>() // userId -> hasProfile
    
    var simulateFailure = false
    
    fun createProfile(userId: String) {
        if (simulateFailure) {
            throw RuntimeException("Simulated analysis profile creation failure")
        }
        
        profiles[userId] = true
    }
    
    fun hasProfile(userId: String): Boolean {
        return profiles[userId] == true
    }
    
    fun deleteProfile(userId: String) {
        profiles.remove(userId)
    }
    
    // 테스트용 메서드
    fun clear() {
        profiles.clear()
        simulateFailure = false
    }
}