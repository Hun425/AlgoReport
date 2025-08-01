package com.algoreport.module.user

/**
 * 사용자 데이터 접근 인터페이스
 * Repository 패턴으로 데이터 접근 로직 분리
 */
interface UserRepository {
    /**
     * 모든 활성 사용자 ID 조회
     */
    fun findAllActiveUserIds(): List<String>
    
    /**
     * 사용자 존재 여부 확인
     */
    fun existsById(userId: String): Boolean
    
    /**
     * 사용자 조회
     */
    fun findById(userId: String): User?
    
    /**
     * 총 사용자 수 조회
     */
    fun count(): Long
}