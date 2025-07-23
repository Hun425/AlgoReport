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
    @JsonProperty("handle")
    val handle: String,
    
    @JsonProperty("bio")
    val bio: String = "",
    
    @JsonProperty("organizations")
    val organizations: List<String> = emptyList(),
    
    @JsonProperty("badge")
    val badge: String? = null,
    
    @JsonProperty("background")
    val background: String = "default",
    
    @JsonProperty("profileImageUrl")
    val profileImageUrl: String? = null,
    
    @JsonProperty("solvedCount")
    val solvedCount: Int = 0,
    
    @JsonProperty("voteCount")
    val voteCount: Int = 0,
    
    @JsonProperty("class")
    val tier: Int = 0,
    
    @JsonProperty("classDecoration")
    val classDecoration: String = "none",
    
    @JsonProperty("rivalCount")
    val rivalCount: Int = 0,
    
    @JsonProperty("reverseRivalCount")
    val reverseRivalCount: Int = 0,
    
    @JsonProperty("maxStreak")
    val maxStreak: Int = 0,
    
    @JsonProperty("coins")
    val coins: Int = 0,
    
    @JsonProperty("stardusts")
    val stardusts: Int = 0,
    
    @JsonProperty("joinedAt")
    val joinedAt: LocalDateTime? = null,
    
    @JsonProperty("bannedUntil")
    val bannedUntil: LocalDateTime? = null,
    
    @JsonProperty("proUntil")
    val proUntil: LocalDateTime? = null,
    
    @JsonProperty("rank")
    val rank: Int = 0
)

/**
 * 제출 이력 응답
 */
data class SubmissionList(
    @JsonProperty("count")
    val count: Int,
    
    @JsonProperty("items")
    val items: List<Submission>
)

/**
 * 개별 제출 정보
 */
data class Submission(
    @JsonProperty("submissionId")
    val submissionId: Long,
    
    @JsonProperty("problem")
    val problem: ProblemSummary,
    
    @JsonProperty("user")
    val user: UserSummary,
    
    @JsonProperty("timestamp")
    val timestamp: LocalDateTime,
    
    @JsonProperty("result")
    val result: String,
    
    @JsonProperty("language")
    val language: String,
    
    @JsonProperty("codeLength")
    val codeLength: Int,
    
    @JsonProperty("runtime")
    val runtime: Int? = null,
    
    @JsonProperty("memory")
    val memory: Int? = null
)

/**
 * 문제 요약 정보
 */
data class ProblemSummary(
    @JsonProperty("problemId")
    val problemId: Int,
    
    @JsonProperty("titleKo")
    val titleKo: String,
    
    @JsonProperty("titles")
    val titles: List<Title>,
    
    @JsonProperty("level")
    val level: Int,
    
    @JsonProperty("acceptedUserCount")
    val acceptedUserCount: Int,
    
    @JsonProperty("averageTries")
    val averageTries: Double,
    
    @JsonProperty("tags")
    val tags: List<Tag>
)

/**
 * 문제 상세 정보
 */
data class ProblemInfo(
    @JsonProperty("problemId")
    val problemId: Int,
    
    @JsonProperty("titleKo")
    val titleKo: String,
    
    @JsonProperty("titles")
    val titles: List<Title>,
    
    @JsonProperty("level")
    val level: Int,
    
    @JsonProperty("acceptedUserCount")
    val acceptedUserCount: Int,
    
    @JsonProperty("averageTries")
    val averageTries: Double,
    
    @JsonProperty("tags")
    val tags: List<Tag>,
    
    @JsonProperty("metadata")
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 사용자 요약 정보
 */
data class UserSummary(
    @JsonProperty("handle")
    val handle: String,
    
    @JsonProperty("bio")
    val bio: String = "",
    
    @JsonProperty("profileImageUrl")
    val profileImageUrl: String? = null,
    
    @JsonProperty("solvedCount")
    val solvedCount: Int = 0,
    
    @JsonProperty("class")
    val tier: Int = 0
)

/**
 * 문제 제목 (다국어)
 */
data class Title(
    @JsonProperty("language")
    val language: String,
    
    @JsonProperty("languageDisplayName")
    val languageDisplayName: String,
    
    @JsonProperty("title")
    val title: String,
    
    @JsonProperty("isOriginal")
    val isOriginal: Boolean
)

/**
 * 문제 태그
 */
data class Tag(
    @JsonProperty("key")
    val key: String,
    
    @JsonProperty("isMeta")
    val isMeta: Boolean,
    
    @JsonProperty("bojTagId")
    val bojTagId: Int,
    
    @JsonProperty("problemCount")
    val problemCount: Int,
    
    @JsonProperty("displayNames")
    val displayNames: List<TagDisplayName>
)

/**
 * 태그 표시명 (다국어)
 */
data class TagDisplayName(
    @JsonProperty("language")
    val language: String,
    
    @JsonProperty("name")
    val name: String,
    
    @JsonProperty("short")
    val short: String
)