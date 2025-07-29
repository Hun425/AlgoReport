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
        
        return group
    }
    
    fun existsByName(name: String): Boolean {
        return nameIndex.containsKey(name)
    }
    
    fun findById(groupId: String): StudyGroup? {
        return studyGroups[groupId]
    }
    
    fun addMember(groupId: String, userId: String): StudyGroup? {
        val group = studyGroups[groupId] ?: return null
        val updatedGroup = group.copy(memberCount = group.memberCount + 1)
        studyGroups[groupId] = updatedGroup
        return updatedGroup
    }
    
    fun deleteGroup(groupId: String) {
        val group = studyGroups[groupId]
        if (group != null) {
            studyGroups.remove(groupId)
            nameIndex.remove(group.name)
        }
    }
    
    // 테스트용 메서드
    fun clear() {
        studyGroups.clear()
        nameIndex.clear()
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