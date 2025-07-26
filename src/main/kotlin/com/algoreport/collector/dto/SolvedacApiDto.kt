package com.algoreport.collector.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/**
 * solved.ac API 응답 DTO 정의
 */

/**
 * 사용자 정보 응답
 */
data class UserInfo(
    @param:JsonProperty("handle")
    val handle: String,
    
    @param:JsonProperty("bio")
    val bio: String = "",
    
    @param:JsonProperty("organizations")
    val organizations: List<String> = emptyList(),
    
    @param:JsonProperty("badge")
    val badge: String? = null,
    
    @param:JsonProperty("background")
    val background: String = "default",
    
    @param:JsonProperty("profileImageUrl")
    val profileImageUrl: String? = null,
    
    @param:JsonProperty("solvedCount")
    val solvedCount: Int = 0,
    
    @param:JsonProperty("voteCount")
    val voteCount: Int = 0,
    
    @param:JsonProperty("class")
    val tier: Int = 0,
    
    @param:JsonProperty("classDecoration")
    val classDecoration: String = "none",
    
    @param:JsonProperty("rivalCount")
    val rivalCount: Int = 0,
    
    @param:JsonProperty("reverseRivalCount")
    val reverseRivalCount: Int = 0,
    
    @param:JsonProperty("maxStreak")
    val maxStreak: Int = 0,
    
    @param:JsonProperty("coins")
    val coins: Int = 0,
    
    @param:JsonProperty("stardusts")
    val stardusts: Int = 0,
    
    @param:JsonProperty("joinedAt")
    val joinedAt: LocalDateTime? = null,
    
    @param:JsonProperty("bannedUntil")
    val bannedUntil: LocalDateTime? = null,
    
    @param:JsonProperty("proUntil")
    val proUntil: LocalDateTime? = null,
    
    @param:JsonProperty("rank")
    val rank: Int = 0
)

/**
 * 제출 이력 응답
 */
data class SubmissionList(
    @param:JsonProperty("count")
    val count: Int,
    
    @param:JsonProperty("items")
    val items: List<Submission>
)

/**
 * 개별 제출 정보
 */
data class Submission(
    @param:JsonProperty("submissionId")
    val submissionId: Long,
    
    @param:JsonProperty("problem")
    val problem: ProblemSummary,
    
    @param:JsonProperty("user")
    val user: UserSummary,
    
    @param:JsonProperty("timestamp")
    val timestamp: LocalDateTime,
    
    @param:JsonProperty("result")
    val result: String,
    
    @param:JsonProperty("language")
    val language: String,
    
    @param:JsonProperty("codeLength")
    val codeLength: Int,
    
    @param:JsonProperty("runtime")
    val runtime: Int? = null,
    
    @param:JsonProperty("memory")
    val memory: Int? = null
)

/**
 * 문제 요약 정보
 */
data class ProblemSummary(
    @param:JsonProperty("problemId")
    val problemId: Int,
    
    @param:JsonProperty("titleKo")
    val titleKo: String,
    
    @param:JsonProperty("titles")
    val titles: List<Title>,
    
    @param:JsonProperty("level")
    val level: Int,
    
    @param:JsonProperty("acceptedUserCount")
    val acceptedUserCount: Int,
    
    @param:JsonProperty("averageTries")
    val averageTries: Double,
    
    @param:JsonProperty("tags")
    val tags: List<Tag>
)

/**
 * 문제 상세 정보
 */
data class ProblemInfo(
    @param:JsonProperty("problemId")
    val problemId: Int,
    
    @param:JsonProperty("titleKo")
    val titleKo: String,
    
    @param:JsonProperty("titles")
    val titles: List<Title>,
    
    @param:JsonProperty("level")
    val level: Int,
    
    @param:JsonProperty("acceptedUserCount")
    val acceptedUserCount: Int,
    
    @param:JsonProperty("averageTries")
    val averageTries: Double,
    
    @param:JsonProperty("tags")
    val tags: List<Tag>,
    
    @param:JsonProperty("metadata")
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 사용자 요약 정보
 */
data class UserSummary(
    @param:JsonProperty("handle")
    val handle: String,
    
    @param:JsonProperty("bio")
    val bio: String = "",
    
    @param:JsonProperty("profileImageUrl")
    val profileImageUrl: String? = null,
    
    @param:JsonProperty("solvedCount")
    val solvedCount: Int = 0,
    
    @param:JsonProperty("class")
    val tier: Int = 0
)

/**
 * 문제 제목 (다국어)
 */
data class Title(
    @param:JsonProperty("language")
    val language: String,
    
    @param:JsonProperty("languageDisplayName")
    val languageDisplayName: String,
    
    @param:JsonProperty("title")
    val title: String,
    
    @param:JsonProperty("isOriginal")
    val isOriginal: Boolean
)

/**
 * 문제 태그
 */
data class Tag(
    @param:JsonProperty("key")
    val key: String,
    
    @param:JsonProperty("isMeta")
    val isMeta: Boolean,
    
    @param:JsonProperty("bojTagId")
    val bojTagId: Int,
    
    @param:JsonProperty("problemCount")
    val problemCount: Int,
    
    @param:JsonProperty("displayNames")
    val displayNames: List<TagDisplayName>
)

/**
 * 태그 표시명 (다국어)
 */
data class TagDisplayName(
    @param:JsonProperty("language")
    val language: String,
    
    @param:JsonProperty("name")
    val name: String,
    
    @param:JsonProperty("short")
    val short: String
)