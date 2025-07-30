package com.algoreport.module.studygroup

import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 스터디 그룹 관리 서비스
 * TDD Green 단계: 기본 기능만 구현 (실제 DB 연동은 추후)
 */
@Service
class StudyGroupService {
    
    // 테스트용 인메모리 저장소
    private val studyGroups = ConcurrentHashMap<String, StudyGroup>()
    private val nameIndex = ConcurrentHashMap<String, String>() // name -> groupId
    private val groupMembers = ConcurrentHashMap<String, MutableSet<String>>() // groupId -> Set<userId>
    
    companion object {
        const val MAX_GROUP_CAPACITY = 20
    }
    
    fun createGroup(request: CreateGroupRequest): StudyGroup {
        val groupId = UUID.randomUUID().toString()
        val group = StudyGroup(
            id = groupId,
            name = request.name,
            description = request.description,
            ownerId = request.ownerId,
            memberCount = 0, // 초기에는 0, 그룹장 추가 후 1이 됨
            createdAt = LocalDateTime.now()
        )
        
        studyGroups[groupId] = group
        nameIndex[request.name] = groupId
        groupMembers[groupId] = mutableSetOf() // 빈 멤버 리스트 초기화
        
        return group
    }
    
    fun existsByName(name: String): Boolean {
        return nameIndex.containsKey(name)
    }
    
    fun findById(groupId: String): StudyGroup? {
        return studyGroups[groupId]
    }
    
    fun findByName(name: String): StudyGroup? {
        val groupId = nameIndex[name] ?: return null
        return studyGroups[groupId]
    }
    
    fun addMember(groupId: String, userId: String): StudyGroup? {
        val group = studyGroups[groupId] ?: return null
        val members = groupMembers[groupId] ?: return null
        
        // 멤버 추가
        members.add(userId)
        val updatedGroup = group.copy(memberCount = members.size)
        studyGroups[groupId] = updatedGroup
        return updatedGroup
    }
    
    fun deleteGroup(groupId: String) {
        val group = studyGroups[groupId]
        if (group != null) {
            studyGroups.remove(groupId)
            nameIndex.remove(group.name)
            groupMembers.remove(groupId)
        }
    }
    
    /**
     * JOIN_GROUP_SAGA에서 사용하는 메서드들
     */
    
    /**
     * 사용자가 이미 그룹의 멤버인지 확인
     */
    fun isUserAlreadyMember(groupId: String, userId: String): Boolean {
        val members = groupMembers[groupId] ?: return false
        return members.contains(userId)
    }
    
    /**
     * 그룹이 정원에 도달했는지 확인
     */
    fun isGroupAtCapacity(groupId: String): Boolean {
        val group = studyGroups[groupId] ?: return true // 그룹이 없으면 참여 불가
        return group.memberCount >= MAX_GROUP_CAPACITY
    }
    
    /**
     * 그룹의 현재 멤버 수 조회
     */
    fun getGroupMemberCount(groupId: String): Int {
        val group = studyGroups[groupId] ?: return 0
        return group.memberCount
    }
    
    /**
     * 그룹이 존재하는지 확인
     */
    fun existsById(groupId: String): Boolean {
        return studyGroups.containsKey(groupId)
    }
    
    // 테스트용 메서드
    fun clear() {
        studyGroups.clear()
        nameIndex.clear()
        groupMembers.clear()
    }
}

data class StudyGroup(
    val id: String,
    val name: String,
    val description: String,
    val ownerId: String,
    val memberCount: Int,
    val createdAt: LocalDateTime
)