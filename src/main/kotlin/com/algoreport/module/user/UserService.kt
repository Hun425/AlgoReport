package com.algoreport.module.user

import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 사용자 관리 서비스
 * TDD Refactor 단계: Repository 패턴 도입으로 데이터 접근 분리
 */
@Service
class UserService : UserRepository {
    
    // 테스트용 인메모리 저장소
    private val users = ConcurrentHashMap<String, User>()
    private val emailIndex = ConcurrentHashMap<String, String>() // email -> userId
    
    fun createUser(request: UserCreateRequest): User {
        val userId = UUID.randomUUID().toString()
        val user = User(
            id = userId,
            email = request.email,
            nickname = request.nickname,
            profileImageUrl = null, // 초기에는 null
            provider = request.provider
        )
        
        users[userId] = user
        emailIndex[request.email] = userId
        
        return user
    }
    
    fun existsByEmail(email: String): Boolean {
        return emailIndex.containsKey(email)
    }
    
    fun findByEmail(email: String): User? {
        val userId = emailIndex[email] ?: return null
        return users[userId]
    }
    
    fun deleteUser(userId: String) {
        val user = users[userId]
        if (user != null) {
            users.remove(userId)
            emailIndex.remove(user.email)
        }
    }
    
    override fun findById(userId: String): User? {
        return users[userId]
    }
    
    fun updateSolvedacInfo(userId: String, solvedacHandle: String, tier: Int, solvedCount: Int): User? {
        val user = users[userId] ?: return null
        val updatedUser = user.copy(
            solvedacHandle = solvedacHandle,
            solvedacTier = tier,
            solvedacSolvedCount = solvedCount
        )
        users[userId] = updatedUser
        return updatedUser
    }
    
    fun existsBySolvedacHandle(handle: String): Boolean {
        // TODO: [GREEN] solved.ac 핸들 중복 체크 로직 구현 필요
        return users.values.any { it.solvedacHandle == handle }
    }
    
    // Repository 인터페이스 구현
    override fun findAllActiveUserIds(): List<String> {
        return users.keys.toList()
    }
    
    override fun existsById(userId: String): Boolean {
        return users.containsKey(userId)
    }
    
    override fun count(): Long {
        return users.size.toLong()
    }
    
    // 테스트용 메서드
    fun clear() {
        users.clear()
        emailIndex.clear()
    }
}