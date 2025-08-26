package com.algoreport.module.notification

import com.algoreport.config.exception.CustomException
import com.algoreport.config.exception.Error
import com.algoreport.module.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 알림 설정 관리 서비스
 */
@Service
@Transactional(readOnly = true)
class NotificationSettingsService(
    private val notificationSettingsRepository: NotificationSettingsRepository,
    private val userRepository: UserRepository // User 엔티티를 조회하기 위해 필요
) {

    /**
     * 사용자의 기본 알림 설정을 생성합니다.
     */
    @Transactional
    fun createSettings(userId: UUID) {
        if (notificationSettingsRepository.existsByUserId(userId)) {
            // 이미 설정이 존재하면, 생성 로직을 건너뛰어 멱등성을 보장합니다.
            return
        }
        val user = userRepository.findById(userId)
            .orElseThrow { CustomException(Error.USER_NOT_FOUND) }

        val settings = NotificationSettings(
            id = UUID.randomUUID(),
            user = user
            // emailEnabled, dailySummaryEnabled는 엔티티의 기본값(true)을 사용합니다.
        )
        notificationSettingsRepository.save(settings)
    }

    /**
     * 사용자에게 알림 설정이 있는지 확인합니다.
     */
    fun hasSettings(userId: UUID): Boolean {
        return notificationSettingsRepository.existsByUserId(userId)
    }

    /**
     * 사용자의 알림 설정을 삭제합니다.
     */
    @Transactional
    fun deleteSettings(userId: UUID) {
        // 멱등성을 위해, 설정이 존재할 때만 삭제를 시도합니다.
        if (!notificationSettingsRepository.existsByUserId(userId)) {
            return
        }
        notificationSettingsRepository.deleteByUserId(userId)
    }
}
