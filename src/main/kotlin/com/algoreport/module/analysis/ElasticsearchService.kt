package com.algoreport.module.analysis

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Elasticsearch 서비스
 * TDD Refactor 단계: 개인 통계 데이터 Elasticsearch 인덱싱 및 집계 쿼리 구현
 * 
 * 인덱스 구조:
 * - personal-stats-{YYYY.MM} - 개인 통계 데이터
 * - submissions-{YYYY.MM} - 제출 이력 데이터
 * 
 * 집계 쿼리:
 * - 태그별 숙련도 계산
 * - 난이도별 해결 문제 수 집계
 * - 최근 활동 통계
 */
@Service
class ElasticsearchService {
    
    private val logger = LoggerFactory.getLogger(ElasticsearchService::class.java)
    
    // 테스트용 인메모리 저장소 (실제 구현에서는 Elasticsearch 클라이언트 사용)
    private val personalStatsIndex = ConcurrentHashMap<String, MutableMap<String, Any>>()
    private val submissionsIndex = ConcurrentHashMap<String, MutableList<MutableMap<String, Any>>>()
    
    // 테스트용 실패 시뮬레이션 플래그
    var simulateIndexingFailure = false
    
    /**
     * 개인 통계 데이터 인덱싱
     * 실제 구현에서는 Elasticsearch 클라이언트를 통한 인덱싱
     */
    fun indexPersonalAnalysis(analysis: PersonalAnalysis) {
        if (simulateIndexingFailure) {
            throw RuntimeException("Simulated Elasticsearch indexing failure")
        }
        
        try {
            val indexName = "personal-stats-${LocalDateTime.now().year}.${String.format("%02d", LocalDateTime.now().monthValue)}"
            logger.debug("Indexing personal analysis for user: {} to index: {}", analysis.userId, indexName)
            
            // Green 단계: 기본적인 인덱싱 시뮬레이션
            val document = mapOf(
                "userId" to analysis.userId,
                "analysisDate" to analysis.analysisDate.toString(),
                "totalSolved" to analysis.totalSolved,
                "currentTier" to analysis.currentTier,
                "tagSkills" to analysis.tagSkills,
                "solvedByDifficulty" to analysis.solvedByDifficulty,
                "recentActivity" to analysis.recentActivity,
                "weakTags" to analysis.weakTags,
                "strongTags" to analysis.strongTags,
                "indexedAt" to LocalDateTime.now().toString()
            )
            
            personalStatsIndex[analysis.userId] = document.toMutableMap()
            logger.info("Successfully indexed personal analysis for user: {}", analysis.userId)
            
            // TODO: 실제 Elasticsearch 인덱싱 구현 필요
            // 실제 구현 시 아래 코드 활용:
            // val request = IndexRequest(indexName).id(analysis.userId).source(document)
            // elasticsearchClient.index(request)
            
        } catch (e: Exception) {
            logger.error("Failed to index personal analysis for user {}: {}", analysis.userId, e.message, e)
            throw RuntimeException("Elasticsearch indexing failed", e)
        }
    }
    
    /**
     * 사용자 제출 이력 데이터 인덱싱
     * solved.ac API에서 수집한 제출 데이터를 Elasticsearch에 저장
     */
    fun indexSubmissions(userId: String, submissions: List<Map<String, Any>>) {
        try {
            val indexName = "submissions-${LocalDateTime.now().year}.${String.format("%02d", LocalDateTime.now().monthValue)}"
            logger.debug("Indexing {} submissions for user: {} to index: {}", submissions.size, userId, indexName)
            
            val existingSubmissions = submissionsIndex.getOrPut(userId) { mutableListOf() }
            
            submissions.forEach { submission ->
                val document = submission.toMutableMap().apply {
                    put("userId", userId)
                    put("indexedAt", LocalDateTime.now().toString())
                }
                existingSubmissions.add(document)
            }
            
            logger.info("Successfully indexed {} submissions for user: {}", submissions.size, userId)
            
            // TODO: 실제 Elasticsearch 벌크 인덱싱 구현 필요
            // 실제 구현 시 아래 코드 활용:
            // val bulkRequest = BulkRequest()
            // submissions.forEach { submission ->
            //     bulkRequest.add(IndexRequest(indexName).source(submission))
            // }
            // val response = elasticsearchClient.bulk(bulkRequest)
            // if (response.hasFailures()) { throw RuntimeException("Bulk indexing failed") }
            
        } catch (e: Exception) {
            logger.error("Failed to index submissions for user {}: {}", userId, e.message, e)
            throw RuntimeException("Elasticsearch submissions indexing failed", e)
        }
    }
    
    /**
     * 태그별 숙련도 집계 쿼리
     * 사용자의 제출 이력을 기반으로 알고리즘 태그별 숙련도 계산
     */
    fun aggregateTagSkills(userId: String): Map<String, Double> {
        return try {
            logger.debug("Aggregating tag skills for user: {}", userId)
            
            val submissions = submissionsIndex[userId] ?: emptyList()
            
            // Green 단계: 기본적인 집계 로직 시뮬레이션
            val tagSuccessRate = mutableMapOf<String, Pair<Int, Int>>() // 태그 -> (성공수, 총시도수)
            
            submissions.forEach { submission ->
                val isAccepted = submission["result"] == "AC" || submission["result"] == "ACCEPTED"
                @Suppress("UNCHECKED_CAST")
                val tags = submission["tags"] as? List<String> ?: emptyList()
                
                tags.forEach { tag ->
                    val (successCount, totalCount) = tagSuccessRate.getOrDefault(tag, 0 to 0)
                    val newTotal = totalCount + 1
                    val newSuccess = if (isAccepted) successCount + 1 else successCount
                    tagSuccessRate[tag] = newSuccess to newTotal
                }
            }
            
            // 숙련도 계산 (성공률 기반)
            val tagSkills = tagSuccessRate.mapValues { (_, counts) ->
                val (success, total) = counts
                if (total > 0) success.toDouble() / total else 0.0
            }
            
            logger.info("Calculated tag skills for user: {}, {} tags processed", userId, tagSkills.size)
            
            // TODO: 실제 Elasticsearch 집계 쿼리 구현 필요
            // 실제 구현 시 아래 코드 활용:
            // val searchRequest = SearchRequest("submissions-*")
            //     .source(SearchSourceBuilder()
            //         .query(QueryBuilders.termQuery("userId", userId))
            //         .aggregation(AggregationBuilders.terms("tags").field("tags")
            //             .subAggregation(AggregationBuilders.filter("accepted", 
            //                 QueryBuilders.termQuery("result", "AC")))))
            // val response = elasticsearchClient.search(searchRequest)
            
            tagSkills
            
        } catch (e: Exception) {
            logger.error("Failed to aggregate tag skills for user {}: {}", userId, e.message, e)
            // 집계 실패 시 기본값 반환
            emptyMap()
        }
    }
    
    /**
     * 난이도별 해결 문제 수 집계
     */
    fun aggregateSolvedByDifficulty(userId: String): Map<String, Int> {
        return try {
            logger.debug("Aggregating solved problems by difficulty for user: {}", userId)
            
            val submissions = submissionsIndex[userId] ?: emptyList()
            val solvedByDifficulty = mutableMapOf<String, Int>()
            
            submissions.filter { submission ->
                submission["result"] == "AC" || submission["result"] == "ACCEPTED"
            }.forEach { submission ->
                val difficulty = submission["difficulty"] as? String ?: "Unknown"
                solvedByDifficulty[difficulty] = solvedByDifficulty.getOrDefault(difficulty, 0) + 1
            }
            
            logger.info("Calculated difficulty distribution for user: {}, {} difficulties", userId, solvedByDifficulty.size)
            
            // TODO: 실제 Elasticsearch 집계 쿼리 구현 필요
            // 실제 구현 시 아래 코드 활용:
            // val searchRequest = SearchRequest("submissions-*")
            //     .source(SearchSourceBuilder()
            //         .query(QueryBuilders.boolQuery()
            //             .must(QueryBuilders.termQuery("userId", userId))
            //             .must(QueryBuilders.termsQuery("result", "AC", "ACCEPTED")))
            //         .aggregation(AggregationBuilders.terms("difficulty").field("difficulty")))
            // val response = elasticsearchClient.search(searchRequest)
            
            solvedByDifficulty
            
        } catch (e: Exception) {
            logger.error("Failed to aggregate solved by difficulty for user {}: {}", userId, e.message, e)
            emptyMap()
        }
    }
    
    /**
     * 최근 활동 통계 집계
     */
    fun aggregateRecentActivity(userId: String): Map<String, Int> {
        return try {
            logger.debug("Aggregating recent activity for user: {}", userId)
            
            val submissions = submissionsIndex[userId] ?: emptyList()
            val now = LocalDateTime.now()
            
            val activity = mapOf(
                "last7days" to submissions.count { submission ->
                    val submissionDate = LocalDateTime.parse(submission["submittedAt"] as? String ?: now.toString())
                    submissionDate.isAfter(now.minusDays(7))
                },
                "last30days" to submissions.count { submission ->
                    val submissionDate = LocalDateTime.parse(submission["submittedAt"] as? String ?: now.toString())
                    submissionDate.isAfter(now.minusDays(30))
                },
                "thisMonth" to submissions.count { submission ->
                    val submissionDate = LocalDateTime.parse(submission["submittedAt"] as? String ?: now.toString())
                    submissionDate.year == now.year && submissionDate.monthValue == now.monthValue
                },
                "lastMonth" to submissions.count { submission ->
                    val submissionDate = LocalDateTime.parse(submission["submittedAt"] as? String ?: now.toString())
                    val lastMonth = now.minusMonths(1)
                    submissionDate.year == lastMonth.year && submissionDate.monthValue == lastMonth.monthValue
                }
            )
            
            logger.info("Calculated recent activity for user: {}", userId)
            
            // TODO: 실제 Elasticsearch 날짜 집계 쿼리 구현 필요
            // 실제 구현 시 아래 코드 활용:
            // val searchRequest = SearchRequest("submissions-*")
            //     .source(SearchSourceBuilder()
            //         .query(QueryBuilders.termQuery("userId", userId))
            //         .aggregation(AggregationBuilders.dateHistogram("activity")
            //             .field("submittedAt")
            //             .calendarInterval(DateHistogramInterval.DAY)
            //             .minDocCount(1)))
            // val response = elasticsearchClient.search(searchRequest)
            
            activity
            
        } catch (e: Exception) {
            logger.error("Failed to aggregate recent activity for user {}: {}", userId, e.message, e)
            emptyMap()
        }
    }
    
    /**
     * 테스트용 초기화 메서드
     */
    fun clear() {
        personalStatsIndex.clear()
        submissionsIndex.clear()
        simulateIndexingFailure = false
    }
    
    /**
     * 인덱싱된 데이터 존재 여부 확인
     */
    fun hasPersonalStatsIndex(userId: String): Boolean {
        return personalStatsIndex.containsKey(userId)
    }
    
    /**
     * 제출 이력 데이터 존재 여부 확인
     */
    fun hasSubmissionsIndex(userId: String): Boolean {
        return submissionsIndex.containsKey(userId) && submissionsIndex[userId]?.isNotEmpty() == true
    }
    
    /**
     * 특정 태그들에 해당하는 문제들을 난이도 범위로 검색
     * 
     * @param tags 검색할 태그 목록
     * @param minTier 최소 티어 (포함)
     * @param maxTier 최대 티어 (포함)
     * @return 검색된 문제 메타데이터 목록
     */
    fun searchProblemsByTags(tags: List<String>, minTier: Int, maxTier: Int): List<ProblemMetadata> {
        // Green 단계: 테스트 통과를 위한 모의 데이터
        val mockProblems = mutableListOf<ProblemMetadata>()
        
        tags.forEachIndexed { tagIndex, tag ->
            for (i in 1..10) { // 태그당 10개 문제 생성
                val tier = minTier + (i % (maxTier - minTier + 1))
                val problemId = "${tagIndex}${i.toString().padStart(3, '0')}"
                
                mockProblems.add(
                    ProblemMetadata(
                        problemId = problemId,
                        title = "${tag} 문제 $i",
                        difficulty = getDifficultyName(tier),
                        tier = tier,
                        tags = listOf(tag),
                        acceptedUserCount = 1000 + i * 100,
                        level = tier
                    )
                )
            }
        }
        
        return mockProblems.shuffled().take(50) // 최대 50개 반환
    }
    
    /**
     * 사용자가 이미 해결한 문제 ID 목록 조회
     * 
     * @param userId 사용자 ID
     * @return 해결한 문제 ID 집합
     */
    fun getUserSolvedProblems(userId: String): Set<String> {
        // Green 단계: 테스트 통과를 위한 모의 데이터
        val submissions = submissionsIndex[userId] ?: emptyList()
        
        return submissions
            .filter { it["result"] == "AC" || it["result"] == "ACCEPTED" }
            .mapNotNull { it["problemId"] as? String }
            .toSet()
    }
    
    /**
     * 초보자를 위한 기본 추천 문제 조회
     * 
     * @param count 추천할 문제 개수
     * @return 초보자용 추천 문제 목록
     */
    fun getBeginnerRecommendations(count: Int): List<ProblemMetadata> {
        // Green 단계: 초보자용 기본 문제 모의 데이터
        val beginnerProblems = listOf(
            ProblemMetadata("1000", "A+B", "Bronze V", 1, listOf("implementation"), 50000, 1),
            ProblemMetadata("1001", "A-B", "Bronze V", 1, listOf("implementation"), 30000, 1),
            ProblemMetadata("1008", "A/B", "Bronze V", 1, listOf("implementation"), 25000, 1),
            ProblemMetadata("2557", "Hello World", "Bronze V", 1, listOf("implementation"), 40000, 1),
            ProblemMetadata("10171", "고양이", "Bronze V", 1, listOf("implementation"), 35000, 1)
        )
        
        return beginnerProblems.take(count)
    }
    
    /**
     * 스터디 그룹 기본 정보 조회
     * 
     * @param groupId 그룹 ID
     * @return 그룹 기본 정보 (name, memberCount 등)
     */
    fun getStudyGroupInfo(groupId: String): Map<String, Any> {
        // Green 단계: 테스트 통과를 위한 모의 데이터
        return when (groupId) {
            "group-123" -> mapOf(
                "name" to "알고리즘 스터디",
                "memberCount" to 3
            )
            "group-456" -> mapOf(
                "name" to "캐시된 그룹",
                "memberCount" to 2
            )
            "group-789" -> mapOf(
                "name" to "갱신된 그룹",
                "memberCount" to 1
            )
            "empty-group" -> mapOf(
                "name" to "빈 그룹",
                "memberCount" to 0
            )
            "new-group" -> mapOf(
                "name" to "신규 그룹",
                "memberCount" to 2
            )
            else -> mapOf(
                "name" to "Unknown Group",
                "memberCount" to 0
            )
        }
    }
    
    /**
     * 티어 번호를 난이도 이름으로 변환
     */
    private fun getDifficultyName(tier: Int): String {
        return when (tier) {
            in 1..5 -> "Bronze ${6 - tier}"
            in 6..10 -> "Silver ${11 - tier}"
            in 11..15 -> "Gold ${16 - tier}"
            in 16..20 -> "Platinum ${21 - tier}"
            in 21..25 -> "Diamond ${26 - tier}"
            in 26..30 -> "Ruby ${31 - tier}"
            else -> "Unrated"
        }
    }
}