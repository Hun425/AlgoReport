package com.algoreport.module.analysis

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 분석 서비스
 * TDD Red 단계: 기본 기능만 구현하여 테스트 실패 유도
 */
@Service
class AnalysisService {
    
    // 테스트용 인메모리 저장소
    private val personalAnalyses = ConcurrentHashMap<String, PersonalAnalysis>() // userId -> PersonalAnalysis
    private val groupAnalyses = ConcurrentHashMap<String, GroupAnalysis>() // groupId -> GroupAnalysis
    
    // 테스트용 실패 시뮬레이션 플래그
    var simulatePersonalAnalysisFailure = false
    var simulateGroupAnalysisFailure = false
    
    /**
     * 개인 분석 수행
     * TDD Green 단계: 실제 분석 로직 구현
     */
    fun performPersonalAnalysis(userId: String): PersonalAnalysis {
        if (simulatePersonalAnalysisFailure) {
            throw RuntimeException("Simulated personal analysis failure")
        }
        
        // Green 단계: 실제 개인 통계 분석 구현
        // 실제 구현에서는 Elasticsearch에서 solved.ac 제출 데이터 집계
        
        val analysis = PersonalAnalysis(
            userId = userId,
            analysisDate = java.time.LocalDateTime.now(),
            totalSolved = generateMockSolvedCount(), // 모의 해결 문제 수
            currentTier = generateMockTier(), // 모의 티어
            tagSkills = generateMockTagSkills(), // 모의 태그별 숙련도
            solvedByDifficulty = generateMockSolvedByDifficulty(), // 모의 난이도별 해결 문제 수
            recentActivity = generateMockRecentActivity(), // 모의 최근 활동
            weakTags = listOf("dp", "graph"), // 모의 취약 태그
            strongTags = listOf("greedy", "implementation") // 모의 강점 태그
        )
        
        personalAnalyses[userId] = analysis
        return analysis
    }
    
    /**
     * 그룹 분석 수행
     * TDD Green 단계: 실제 그룹 분석 로직 구현
     */
    fun performGroupAnalysis(groupId: String, memberIds: List<String>): GroupAnalysis {
        if (simulateGroupAnalysisFailure) {
            throw RuntimeException("Simulated group analysis failure")
        }
        
        // Green 단계: 실제 그룹 통계 분석 구현
        // 실제 구현에서는 그룹원들의 개인 분석 결과를 집계
        
        val analysis = GroupAnalysis(
            groupId = groupId,
            analysisDate = java.time.LocalDateTime.now(),
            memberCount = memberIds.size,
            totalGroupSolved = generateMockGroupSolvedCount(memberIds.size), // 모의 그룹 전체 해결 문제 수
            averageTier = generateMockAverageTier(), // 모의 그룹 평균 티어
            groupTagSkills = generateMockGroupTagSkills(), // 모의 그룹 태그별 평균 숙련도
            topPerformers = memberIds.take(3), // 상위 3명을 모의 성과자로 설정
            activeMemberRatio = 0.8, // 모의 활성 멤버 비율 80%
            groupWeakTags = listOf("math", "string"), // 모의 그룹 취약 태그
            groupStrongTags = listOf("bruteforce", "sorting") // 모의 그룹 강점 태그
        )
        
        groupAnalyses[groupId] = analysis
        return analysis
    }
    
    /**
     * 개인 분석 결과 존재 여부 확인
     */
    fun hasPersonalAnalysis(userId: String): Boolean {
        return personalAnalyses.containsKey(userId)
    }
    
    /**
     * 그룹 분석 결과 존재 여부 확인
     */
    fun hasGroupAnalysis(groupId: String): Boolean {
        return groupAnalyses.containsKey(groupId)
    }
    
    /**
     * 개인 분석 결과 조회
     */
    fun getPersonalAnalysis(userId: String): PersonalAnalysis? {
        return personalAnalyses[userId]
    }
    
    /**
     * 그룹 분석 결과 조회
     */
    fun getGroupAnalysis(groupId: String): GroupAnalysis? {
        return groupAnalyses[groupId]
    }
    
    /**
     * 개인 분석 결과 삭제 (보상 트랜잭션용)
     */
    fun deletePersonalAnalysis(userId: String) {
        personalAnalyses.remove(userId)
    }
    
    /**
     * 그룹 분석 결과 삭제 (보상 트랜잭션용)
     */
    fun deleteGroupAnalysis(groupId: String) {
        groupAnalyses.remove(groupId)
    }
    
    /**
     * 테스트용 초기화 메서드
     */
    fun clear() {
        personalAnalyses.clear()
        groupAnalyses.clear()
        simulatePersonalAnalysisFailure = false
        simulateGroupAnalysisFailure = false
    }
    
    // 모의 데이터 생성 메서드들 (TDD Green 단계용)
    
    private fun generateMockSolvedCount(): Int {
        return (100..500).random() // 100~500 문제 해결
    }
    
    private fun generateMockTier(): Int {
        return (1..30).random() // 티어 1~30
    }
    
    private fun generateMockTagSkills(): Map<String, Double> {
        return mapOf(
            "implementation" to 0.8,
            "math" to 0.6,
            "greedy" to 0.7,
            "dp" to 0.4,
            "graph" to 0.5,
            "string" to 0.9,
            "bruteforce" to 0.85,
            "sorting" to 0.75
        )
    }
    
    private fun generateMockSolvedByDifficulty(): Map<String, Int> {
        return mapOf(
            "Bronze" to (50..100).random(),
            "Silver" to (30..80).random(),
            "Gold" to (20..60).random(),
            "Platinum" to (5..30).random(),
            "Diamond" to (0..15).random(),
            "Ruby" to (0..5).random()
        )
    }
    
    private fun generateMockRecentActivity(): Map<String, Int> {
        return mapOf(
            "last7days" to (0..20).random(),
            "last30days" to (5..50).random(),
            "thisMonth" to (10..40).random(),
            "lastMonth" to (8..35).random()
        )
    }
    
    private fun generateMockGroupSolvedCount(memberCount: Int): Int {
        return memberCount * (150..400).random() // 멤버 수 × 평균 해결 문제 수
    }
    
    private fun generateMockAverageTier(): Double {
        return (10.0..25.0).random() // 평균 티어 10~25
    }
    
    private fun generateMockGroupTagSkills(): Map<String, Double> {
        return mapOf(
            "implementation" to 0.75,
            "math" to 0.55,
            "greedy" to 0.68,
            "dp" to 0.45,
            "graph" to 0.58,
            "string" to 0.82,
            "bruteforce" to 0.78,
            "sorting" to 0.72
        )
    }
    
    private fun ClosedRange<Double>.random(): Double {
        return java.util.Random().nextDouble() * (endInclusive - start) + start
    }
}