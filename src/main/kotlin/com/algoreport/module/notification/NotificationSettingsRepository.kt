package com.algoreport.module.notification

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface NotificationSettingsRepository : JpaRepository<NotificationSettings, UUID> {

    fun findByUserId(userId: UUID): NotificationSettings?

    fun existsByUserId(userId: UUID): Boolean

    @Transactional
    fun deleteByUserId(userId: UUID)
}
