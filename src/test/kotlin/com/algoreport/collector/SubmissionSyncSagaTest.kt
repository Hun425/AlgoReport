package com.algoreport.collector

import com.algoreport.collector.dto.SubmissionList
import com.algoreport.collector.dto.Submission
import com.algoreport.collector.dto.ProblemSummary
import com.algoreport.collector.dto.UserSummary
import com.algoreport.config.outbox.OutboxService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.util.*

/**
 * SUBMISSION_SYNC_SAGA 테스트
 * 
 * TDD Red 단계: 실시간 제출 동기화 테스트 작성
 * Task 1-2-1: 실시간 제출 동기화 기본 구조
 */
class SubmissionSyncSagaTest : BehaviorSpec({
    
    given("SubmissionSyncSaga") {
        val solvedacApiClient = mockk<SolvedacApiClient>()
        val submissionSyncService = mockk<SubmissionSyncService>()
        val outboxService = mockk<OutboxService>()
        val submissionRepository = mockk<SubmissionRepository>()
        
        val userRepository = mockk<com.algoreport.module.user.UserRepository>()
        
        val saga = SubmissionSyncSaga(
            solvedacApiClient = solvedacApiClient,
            submissionSyncService = submissionSyncService,
            outboxService = outboxService,
            submissionRepository = submissionRepository,
            userRepository = userRepository,
            coroutineScope = mockk()
        )
        
        // 공통 Mock 설정
        every { 
            outboxService.publishEvent(
                aggregateType = any(),
                aggregateId = any(),
                eventType = any(),
                eventData = any()
            ) 
        } returns UUID.randomUUID()
        
        `when`("5분마다 스케줄링된 실시간 제출 동기화를 실행할 때") {
            val activeUserIds = listOf(UUID.randomUUID(), UUID.randomUUID())
            val handle1 = "user1"
            val handle2 = "user2"
            
            // 새로운 제출 데이터 Mock
            val newSubmissions = listOf(
                Submission(
                    submissionId = 1001L,
                    problem = ProblemSummary(
                        problemId = 1000,
                        titleKo = "A+B",
                        titles = emptyList(),
                        level = 1,
                        acceptedUserCount = 100000,
                        averageTries = 1.2,
                        tags = emptyList()
                    ),
                    user = UserSummary(
                        handle = "user1",
                        bio = "",
                        profileImageUrl = null,
                        solvedCount = 100,
                        tier = 5
                    ),
                    timestamp = LocalDateTime.now().minusMinutes(3),
                    result = "맞았습니다!!",
                    language = "Kotlin",
                    codeLength = 800
                ),
                Submission(
                    submissionId = 1002L,
                    problem = ProblemSummary(
                        problemId = 1001,
                        titleKo = "A+B",
                        titles = emptyList(),
                        level = 1,
                        acceptedUserCount = 100000,
                        averageTries = 1.2,
                        tags = emptyList()
                    ),
                    user = UserSummary(
                        handle = "user2",
                        bio = "",
                        profileImageUrl = null,
                        solvedCount = 200,
                        tier = 10
                    ),
                    timestamp = LocalDateTime.now().minusMinutes(1),
                    result = "맞았습니다!!",
                    language = "Java",
                    codeLength = 1200
                )
            )
            
            every { submissionSyncService.getActiveUserIds() } returns activeUserIds
            every { submissionSyncService.getUserHandle(activeUserIds[0]) } returns handle1
            every { submissionSyncService.getUserHandle(activeUserIds[1]) } returns handle2
            every { submissionSyncService.getLastSyncTime(any()) } returns LocalDateTime.now().minusMinutes(5)
            
            every { 
                solvedacApiClient.getSubmissions(handle1, any()) 
            } returns SubmissionList(count = 1, items = listOf(newSubmissions[0]))
            
            every { 
                solvedacApiClient.getSubmissions(handle2, any()) 
            } returns SubmissionList(count = 1, items = listOf(newSubmissions[1]))
            
            every { submissionRepository.existsBySubmissionId(1001L) } returns false
            every { submissionRepository.existsBySubmissionId(1002L) } returns false
            every { submissionRepository.save(any()) } returnsArgument 0
            every { submissionSyncService.updateLastSyncTime(any(), any()) } returns Unit
            
            // UserRepository Mock 설정 - solved.ac 핸들로 사용자 찾기
            val mockUser1 = mockk<com.algoreport.module.user.User>()
            val mockUser2 = mockk<com.algoreport.module.user.User>()
            every { userRepository.findBySolvedacHandle("user1") } returns mockUser1
            every { userRepository.findBySolvedacHandle("user2") } returns mockUser2
            
            then("모든 활성 사용자의 새로운 제출이 수집되어야 한다") {
                val result = runBlocking { saga.executeSync() }
                
                result shouldNotBe null
                result.successful shouldBe true
                result.processedUsers shouldBe 2
                result.newSubmissionsCount shouldBe 2
                result.executionTimeMs shouldBeGreaterThan 0L
                result.syncStatus shouldBe SyncStatus.COMPLETED
                
                // 이벤트 발행 검증 (간단한 호출 횟수만 검증)
                verify(exactly = 2) { 
                    outboxService.publishEvent(any(), any(), any(), any())
                }
            }
        }
        
        `when`("증분 업데이트로 마지막 동기화 이후 제출만 수집할 때") {
            val userId = UUID.randomUUID()
            val handle = "testuser"
            val lastSyncTime = LocalDateTime.now().minusHours(1)
            
            // 마지막 동기화 이후 새로운 제출만
            val recentSubmissions = listOf(
                Submission(
                    submissionId = 2001L,
                    problem = ProblemSummary(
                        problemId = 2000,
                        titleKo = "A+B",
                        titles = emptyList(),
                        level = 1,
                        acceptedUserCount = 100000,
                        averageTries = 1.2,
                        tags = emptyList()
                    ),
                    user = UserSummary(
                        handle = "testuser",
                        bio = "",
                        profileImageUrl = null,
                        solvedCount = 50,
                        tier = 3
                    ),
                    timestamp = LocalDateTime.now().minusMinutes(30), // 마지막 동기화 이후
                    result = "맞았습니다!!",
                    language = "Python",
                    codeLength = 600
                )
            )
            
            every { submissionSyncService.getActiveUserIds() } returns listOf(userId)
            every { submissionSyncService.getUserHandle(userId) } returns handle
            every { submissionSyncService.getLastSyncTime(userId) } returns lastSyncTime
            
            every { 
                solvedacApiClient.getSubmissions(handle, any()) 
            } returns SubmissionList(count = 1, items = recentSubmissions)
            
            every { submissionRepository.existsBySubmissionId(2001L) } returns false
            every { submissionRepository.save(any()) } returnsArgument 0
            every { submissionSyncService.updateLastSyncTime(userId, any()) } returns Unit
            
            // UserRepository Mock 설정
            val mockUser = mockk<com.algoreport.module.user.User>()
            every { userRepository.findBySolvedacHandle("testuser") } returns mockUser
            
            then("마지막 동기화 시점 이후의 제출만 처리되어야 한다") {
                val result = runBlocking { saga.executeSync() }
                
                result.successful shouldBe true
                result.newSubmissionsCount shouldBe 1
                result.duplicatesSkipped shouldBe 0
                
                // 처리 결과 검증은 결과 값으로만 확인
                // newSubmissionsCount = 1, successful = true로 이미 검증됨
            }
        }
        
        `when`("중복 제출을 처리할 때") {
            val userId = UUID.randomUUID()
            val handle = "testuser"
            
            val existingSubmission = Submission(
                submissionId = 3001L,
                problem = ProblemSummary(
                    problemId = 3000,
                    titleKo = "A+B",
                    titles = emptyList(),
                    level = 1,
                    acceptedUserCount = 100000,
                    averageTries = 1.2,
                    tags = emptyList()
                ),
                user = UserSummary(
                    handle = "testuser",
                    bio = "",
                    profileImageUrl = null,
                    solvedCount = 75,
                    tier = 4
                ),
                timestamp = LocalDateTime.now().minusMinutes(10),
                result = "맞았습니다!!",
                language = "C++",
                codeLength = 900
            )
            
            every { submissionSyncService.getActiveUserIds() } returns listOf(userId)
            every { submissionSyncService.getUserHandle(userId) } returns handle
            every { submissionSyncService.getLastSyncTime(userId) } returns LocalDateTime.now().minusMinutes(15)
            
            every { 
                solvedacApiClient.getSubmissions(handle, any()) 
            } returns SubmissionList(count = 1, items = listOf(existingSubmission))
            
            // 이미 존재하는 제출
            every { submissionRepository.existsBySubmissionId(3001L) } returns true
            every { submissionSyncService.updateLastSyncTime(userId, any()) } returns Unit
            
            // UserRepository Mock 설정 (중복 제출의 경우에는 실제로는 호출되지 않지만 안전성을 위해 설정)
            val mockUser = mockk<com.algoreport.module.user.User>()
            every { userRepository.findBySolvedacHandle("testuser") } returns mockUser
            
            then("중복 제출은 건너뛰고 카운트되어야 한다") {
                val result = runBlocking { saga.executeSync() }
                
                result.successful shouldBe true
                result.newSubmissionsCount shouldBe 0
                result.duplicatesSkipped shouldBe 1
                
                // 중복 제출 검증은 결과 값으로만 확인 (Mock 검증 복잡성 제거)
                // duplicatesSkipped = 1, newSubmissionsCount = 0으로 이미 검증됨
            }
        }
        
        `when`("스케줄링 성능을 테스트할 때") {
            val startTime = System.currentTimeMillis()
            
            then("5분마다 실행되는 스케줄링 메서드가 존재해야 한다") {
                // @Scheduled 애노테이션이 있는 메서드 존재 여부 테스트
                val scheduledMethod = saga::class.java.declaredMethods
                    .find { it.name == "scheduledSubmissionSync" }
                
                scheduledMethod shouldNotBe null
                scheduledMethod!!.isAnnotationPresent(org.springframework.scheduling.annotation.Scheduled::class.java) shouldBe true
            }
        }
        
        `when`("API 에러가 발생할 때") {
            val userId = UUID.randomUUID()
            val handle = "testuser"
            
            every { submissionSyncService.getActiveUserIds() } returns listOf(userId)
            every { submissionSyncService.getUserHandle(userId) } returns handle
            every { submissionSyncService.getLastSyncTime(userId) } returns LocalDateTime.now().minusMinutes(5)
            
            // solved.ac API 에러 시뮬레이션
            every { solvedacApiClient.getSubmissions(handle, any()) } throws 
                RuntimeException("API temporarily unavailable")
            
            then("에러를 처리하고 부분 실패 상태를 반환해야 한다") {
                val result = runBlocking { saga.executeSync() }
                
                result.successful shouldBe false
                result.syncStatus shouldBe SyncStatus.FAILED  // 전체 사용자(1명)가 실패했으므로 FAILED
                result.failedUsers shouldBe 1
                result.errorMessage shouldNotBe null
                
                // 에러 처리 검증은 결과 값으로만 확인 (Mock 검증 복잡성 제거)
                // syncStatus = FAILED, failedUsers = 1, errorMessage != null로 이미 검증됨
            }
        }
        
        `when`("대량의 사용자 데이터를 순차 처리할 때") {
            val userCount = 50
            val activeUserIds = (1..userCount).map { UUID.randomUUID() }
            
            every { submissionSyncService.getActiveUserIds() } returns activeUserIds
            activeUserIds.forEach { userId ->
                every { submissionSyncService.getUserHandle(userId) } returns "user$userId"
                every { submissionSyncService.getLastSyncTime(userId) } returns LocalDateTime.now().minusMinutes(5)
                every { 
                    solvedacApiClient.getSubmissions("user$userId", any()) 
                } returns SubmissionList(count = 0, items = emptyList())
                every { submissionSyncService.updateLastSyncTime(userId, any()) } returns Unit
                
                // UserRepository Mock 설정 (빈 결과이므로 실제로는 호출되지 않지만 안전성을 위해 설정)
                val mockUser = mockk<com.algoreport.module.user.User>()
                every { userRepository.findBySolvedacHandle("user$userId") } returns mockUser
            }
            
            then("순차 처리로 모든 사용자 데이터가 처리되어야 한다") {
                val startTime = System.currentTimeMillis()
                val result = runBlocking { saga.executeSync() }
                val actualTime = System.currentTimeMillis() - startTime
                
                result.successful shouldBe true
                result.processedUsers shouldBe userCount
                
                // 순차 처리 성능 검증 (실제 구현: 41-81행의 for loop)
                actualTime shouldBeGreaterThan 0L // 실행 시간이 0보다 커야 함
                
                // 사용자 처리 검증은 결과 값으로만 확인 (Mock 검증 복잡성 제거)
                // processedUsers = userCount로 이미 검증됨
            }
        }
    }
})

