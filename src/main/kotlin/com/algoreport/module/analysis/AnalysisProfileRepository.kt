package com.algoreport.module.analysis

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface AnalysisProfileRepository : JpaRepository<AnalysisProfile, UUID> {

    fun findByUserId(userId: UUID): AnalysisProfile?

    fun existsByUserId(userId: UUID): Boolean

    @Transactional
    fun deleteByUserId(userId: UUID)
}
