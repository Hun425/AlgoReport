package com.algoreport.module.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * 사용자 데이터 접근을 위한 Spring Data JPA 리포지토리
 */
@Repository
interface UserRepository : JpaRepository<User, UUID> {

    /**
     * 이메일로 사용자를 조회합니다.
     */
    fun findByEmail(email: String): User?

    /**
     * 해당 이메일의 사용자가 존재하는지 확인합니다.
     */
    fun existsByEmail(email: String): Boolean

    /**
     * 해당 solved.ac 핸들의 사용자가 존재하는지 확인합니다.
     */
    fun existsBySolvedacHandle(solvedacHandle: String): Boolean

    /**
     * solved.ac 핸들이 설정된 모든 사용자를 조회합니다.
     */
    fun findAllBySolvedacHandleIsNotNull(): List<User>

    // JpaRepository가 기본으로 제공하는 메소드들:
    // - save(user: User): User
    // - findById(id: UUID): Optional<User>
    // - existsById(id: UUID): Boolean
    // - count(): Long
    // - delete(user: User)
    // - findAll(): List<User>
}
