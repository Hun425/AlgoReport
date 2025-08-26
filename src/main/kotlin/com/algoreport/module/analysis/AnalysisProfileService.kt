package com.algoreport.module.analysis

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.module.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 분석 프로필 관리 서비스
 */
@Service
@Transactional(readOnly = true)
class AnalysisProfileService(
    private val analysisProfileRepository: AnalysisProfileRepository,
    private val userRepository: UserRepository // User 엔티티를 조회하기 위해 필요
) {

    /**
     * 사용자의 분석 프로필을 생성합니다.
     */
    @Transactional
    fun createProfile(userId: UUID) {
        if (analysisProfileRepository.existsByUserId(userId)) {
            throw CustomException(Error.PROFILE_ALREADY_EXISTS)
        }
        val user = userRepository.findById(userId)
            .orElseThrow { CustomException(Error.USER_NOT_FOUND) }

        val profile = AnalysisProfile(
            id = UUID.randomUUID(),
            user = user
        )
        analysisProfileRepository.save(profile)
    }

    /**
     * 사용자에게 분석 프로필이 있는지 확인합니다.
     */
    fun hasProfile(userId: UUID): Boolean {
        return analysisProfileRepository.existsByUserId(userId)
    }

    /**
     * 사용자의 분석 프로필을 삭제합니다.
     */
    @Transactional
    fun deleteProfile(userId: UUID) {
        // 멱등성을 위해, 프로필이 존재할 때만 삭제를 시도합니다.
        if (!analysisProfileRepository.existsByUserId(userId)) {
            return
        }
        analysisProfileRepository.deleteByUserId(userId)
    }
}
