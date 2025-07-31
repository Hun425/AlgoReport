package com.algoreport.collector

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import java.time.LocalDateTime
import java.util.*

/**
 * 제출 동기화 서비스 테스트
 * 
 * 커버리지 향상을 위한 누락된 테스트 추가
 */
class SubmissionSyncServiceTest : BehaviorSpec({
    
    given("SubmissionSyncServiceImpl") {
        val submissionSyncService = SubmissionSyncServiceImpl()
        
        beforeEach {
            submissionSyncService.clear()
        }
        
        `when`("활성 사용자 ID 목록을 조회할 때") {
            then("하드코딩된 활성 사용자들을 반환해야 한다") {
                val activeUserIds = submissionSyncService.getActiveUserIds()
                
                activeUserIds shouldHaveSize 2
                activeUserIds.contains(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")) shouldBe true
                activeUserIds.contains(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")) shouldBe true
            }
        }
        
        `when`("등록되지 않은 사용자의 핸들을 조회할 때") {
            val userId = UUID.randomUUID()
            
            then("기본 핸들 형식을 반환해야 한다") {
                val handle = submissionSyncService.getUserHandle(userId)
                
                handle shouldContain "testuser"
                handle shouldContain userId.toString().takeLast(4)
            }
        }
        
        `when`("사용자 핸들을 설정한 후 조회할 때") {
            then("설정한 핸들을 반환해야 한다") {
                val userId = UUID.randomUUID()
                val expectedHandle = "customuser123"
                
                submissionSyncService.setUserHandle(userId, expectedHandle)
                val actualHandle = submissionSyncService.getUserHandle(userId)
                actualHandle shouldBe expectedHandle
            }
        }
        
        `when`("마지막 동기화 시간이 설정되지 않은 사용자를 조회할 때") {
            val userId = UUID.randomUUID()
            
            then("1시간 전 시간을 기본값으로 반환해야 한다") {
                val lastSyncTime = submissionSyncService.getLastSyncTime(userId)
                val oneHourAgo = LocalDateTime.now().minusHours(1)
                
                // 테스트 실행 시간 오차를 고려하여 범위로 검증
                lastSyncTime.isAfter(oneHourAgo.minusMinutes(1)) shouldBe true
                lastSyncTime.isBefore(oneHourAgo.plusMinutes(1)) shouldBe true
            }
        }
        
        `when`("마지막 동기화 시간을 업데이트할 때") {
            then("업데이트된 시간을 반환해야 한다") {
                val userId = UUID.randomUUID()
                val syncTime = LocalDateTime.now().minusMinutes(30)
                
                submissionSyncService.updateLastSyncTime(userId, syncTime)
                val retrievedTime = submissionSyncService.getLastSyncTime(userId)
                retrievedTime shouldBe syncTime
            }
        }
        
        `when`("여러 사용자의 핸들과 동기화 시간을 관리할 때") {
            then("각 사용자의 데이터가 독립적으로 관리되어야 한다") {
                val user1Id = UUID.randomUUID()
                val user2Id = UUID.randomUUID()
                val user1Handle = "user1handle"
                val user2Handle = "user2handle"
                val user1SyncTime = LocalDateTime.now().minusHours(2)
                val user2SyncTime = LocalDateTime.now().minusMinutes(30)
                
                submissionSyncService.setUserHandle(user1Id, user1Handle)
                submissionSyncService.setUserHandle(user2Id, user2Handle)
                submissionSyncService.updateLastSyncTime(user1Id, user1SyncTime)
                submissionSyncService.updateLastSyncTime(user2Id, user2SyncTime)
                
                submissionSyncService.getUserHandle(user1Id) shouldBe user1Handle
                submissionSyncService.getUserHandle(user2Id) shouldBe user2Handle
                submissionSyncService.getLastSyncTime(user1Id) shouldBe user1SyncTime
                submissionSyncService.getLastSyncTime(user2Id) shouldBe user2SyncTime
            }
        }
        
        `when`("clear() 메서드를 호출할 때") {
            val userId = UUID.randomUUID()
            val handle = "testhandle"
            val syncTime = LocalDateTime.now().minusMinutes(15)
            
            // 데이터 설정
            submissionSyncService.setUserHandle(userId, handle)
            submissionSyncService.updateLastSyncTime(userId, syncTime)
            
            // 초기화
            submissionSyncService.clear()
            
            then("모든 데이터가 초기화되어야 한다") {
                val retrievedHandle = submissionSyncService.getUserHandle(userId)
                val retrievedTime = submissionSyncService.getLastSyncTime(userId)
                
                // 초기화 후에는 기본값들이 반환되어야 함
                retrievedHandle shouldNotBe handle
                retrievedHandle shouldContain "testuser"
                
                // 기본 1시간 전 시간이 반환되어야 함
                val oneHourAgo = LocalDateTime.now().minusHours(1)
                retrievedTime.isAfter(oneHourAgo.minusMinutes(1)) shouldBe true
                retrievedTime.isBefore(oneHourAgo.plusMinutes(1)) shouldBe true
            }
        }
        
        `when`("동일한 사용자의 핸들을 여러 번 설정할 때") {
            then("마지막에 설정한 핸들이 반환되어야 한다") {
                val userId = UUID.randomUUID()
                val firstHandle = "firsthandle"
                val secondHandle = "secondhandle"
                
                submissionSyncService.setUserHandle(userId, firstHandle)
                submissionSyncService.setUserHandle(userId, secondHandle)
                
                val actualHandle = submissionSyncService.getUserHandle(userId)
                actualHandle shouldBe secondHandle
            }
        }
        
        `when`("동일한 사용자의 동기화 시간을 여러 번 업데이트할 때") {
            then("마지막에 업데이트한 시간이 반환되어야 한다") {
                val userId = UUID.randomUUID()
                val firstTime = LocalDateTime.now().minusHours(3)
                val secondTime = LocalDateTime.now().minusMinutes(45)
                
                submissionSyncService.updateLastSyncTime(userId, firstTime)
                submissionSyncService.updateLastSyncTime(userId, secondTime)
                
                val actualTime = submissionSyncService.getLastSyncTime(userId)
                actualTime shouldBe secondTime
            }
        }
        
        `when`("활성 사용자 목록을 여러 번 조회할 때") {
            then("일관된 결과를 반환해야 한다") {
                val firstCall = submissionSyncService.getActiveUserIds()
                val secondCall = submissionSyncService.getActiveUserIds()
                val thirdCall = submissionSyncService.getActiveUserIds()
                
                firstCall shouldBe secondCall
                secondCall shouldBe thirdCall
                firstCall shouldHaveSize 2
            }
        }
    }
})