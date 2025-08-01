package com.algoreport.module.analysis

import com.algoreport.module.user.SagaStatus
import java.time.LocalDateTime
import java.util.*

/**
 * 분석 업데이트 SAGA 요청 데이터
 */
data class AnalysisUpdateRequest(
    val analysisDate: LocalDateTime,
    val batchSize: Int = 100,
    val enablePersonalAnalysis: Boolean = true,
    val enableGroupAnalysis: Boolean = true
)

/**
 * 분석 업데이트 SAGA 결과 데이터
 */
data class AnalysisUpdateResult(
    val sagaStatus: SagaStatus,
    val totalUsersProcessed: Int = 0,
    val totalGroupsProcessed: Int = 0,
    val batchesProcessed: Int = 0,
    val personalAnalysisCompleted: Boolean = false,
    val groupAnalysisCompleted: Boolean = false,
    val cacheUpdateCompleted: Boolean = false,
    val eventPublished: Boolean = false,
    val compensationExecuted: Boolean = false,
    val errorMessage: String? = null,
    val processingTimeMs: Long = 0L
)

/**
 * 개인 분석 데이터
 */
data class PersonalAnalysis(
    val userId: String = "",
    val analysisDate: LocalDateTime = LocalDateTime.now(),
    val totalSolved: Int = 0,
    val currentTier: Int = 0,
    val tagSkills: Map<String, Double> = emptyMap(), // 태그별 숙련도 (0.0 ~ 1.0)
    val solvedByDifficulty: Map<String, Int> = emptyMap(), // 난이도별 해결 문제 수
    val recentActivity: Map<String, Int> = emptyMap(), // 최근 30일 활동
    val weakTags: List<String> = emptyList(), // 취약한 알고리즘 태그
    val strongTags: List<String> = emptyList() // 강한 알고리즘 태그
)

/**
 * 그룹 분석 데이터
 */
data class GroupAnalysis(
    val groupId: String = "",
    val analysisDate: LocalDateTime = LocalDateTime.now(),
    val memberCount: Int = 0,
    val totalGroupSolved: Int = 0,
    val averageTier: Double = 0.0,
    val groupTagSkills: Map<String, Double> = emptyMap(), // 그룹 전체 태그별 평균 숙련도
    val topPerformers: List<String> = emptyList(), // 상위 성과자 사용자 ID 목록
    val activeMemberRatio: Double = 0.0, // 활성 멤버 비율 (최근 7일 활동 기준)
    val groupWeakTags: List<String> = emptyList(), // 그룹 전체 취약 태그
    val groupStrongTags: List<String> = emptyList() // 그룹 전체 강점 태그
)

/**
 * 분석 작업 배치 정보
 */
data class AnalysisBatch(
    val batchId: String = UUID.randomUUID().toString(),
    val userIds: List<String>,
    val groupIds: List<String>,
    val status: BatchStatus = BatchStatus.PENDING,
    val startTime: LocalDateTime? = null,
    val endTime: LocalDateTime? = null,
    val errorMessage: String? = null
)

/**
 * 배치 처리 상태
 */
enum class BatchStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}