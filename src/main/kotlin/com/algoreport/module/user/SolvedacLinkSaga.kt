package com.algoreport.module.user

import com.algoreport.collector.SolvedacApiClient
import com.algoreport.collector.dto.UserInfo
import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.config.outbox.OutboxService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * SOLVEDAC_LINK_SAGA 구현
 * 
 * 사용자의 solved.ac 계정을 연동하는 SAGA 패턴 구현
 * - 보상 트랜잭션을 통한 데이터 일관성 보장
 * - 단계별 실패 시 자동 롤백
 * - 구조화된 예외 처리
 */
@Service
class SolvedacLinkSaga(
    private val userService: UserService,
    private val solvedacApiClient: SolvedacApiClient,
    private val outboxService: OutboxService
) {
    
    private val logger = LoggerFactory.getLogger(SolvedacLinkSaga::class.java)
    
    fun start(request: SolvedacLinkRequest): SolvedacLinkResult {
        logger.info("SOLVEDAC_LINK_SAGA 시작 - userId: ${request.userId}, handle: ${request.solvedacHandle}")
        
        // 보상 트랜잭션을 위한 원본 상태 저장
        var compensationNeeded = false
        var originalUser: User? = null
        
        return try {
            // Step 1: 사용자 존재 여부 확인
            val user = validateUserExists(request.userId)
            originalUser = user
            logger.debug("Step 1 완료 - 사용자 검증 성공: ${user.email}")
            
            // Step 2: 중복 핸들 체크
            validateHandleNotDuplicated(request.solvedacHandle)
            logger.debug("Step 2 완료 - 핸들 중복 체크 통과")
            
            // Step 3: solved.ac API 검증
            val userInfo = validateSolvedacHandle(request.solvedacHandle)
            logger.debug("Step 3 완료 - solved.ac API 검증 성공 (tier: ${userInfo.tier})")
            
            // Step 4: 사용자 프로필 업데이트 (보상 트랜잭션 대상)
            compensationNeeded = true
            updateUserProfile(request.userId, request.solvedacHandle, userInfo)
            logger.debug("Step 4 완료 - 사용자 프로필 업데이트 성공")
            
            // Step 5: 이벤트 발행
            publishLinkingEvent(request.userId, request.solvedacHandle, userInfo)
            logger.info("SOLVEDAC_LINK_SAGA 완료 - 모든 단계 성공")
            
            SolvedacLinkResult(
                sagaStatus = SagaStatus.COMPLETED,
                linkedHandle = request.solvedacHandle,
                errorMessage = null
            )
            
        } catch (exception: Exception) {
            logger.error("SOLVEDAC_LINK_SAGA 실패 - userId: ${request.userId}", exception)
            
            // 보상 트랜잭션 실행
            if (compensationNeeded && originalUser != null) {
                executeCompensation(originalUser)
            }
            
            handleSagaFailure(exception)
        }
    }
    
    /**
     * Step 1: 사용자 존재 여부 확인
     */
    private fun validateUserExists(userId: UUID): User {
        return userService.findById(userId)
    }
    
    /**
     * Step 2: solved.ac 핸들 중복 체크
     */
    private fun validateHandleNotDuplicated(solvedacHandle: String) {
        if (userService.existsBySolvedacHandle(solvedacHandle)) {
            throw CustomException(Error.ALREADY_LINKED_SOLVEDAC_HANDLE)
        }
    }
    
    /**
     * Step 3: solved.ac API를 통한 핸들 유효성 검증
     */
    private fun validateSolvedacHandle(solvedacHandle: String): UserInfo {
        return try {
            solvedacApiClient.getUserInfo(solvedacHandle)
        } catch (e: Exception) {
            logger.warn("solved.ac API 호출 실패 - handle: $solvedacHandle", e)
            throw CustomException(Error.SOLVEDAC_USER_NOT_FOUND)
        }
    }
    
    /**
     * Step 4: 사용자 프로필에 solved.ac 정보 업데이트
     */
    private fun updateUserProfile(userId: UUID, solvedacHandle: String, userInfo: UserInfo): User {
        return try {
            userService.updateSolvedacInfo(
                userId = userId,
                solvedacHandle = solvedacHandle,
                tier = userInfo.tier,
                solvedCount = userInfo.solvedCount
            )
        } catch (ex: Exception) {
            logger.error("사용자 프로필 업데이트 실패 - userId: $userId", ex)
            throw CustomException(Error.USER_UPDATE_FAILED)
        }
    }
    
    /**
     * Step 5: SOLVEDAC_LINKED 이벤트 발행
     */
    private fun publishLinkingEvent(userId: UUID, solvedacHandle: String, userInfo: UserInfo) {
        try {
            outboxService.publishEvent(
                aggregateType = "USER",
                aggregateId = userId.toString(),
                eventType = "SOLVEDAC_LINKED",
                eventData = mapOf(
                    "userId" to userId.toString(),
                    "solvedacHandle" to solvedacHandle,
                    "tier" to userInfo.tier,
                    "solvedCount" to userInfo.solvedCount
                )
            )
        } catch (e: Exception) {
            logger.error("이벤트 발행 실패 - userId: $userId", e)
            throw CustomException(Error.EVENT_PUBLISH_FAILED)
        }
    }
    
    /**
     * 보상 트랜잭션: 실패 시 원본 상태로 롤백
     */
    private fun executeCompensation(originalUser: User) {
        try {
            logger.info("보상 트랜잭션 실행 - userId: ${originalUser.id}")
            
            // 사용자 프로필을 원본 상태로 롤백
            userService.updateSolvedacInfo(
                userId = originalUser.id,
                solvedacHandle = originalUser.solvedacHandle ?: "",
                tier = originalUser.solvedacTier ?: 0,
                solvedCount = originalUser.solvedacSolvedCount ?: 0
            )
            
            logger.info("보상 트랜잭션 완료 - 원본 상태로 롤백")
        } catch (e: Exception) {
            logger.error("보상 트랜잭션 실패 - userId: ${originalUser.id}", e)
            // 보상 트랜잭션 실패는 별도 알림이나 수동 처리가 필요
        }
    }
    
    /**
     * SAGA 실패 처리
     */
    private fun handleSagaFailure(exception: Exception): SolvedacLinkResult {
        return when (exception) {
            is CustomException -> SolvedacLinkResult(
                sagaStatus = SagaStatus.FAILED,
                linkedHandle = null,
                errorMessage = exception.error.message
            )
            else -> SolvedacLinkResult(
                sagaStatus = SagaStatus.FAILED,
                linkedHandle = null,
                errorMessage = "알 수 없는 오류가 발생했습니다."
            )
        }
    }
}