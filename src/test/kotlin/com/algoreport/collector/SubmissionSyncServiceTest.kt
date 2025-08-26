package com.algoreport.collector

import com.algoreport.module.user.User
import com.algoreport.module.user.UserRepository
import com.algoreport.module.user.AuthProvider
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.*

/**
 * 제출 동기화 서비스 테스트
 * 
 * Phase 1.6: JPA Repository를 사용하는 단위 테스트로 변경
 */
class SubmissionSyncServiceTest : BehaviorSpec({
    
    given("SubmissionSyncServiceImpl") {
        val userRepository = mockk<UserRepository>()
        val dataSyncCheckpointRepository = mockk<DataSyncCheckpointRepository>()
        val submissionSyncService = SubmissionSyncServiceImpl(userRepository, dataSyncCheckpointRepository)
        
        
        `when`("활성 사용자 ID 목록을 조회할 때") {
            then("solved.ac 핸들이 설정된 사용자들을 반환해야 한다") {
                // Given
                val user1 = User(
                    id = UUID.randomUUID(),
                    email = "user1@test.com",
                    nickname = "사용자1",
                    provider = AuthProvider.GOOGLE,
                    solvedacHandle = "testuser1"
                )
                val user2 = User(
                    id = UUID.randomUUID(),
                    email = "user2@test.com", 
                    nickname = "사용자2",
                    provider = AuthProvider.GOOGLE,
                    solvedacHandle = "testuser2"
                )
                
                every { userRepository.findAllBySolvedacHandleIsNotNull() } returns listOf(user1, user2)
                
                // When
                val activeUserIds = submissionSyncService.getActiveUserIds()
                
                // Then
                activeUserIds shouldHaveSize 2
                activeUserIds.contains(user1.id) shouldBe true
                activeUserIds.contains(user2.id) shouldBe true
            }
        }
        
        `when`("사용자 핸들을 조회할 때") {
            then("DB에서 사용자 정보를 조회하여 핸들을 반환해야 한다") {
                // Given
                val userId = UUID.randomUUID()
                val expectedHandle = "testuser123"
                val user = User(
                    id = userId,
                    email = "test@example.com",
                    nickname = "테스트사용자",
                    provider = AuthProvider.GOOGLE,
                    solvedacHandle = expectedHandle
                )
                
                every { userRepository.findById(userId) } returns java.util.Optional.of(user)
                
                // When
                val actualHandle = submissionSyncService.getUserHandle(userId)
                
                // Then
                actualHandle shouldBe expectedHandle
            }
        }
        
        `when`("마지막 동기화 시간을 조회할 때") {
            then("체크포인트가 없으면 24시간 전 시간을 기본값으로 반환해야 한다") {
                // Given
                val userId = UUID.randomUUID()
                every { dataSyncCheckpointRepository.findTopByUserIdOrderByCheckpointAtDesc(userId) } returns null
                
                // When
                val lastSyncTime = submissionSyncService.getLastSyncTime(userId)
                val oneDayAgo = LocalDateTime.now().minusHours(24)
                
                // Then
                // 테스트 실행 시간 오차를 고려하여 범위로 검증
                lastSyncTime.isAfter(oneDayAgo.minusMinutes(1)) shouldBe true
                lastSyncTime.isBefore(oneDayAgo.plusMinutes(1)) shouldBe true
            }
        }
        
        `when`("체크포인트가 있는 사용자의 마지막 동기화 시간을 조회할 때") {
            then("체크포인트의 시간을 반환해야 한다") {
                // Given
                val userId = UUID.randomUUID()
                val checkpointTime = LocalDateTime.now().minusHours(2)
                val checkpoint = DataSyncCheckpoint(
                    syncJobId = UUID.randomUUID(),
                    userId = userId,
                    currentBatch = 1,
                    totalBatches = 5,
                    lastProcessedSubmissionId = 12345L,
                    collectedCount = 100,
                    checkpointAt = checkpointTime,
                    canResume = true
                )
                
                every { dataSyncCheckpointRepository.findTopByUserIdOrderByCheckpointAtDesc(userId) } returns checkpoint
                
                // When
                val lastSyncTime = submissionSyncService.getLastSyncTime(userId)
                
                // Then
                lastSyncTime shouldBe checkpointTime
            }
    }
})