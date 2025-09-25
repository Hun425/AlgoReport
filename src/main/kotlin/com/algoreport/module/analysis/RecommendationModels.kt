package com.algoreport.module.analysis

import java.time.LocalDateTime
import java.util.UUID

/**
 * 문제 추천 요청 데이터
 */
data class RecommendationRequest(
    val userId: UUID,
    val maxRecommendations: Int = 5,
    val forceRefresh: Boolean = false
)

/**
 * 추천 문제 정보
 */
data class RecommendedProblem(
    val problemId: String,
    val title: String,
    val difficulty: String,
    val tags: List<String>,
    val recommendationReason: String,
    val weakTag: String,
    val estimatedDifficulty: Int,
    val url: String = "https://www.acmicpc.net/problem/$problemId"
)

/**
 * 문제 추천 응답 데이터
 */
data class RecommendationResponse(
    val userId: UUID,
    val recommendedProblems: List<RecommendedProblem>,
    val totalRecommendations: Int,
    val weakTags: List<String>,
    val userCurrentTier: Int,
    val recommendationStrategy: String,
    val cacheHit: Boolean = false,
    val responseTimeMs: Long = 0,
    val dataSource: String = "LIVE",
    val lastUpdated: LocalDateTime = LocalDateTime.now(),
    val message: String? = null
)

/**
 * 사용자 취약점 분석 데이터
 */
data class UserWeakness(
    val userId: UUID,
    val weakTags: List<String>,
    val currentTier: Int,
    val totalSolved: Int,
    val tagSkills: Map<String, Double>
)

/**
 * 문제 메타데이터
 */
data class ProblemMetadata(
    val problemId: String,
    val title: String,
    val difficulty: String,
    val tier: Int,
    val tags: List<String>,
    val acceptedUserCount: Int,
    val level: Int
)

/**
 * 스터디 그룹 대시보드 요청 데이터
 */
data class StudyGroupDashboardRequest(
    val groupId: String,
    val forceRefresh: Boolean = false
)

/**
 * 그룹 멤버 정보
 */
data class GroupMemberInfo(
    val userId: String,
    val nickname: String? = null,
    val currentTier: Int,
    val totalSolved: Int,
    val recentActivity: Int, // 최근 7일간 문제 해결 수
    val contributionRank: Int, // 그룹 내 기여도 순위
    val isActive: Boolean = true
)

/**
 * 스터디 그룹 통계 정보
 */
data class StudyGroupStats(
    val averageTier: Double,
    val totalSolvedByGroup: Int,
    val activeMembers: Int,
    val totalMembers: Int,
    val topPerformers: List<GroupMemberInfo>, // 상위 3명
    val groupWeakTags: List<String>, // 그룹 전체 취약 태그 TOP 3
    val groupStrongTags: List<String>, // 그룹 전체 강점 태그 TOP 3
    val weeklyProgress: Map<String, Int>, // 최근 7일간 그룹 전체 진행도
    val memberActivityRate: Double // 활성 멤버 비율
)

/**
 * 스터디 그룹 대시보드 응답 데이터
 */
data class StudyGroupDashboardResponse(
    val groupId: String,
    val groupName: String,
    val memberCount: Int,
    val groupStats: StudyGroupStats,
    val memberDetails: List<GroupMemberInfo>,
    val cacheHit: Boolean = false,
    val responseTimeMs: Long = 0,
    val dataSource: String = "LIVE",
    val lastUpdated: LocalDateTime = LocalDateTime.now(),
    val message: String? = null
)