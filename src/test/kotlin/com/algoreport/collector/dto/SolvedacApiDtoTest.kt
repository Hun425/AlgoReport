package com.algoreport.collector.dto

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime

/**
 * solved.ac API DTO 테스트
 *
 * 커버리지 향상을 위한 DTO 테스트 추가
 */
class SolvedacApiDtoTest : BehaviorSpec({
    
    given("UserInfo DTO") {
        `when`("기본값으로 생성할 때") {
            val userInfo = UserInfo(handle = "testuser")
            
            then("기본값들이 올바르게 설정되어야 한다") {
                userInfo.handle shouldBe "testuser"
                userInfo.bio shouldBe ""
                userInfo.organizations shouldBe emptyList()
                userInfo.badge shouldBe null
                userInfo.background shouldBe "default"
                userInfo.profileImageUrl shouldBe null
                userInfo.solvedCount shouldBe 0
                userInfo.voteCount shouldBe 0
                userInfo.tier shouldBe 0
                userInfo.classDecoration shouldBe "none"
                userInfo.rivalCount shouldBe 0
                userInfo.reverseRivalCount shouldBe 0
                userInfo.maxStreak shouldBe 0
                userInfo.coins shouldBe 0
                userInfo.stardusts shouldBe 0
                userInfo.joinedAt shouldBe null
                userInfo.bannedUntil shouldBe null
                userInfo.proUntil shouldBe null
                userInfo.rank shouldBe 0
            }
        }
        
        `when`("모든 필드를 설정할 때") {
            val joinedAt = LocalDateTime.of(2023, 1, 1, 0, 0, 0)
            val userInfo = UserInfo(
                handle = "advanced_user",
                bio = "알고리즘 문제 해결을 좋아합니다",
                organizations = listOf("TestOrg", "AlgoClub"),
                badge = "gold",
                background = "custom",
                profileImageUrl = "https://example.com/profile.jpg",
                solvedCount = 1500,
                voteCount = 200,
                tier = 5,
                classDecoration = "special",
                rivalCount = 10,
                reverseRivalCount = 15,
                maxStreak = 100,
                coins = 5000,
                stardusts = 300,
                joinedAt = joinedAt,
                bannedUntil = null,
                proUntil = joinedAt.plusYears(1),
                rank = 1000
            )
            
            then("모든 필드가 올바르게 설정되어야 한다") {
                userInfo.handle shouldBe "advanced_user"
                userInfo.bio shouldBe "알고리즘 문제 해결을 좋아합니다"
                userInfo.organizations shouldBe listOf("TestOrg", "AlgoClub")
                userInfo.badge shouldBe "gold"
                userInfo.background shouldBe "custom"
                userInfo.profileImageUrl shouldBe "https://example.com/profile.jpg"
                userInfo.solvedCount shouldBe 1500
                userInfo.voteCount shouldBe 200
                userInfo.tier shouldBe 5
                userInfo.classDecoration shouldBe "special"
                userInfo.rivalCount shouldBe 10
                userInfo.reverseRivalCount shouldBe 15
                userInfo.maxStreak shouldBe 100
                userInfo.coins shouldBe 5000
                userInfo.stardusts shouldBe 300
                userInfo.joinedAt shouldBe joinedAt
                userInfo.bannedUntil shouldBe null
                userInfo.proUntil shouldBe joinedAt.plusYears(1)
                userInfo.rank shouldBe 1000
            }
        }
        
        `when`("copy 메서드를 사용할 때") {
            val original = UserInfo(handle = "original")
            val copied = original.copy(handle = "copied", solvedCount = 100)
            
            then("지정된 필드만 변경되어야 한다") {
                copied.handle shouldBe "copied"
                copied.solvedCount shouldBe 100
                copied.bio shouldBe original.bio
                copied.tier shouldBe original.tier
            }
        }
        
        `when`("equals와 hashCode를 테스트할 때") {
            val user1 = UserInfo(handle = "test", solvedCount = 100)
            val user2 = UserInfo(handle = "test", solvedCount = 100)
            val user3 = UserInfo(handle = "different", solvedCount = 100)
            
            then("같은 내용이면 equals가 true이고 hashCode가 같아야 한다") {
                user1 shouldBe user2
                user1.hashCode() shouldBe user2.hashCode()
                user1 shouldNotBe user3
            }
        }
    }
    
    given("SubmissionList DTO") {
        `when`("빈 제출 목록을 생성할 때") {
            val submissionList = SubmissionList(count = 0, items = emptyList())
            
            then("count와 items가 올바르게 설정되어야 한다") {
                submissionList.count shouldBe 0
                submissionList.items shouldBe emptyList()
            }
        }
        
        `when`("제출 목록을 생성할 때") {
            val submission = createTestSubmission()
            val submissionList = SubmissionList(count = 1, items = listOf(submission))
            
            then("count와 items가 올바르게 설정되어야 한다") {
                submissionList.count shouldBe 1
                submissionList.items.size shouldBe 1
                submissionList.items[0] shouldBe submission
            }
        }
    }
    
    given("Submission DTO") {
        `when`("제출 정보를 생성할 때") {
            val submission = createTestSubmission()
            
            then("모든 필드가 올바르게 설정되어야 한다") {
                submission.submissionId shouldBe 12345L
                submission.result shouldBe "Accepted"
                submission.language shouldBe "Kotlin"
                submission.codeLength shouldBe 1000
                submission.runtime shouldBe 100
                submission.memory shouldBe 2048
            }
        }
        
        `when`("선택적 필드가 null인 제출을 생성할 때") {
            val submission = createTestSubmission().copy(runtime = null, memory = null)
            
            then("선택적 필드가 null이어야 한다") {
                submission.runtime shouldBe null
                submission.memory shouldBe null
            }
        }
    }
    
    given("ProblemSummary DTO") {
        `when`("문제 요약을 생성할 때") {
            val problemSummary = createTestProblemSummary()
            
            then("모든 필드가 올바르게 설정되어야 한다") {
                problemSummary.problemId shouldBe 1000
                problemSummary.titleKo shouldBe "A+B"
                problemSummary.level shouldBe 1
                problemSummary.acceptedUserCount shouldBe 100000
                problemSummary.averageTries shouldBe 1.5
                problemSummary.titles.size shouldBe 2
                problemSummary.tags.size shouldBe 1
            }
        }
    }
    
    given("ProblemInfo DTO") {
        `when`("문제 정보를 생성할 때") {
            val problemInfo = createTestProblemInfo()
            
            then("모든 필드가 올바르게 설정되어야 한다") {
                problemInfo.problemId shouldBe 1000
                problemInfo.titleKo shouldBe "A+B"
                problemInfo.level shouldBe 1
                problemInfo.acceptedUserCount shouldBe 100000
                problemInfo.averageTries shouldBe 1.5
                problemInfo.titles.size shouldBe 2
                problemInfo.tags.size shouldBe 1
                problemInfo.metadata shouldBe mapOf("difficulty" to "easy")
            }
        }
        
        `when`("기본 metadata로 생성할 때") {
            val problemInfo = createTestProblemInfo().copy(metadata = emptyMap())
            
            then("metadata가 빈 맵이어야 한다") {
                problemInfo.metadata shouldBe emptyMap()
            }
        }
    }
    
    given("UserSummary DTO") {
        `when`("기본값으로 생성할 때") {
            val userSummary = UserSummary(handle = "testuser")
            
            then("기본값들이 올바르게 설정되어야 한다") {
                userSummary.handle shouldBe "testuser"
                userSummary.bio shouldBe ""
                userSummary.profileImageUrl shouldBe null
                userSummary.solvedCount shouldBe 0
                userSummary.tier shouldBe 0
            }
        }
        
        `when`("모든 필드를 설정할 때") {
            val userSummary = UserSummary(
                handle = "advanced_user",
                bio = "알고리즘 전문가",
                profileImageUrl = "https://example.com/profile.jpg",
                solvedCount = 2000,
                tier = 6
            )
            
            then("모든 필드가 올바르게 설정되어야 한다") {
                userSummary.handle shouldBe "advanced_user"
                userSummary.bio shouldBe "알고리즘 전문가"
                userSummary.profileImageUrl shouldBe "https://example.com/profile.jpg"
                userSummary.solvedCount shouldBe 2000
                userSummary.tier shouldBe 6
            }
        }
    }
    
    given("Title DTO") {
        `when`("제목을 생성할 때") {
            val title = Title(
                language = "ko",
                languageDisplayName = "한국어",
                title = "A+B",
                isOriginal = true
            )
            
            then("모든 필드가 올바르게 설정되어야 한다") {
                title.language shouldBe "ko"
                title.languageDisplayName shouldBe "한국어"
                title.title shouldBe "A+B"
                title.isOriginal shouldBe true
            }
        }
        
        `when`("원본이 아닌 제목을 생성할 때") {
            val title = Title(
                language = "en",
                languageDisplayName = "English",
                title = "A+B",
                isOriginal = false
            )
            
            then("isOriginal이 false여야 한다") {
                title.isOriginal shouldBe false
            }
        }
    }
    
    given("Tag DTO") {
        `when`("태그를 생성할 때") {
            val tag = createTestTag()
            
            then("모든 필드가 올바르게 설정되어야 한다") {
                tag.key shouldBe "math"
                tag.isMeta shouldBe false
                tag.bojTagId shouldBe 124
                tag.problemCount shouldBe 5000
                tag.displayNames.size shouldBe 2
            }
        }
        
        `when`("메타 태그를 생성할 때") {
            val tag = createTestTag().copy(isMeta = true)
            
            then("isMeta가 true여야 한다") {
                tag.isMeta shouldBe true
            }
        }
    }
    
    given("TagDisplayName DTO") {
        `when`("태그 표시명을 생성할 때") {
            val tagDisplayName = TagDisplayName(
                language = "ko",
                name = "수학",
                short = "수학"
            )
            
            then("모든 필드가 올바르게 설정되어야 한다") {
                tagDisplayName.language shouldBe "ko"
                tagDisplayName.name shouldBe "수학"
                tagDisplayName.short shouldBe "수학"
            }
        }
        
        `when`("영어 태그 표시명을 생성할 때") {
            val tagDisplayName = TagDisplayName(
                language = "en",
                name = "Mathematics",
                short = "math"
            )
            
            then("모든 필드가 올바르게 설정되어야 한다") {
                tagDisplayName.language shouldBe "en"
                tagDisplayName.name shouldBe "Mathematics"
                tagDisplayName.short shouldBe "math"
            }
        }
    }
})

/**
 * 테스트용 헬퍼 함수들
 */
private fun createTestSubmission(): Submission {
    return Submission(
        submissionId = 12345L,
        problem = createTestProblemSummary(),
        user = UserSummary(handle = "testuser"),
        timestamp = LocalDateTime.of(2023, 1, 1, 12, 0, 0),
        result = "Accepted",
        language = "Kotlin",
        codeLength = 1000,
        runtime = 100,
        memory = 2048
    )
}

private fun createTestProblemSummary(): ProblemSummary {
    return ProblemSummary(
        problemId = 1000,
        titleKo = "A+B",
        titles = listOf(
            Title("ko", "한국어", "A+B", true),
            Title("en", "English", "A+B", false)
        ),
        level = 1,
        acceptedUserCount = 100000,
        averageTries = 1.5,
        tags = listOf(createTestTag())
    )
}

private fun createTestProblemInfo(): ProblemInfo {
    return ProblemInfo(
        problemId = 1000,
        titleKo = "A+B",
        titles = listOf(
            Title("ko", "한국어", "A+B", true),
            Title("en", "English", "A+B", false)
        ),
        level = 1,
        acceptedUserCount = 100000,
        averageTries = 1.5,
        tags = listOf(createTestTag()),
        metadata = mapOf("difficulty" to "easy")
    )
}

private fun createTestTag(): Tag {
    return Tag(
        key = "math",
        isMeta = false,
        bojTagId = 124,
        problemCount = 5000,
        displayNames = listOf(
            TagDisplayName("ko", "수학", "수학"),
            TagDisplayName("en", "Mathematics", "math")
        )
    )
}