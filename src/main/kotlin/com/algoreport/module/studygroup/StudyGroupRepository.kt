package com.algoreport.module.studygroup

/**
 * 스터디 그룹 데이터 접근 인터페이스
 * Repository 패턴으로 데이터 접근 로직 분리
 */
interface StudyGroupRepository {
    /**
     * 모든 활성 그룹 ID 조회
     */
    fun findAllActiveGroupIds(): List<String>
    
    /**
     * 그룹 존재 여부 확인
     */
    fun existsById(groupId: String): Boolean
    
    /**
     * 그룹 조회
     */
    fun findById(groupId: String): StudyGroup?
    
    /**
     * 그룹 멤버 ID 목록 조회
     */
    fun findGroupMemberIds(groupId: String): List<String>
    
    /**
     * 총 그룹 수 조회
     */
    fun count(): Long
}