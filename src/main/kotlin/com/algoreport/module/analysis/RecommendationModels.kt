package com.algoreport.module.analysis

import java.time.LocalDateTime

/**
 * 문제 추천 요청 데이터
 */
data class RecommendationRequest(
    val userId: String,
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
    val userId: String,
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
    val userId: String,
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