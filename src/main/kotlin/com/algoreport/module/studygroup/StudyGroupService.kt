package com.algoreport.module.studygroup

import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 스터디 그룹 관리 서비스
 * TDD Refactor 단계: Repository 패턴 도입으로 데이터 접근 분리
 */
@Service
class StudyGroupService : StudyGroupRepository {
    
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
    
    override fun findById(groupId: String): StudyGroup? {
        return studyGroups[groupId]
    }
    
    fun findByName(name: String): StudyGroup? {
        val groupId = nameIndex[name] ?: return null
        return studyGroups[groupId]
    }
    
    fun addMember(groupId: String, userId: UUID): StudyGroup? {
        val group = studyGroups[groupId] ?: return null
        val members = groupMembers[groupId] ?: return null
        
        // 멤버 추가 (UUID를 String으로 변환)
        members.add(userId.toString())
        val updatedGroup = group.copy(memberCount = members.size)
        studyGroups[groupId] = updatedGroup
        return updatedGroup
    }
    
    /**
     * 그룹에서 멤버를 제거합니다.
     * 보상 트랜잭션 및 그룹 탈퇴 기능에서 사용됩니다.
     * 
     * @param groupId 그룹 ID
     * @param userId 제거할 사용자 ID
     * @return 업데이트된 그룹 정보, 그룹이 존재하지 않으면 null
     */
    fun removeMember(groupId: String, userId: UUID): StudyGroup? {
        val group = studyGroups[groupId] ?: return null
        val members = groupMembers[groupId] ?: return null
        
        // 멤버 제거 (UUID를 String으로 변환, 실제로 멤버였는지 상관없이 제거 시도 - 멱등성 보장)
        val removed = members.remove(userId.toString())
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
    fun isUserAlreadyMember(groupId: String, userId: UUID): Boolean {
        val members = groupMembers[groupId] ?: return false
        return members.contains(userId.toString())
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
    
    // Repository 인터페이스 구현
    override fun findAllActiveGroupIds(): List<String> {
        return studyGroups.keys.toList()
    }
    
    override fun existsById(groupId: String): Boolean {
        return studyGroups.containsKey(groupId)
    }
    
    override fun findGroupMemberIds(groupId: String): List<String> {
        return groupMembers[groupId]?.toList() ?: emptyList()
    }
    
    override fun count(): Long {
        return studyGroups.size.toLong()
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
    val ownerId: UUID,
    val memberCount: Int,
    val createdAt: LocalDateTime
)