package com.algoreport.config.security

import com.algoreport.module.user.*
import org.slf4j.LoggerFactory
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

/**
 * Google OAuth2 사용자 정보 처리 서비스
 * Google에서 받은 사용자 정보를 우리 시스템에 맞게 처리
 */
@Service
class CustomOAuth2UserService(
    private val userRegistrationSaga: UserRegistrationSaga
) : DefaultOAuth2UserService() {
    
    companion object {
        private val logger = LoggerFactory.getLogger(CustomOAuth2UserService::class.java)
    }
    
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oauth2User = super.loadUser(userRequest)
        
        return try {
            processOAuth2User(userRequest, oauth2User)
        } catch (ex: Exception) {
            logger.error("Failed to process OAuth2 user", ex)
            throw ex
        }
    }
    
    private fun processOAuth2User(userRequest: OAuth2UserRequest, oauth2User: OAuth2User): OAuth2User {
        val registrationId = userRequest.clientRegistration.registrationId
        val userNameAttributeName = userRequest.clientRegistration.providerDetails.userInfoEndpoint.userNameAttributeName
        
        // Google OAuth2 사용자 정보 추출
        val attributes = oauth2User.attributes
        val email = attributes["email"] as? String
        val name = attributes["name"] as? String
        val picture = attributes["picture"] as? String
        
        logger.info("Processing OAuth2 user: email=$email, name=$name, provider=$registrationId")
        
        if (email == null || name == null) {
            throw IllegalArgumentException("Required user information is missing from OAuth2 provider")
        }
        
        // 사용자 등록 또는 업데이트
        val userRegistrationRequest = UserRegistrationRequest(
            authCode = "oauth2_${registrationId}_$email", // OAuth2 플로우에서는 실제 authCode 대신 식별자 사용
            email = email,
            nickname = name
        )
        
        val registrationResult = userRegistrationSaga.start(userRegistrationRequest)
        
        if (registrationResult.sagaStatus != SagaStatus.COMPLETED) {
            logger.error("User registration failed: ${registrationResult.errorMessage}")
            throw RuntimeException("Failed to register user: ${registrationResult.errorMessage}")
        }
        
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        
        // 사용자 ID를 attributes에 추가
        val updatedAttributes = attributes.toMutableMap()
        updatedAttributes["userId"] = registrationResult.userId
        
        return DefaultOAuth2User(authorities, updatedAttributes, userNameAttributeName)
    }
}