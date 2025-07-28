package com.algoreport.module.user

import com.algoreport.collector.SolvedacApiClient
import com.algoreport.config.outbox.OutboxService
import org.springframework.stereotype.Service

/**
 * SOLVEDAC_LINK_SAGA 구현
 * TDD RED 단계: 컴파일 성공을 위한 빈 구현체
 */
@Service
class SolvedacLinkSaga(
    private val userService: UserService,
    private val solvedacApiClient: SolvedacApiClient,
    private val outboxService: OutboxService
) {
    
    fun start(request: SolvedacLinkRequest): SolvedacLinkResult {
        return try {
            // TODO: [GREEN] solved.ac API 호출 및 사용자 검증 로직 구현 필요
            // TODO: [GREEN] 사용자 프로필에 solved.ac 정보 업데이트 로직 구현 필요  
            // TODO: [GREEN] OutboxService를 통한 SOLVEDAC_LINKED 이벤트 발행 구현 필요
            // TODO: [REFACTOR] 중복 핸들 체크 및 보상 트랜잭션 로직 추가 필요
            
            // Step 1: 사용자 존재 여부 확인
            val user = userService.findById(request.userId)
                ?: return SolvedacLinkResult(
                    sagaStatus = SagaStatus.FAILED,
                    linkedHandle = null,
                    errorMessage = "사용자를 찾을 수 없습니다."
                )
            
            // Step 2: solved.ac 핸들 중복 체크
            if (userService.existsBySolvedacHandle(request.solvedacHandle)) {
                return SolvedacLinkResult(
                    sagaStatus = SagaStatus.FAILED,
                    linkedHandle = null,
                    errorMessage = "이미 다른 사용자가 연동한 핸들입니다."
                )
            }
            
            // Step 3: solved.ac API를 통한 핸들 유효성 검증
            val userInfo = try {
                solvedacApiClient.getUserInfo(request.solvedacHandle)
            } catch (e: Exception) {
                return SolvedacLinkResult(
                    sagaStatus = SagaStatus.FAILED,
                    linkedHandle = null,
                    errorMessage = "solved.ac에서 해당 핸들을 찾을 수 없습니다."
                )
            }
            
            // Step 4: 사용자 프로필에 solved.ac 정보 업데이트
            userService.updateSolvedacInfo(
                userId = request.userId,
                solvedacHandle = request.solvedacHandle,
                tier = userInfo.tier,
                solvedCount = userInfo.solvedCount
            )
            
            // Step 5: SOLVEDAC_LINKED 이벤트 발행
            outboxService.publishEvent(
                aggregateType = "USER",
                aggregateId = request.userId,
                eventType = "SOLVEDAC_LINKED",
                eventData = mapOf(
                    "userId" to request.userId,
                    "solvedacHandle" to request.solvedacHandle,
                    "tier" to userInfo.tier,
                    "solvedCount" to userInfo.solvedCount
                )
            )
            
            SolvedacLinkResult(
                sagaStatus = SagaStatus.COMPLETED,
                linkedHandle = request.solvedacHandle,
                errorMessage = null
            )
            
        } catch (exception: Exception) {
            SolvedacLinkResult(
                sagaStatus = SagaStatus.FAILED,
                linkedHandle = null,
                errorMessage = exception.message ?: "알 수 없는 오류가 발생했습니다."
            )
        }
    }
}