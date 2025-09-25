package com.algoreport.module.user

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 사용자 관리 서비스
 * TDD Refactor 단계: Repository 패턴 도입으로 데이터 접근 분리
 */
@Service
@Transactional(readOnly = true) // 클래스 레벨에 읽기 전용 트랜잭션 적용
class UserService(
    private val userRepository: UserRepository // JPA 리포지토리 주입
) {

    /**
     * 신규 사용자를 생성합니다.
     * 이메일이 중복될 경우 예외를 발생시킵니다.
     */
    @Transactional // 쓰기 작업에는 별도 트랜잭션 적용
    fun createUser(request: UserCreateRequest): User {
        if (userRepository.existsByEmail(request.email)) {
            throw CustomException(Error.DUPLICATE_EMAIL) // 예외 처리 추가
        }
        val user = User(
            email = request.email,
            nickname = request.nickname,
            provider = request.provider
        )
        return userRepository.save(user)
    }

    /**
     * 이메일로 사용자를 조회합니다.
     */
    fun findByEmail(email: String): User? {
        return userRepository.findByEmail(email)
    }

    /**
     * ID로 사용자를 조회합니다.
     */
    fun findById(userId: UUID): User {
        return userRepository.findById(userId)
            .orElseThrow { CustomException(Error.USER_NOT_FOUND) }
    }

    /**
     * 사용자의 solved.ac 정보를 업데이트합니다.
     */
    @Transactional
    fun updateSolvedacInfo(userId: UUID, solvedacHandle: String, tier: Int, solvedCount: Int): User {
        val user = findById(userId)

        val updatedUser = user.copy(
            solvedacHandle = solvedacHandle,
            solvedacTier = tier,
            solvedacSolvedCount = solvedCount,
            updatedAt = java.time.LocalDateTime.now()
        )
        return userRepository.save(updatedUser)
    }

    /**
     * solved.ac 핸들의 중복 여부를 확인합니다.
     */
    fun existsBySolvedacHandle(handle: String): Boolean {
        // 이 기능을 위해 UserRepository에 메소드 추가가 필요합니다.
        return userRepository.existsBySolvedacHandle(handle)
    }

    /**
     * 사용자를 삭제합니다.
     */
    @Transactional
    fun deleteUser(userId: UUID) {
        if (!userRepository.existsById(userId)) {
            throw CustomException(Error.USER_NOT_FOUND)
        }
        userRepository.deleteById(userId)
    }
}
