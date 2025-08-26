package com.algoreport.config.security

import com.algoreport.module.user.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import java.util.UUID

/**
 * CustomOAuth2UserService 테스트
 *
 * 커버리지 향상을 위한 OAuth2 사용자 서비스 테스트
 */
class CustomOAuth2UserServiceTest : BehaviorSpec({
    
    given("CustomOAuth2UserService processOAuth2User 메서드") {
        val mockUserRegistrationSaga = mockk<UserRegistrationSaga>()
        val service = CustomOAuth2UserService(mockUserRegistrationSaga)
        
        `when`("유효한 Google OAuth2 사용자 정보가 제공되면") {
            val email = "test@example.com"
            val name = "Test User"
            val picture = "https://example.com/profile.jpg"
            val userId = UUID.randomUUID()
            
            val mockUserRequest = createMockOAuth2UserRequest()
            val mockOAuth2User = createMockOAuth2User(
                attributes = mapOf(
                    "email" to email,
                    "name" to name,
                    "picture" to picture,
                    "sub" to "google_user_id_123"
                )
            )
            
            val expectedRegistrationRequest = UserRegistrationRequest(
                authCode = "oauth2_google_$email",
                email = email,
                nickname = name
            )
            
            val sagaResult = UserRegistrationResult(
                sagaStatus = SagaStatus.COMPLETED,
                userId = userId,
                errorMessage = null
            )
            
            every { mockUserRegistrationSaga.start(expectedRegistrationRequest) } returns sagaResult
            
            then("사용자 등록 SAGA를 시작하고 향상된 OAuth2User를 반환해야 한다") {
                // Reflection을 사용하여 private 메서드 테스트
                val processMethod = service::class.java.getDeclaredMethod(
                    "processOAuth2User", 
                    OAuth2UserRequest::class.java, 
                    OAuth2User::class.java
                )
                processMethod.isAccessible = true
                
                val result = processMethod.invoke(service, mockUserRequest, mockOAuth2User) as OAuth2User
                
                verify(exactly = 1) { mockUserRegistrationSaga.start(expectedRegistrationRequest) }
                
                result shouldNotBe null
                result.authorities shouldBe listOf(SimpleGrantedAuthority("ROLE_USER"))
                result.attributes["email"] shouldBe email
                result.attributes["name"] shouldBe name
                result.attributes["userId"] shouldBe userId
                result.name shouldBe "google_user_id_123"
            }
        }
        
        `when`("이메일이 누락되면") {
            val mockUserRegistrationSaga = mockk<UserRegistrationSaga>()
            val service = CustomOAuth2UserService(mockUserRegistrationSaga)
            val name = "Test User"
            
            val mockUserRequest = createMockOAuth2UserRequest()
            val mockOAuth2User = createMockOAuth2User(
                attributes = mapOf(
                    "name" to name,
                    "sub" to "google_user_id_123"
                    // email이 누락됨
                )
            )
            
            then("IllegalArgumentException을 던져야 한다") {
                val processMethod = service::class.java.getDeclaredMethod(
                    "processOAuth2User", 
                    OAuth2UserRequest::class.java, 
                    OAuth2User::class.java
                )
                processMethod.isAccessible = true
                
                val exception = shouldThrow<java.lang.reflect.InvocationTargetException> {
                    processMethod.invoke(service, mockUserRequest, mockOAuth2User)
                }
                
                // InvocationTargetException의 cause가 실제 예외
                val actualException = exception.cause as IllegalArgumentException
                actualException.message shouldContain "Required user information is missing"
                
                // SAGA가 호출되지 않아야 함
                verify(exactly = 0) { mockUserRegistrationSaga.start(any()) }
            }
        }
        
        `when`("이름이 누락되면") {
            val mockUserRegistrationSaga = mockk<UserRegistrationSaga>()
            val service = CustomOAuth2UserService(mockUserRegistrationSaga)
            val email = "test@example.com"
            
            val mockUserRequest = createMockOAuth2UserRequest()
            val mockOAuth2User = createMockOAuth2User(
                attributes = mapOf(
                    "email" to email,
                    "sub" to "google_user_id_123"
                    // name이 누락됨
                )
            )
            
            then("IllegalArgumentException을 던져야 한다") {
                val processMethod = service::class.java.getDeclaredMethod(
                    "processOAuth2User", 
                    OAuth2UserRequest::class.java, 
                    OAuth2User::class.java
                )
                processMethod.isAccessible = true
                
                val exception = shouldThrow<java.lang.reflect.InvocationTargetException> {
                    processMethod.invoke(service, mockUserRequest, mockOAuth2User)
                }
                
                // InvocationTargetException의 cause가 실제 예외
                val actualException = exception.cause as IllegalArgumentException
                actualException.message shouldContain "Required user information is missing"
                
                // SAGA가 호출되지 않아야 함
                verify(exactly = 0) { mockUserRegistrationSaga.start(any()) }
            }
        }
        
        `when`("사용자 등록 SAGA가 실패하면") {
            val mockUserRegistrationSaga = mockk<UserRegistrationSaga>()
            val service = CustomOAuth2UserService(mockUserRegistrationSaga)
            val email = "test@example.com"
            val name = "Test User"
            val errorMessage = "Database connection failed"
            
            val mockUserRequest = createMockOAuth2UserRequest()
            val mockOAuth2User = createMockOAuth2User(
                attributes = mapOf(
                    "email" to email,
                    "name" to name,
                    "sub" to "google_user_id_123"
                )
            )
            
            val sagaResult = UserRegistrationResult(
                sagaStatus = SagaStatus.FAILED,
                userId = null,
                errorMessage = errorMessage
            )
            
            every { mockUserRegistrationSaga.start(any()) } returns sagaResult
            
            then("RuntimeException을 던져야 한다") {
                val processMethod = service::class.java.getDeclaredMethod(
                    "processOAuth2User", 
                    OAuth2UserRequest::class.java, 
                    OAuth2User::class.java
                )
                processMethod.isAccessible = true
                
                val exception = shouldThrow<java.lang.reflect.InvocationTargetException> {
                    processMethod.invoke(service, mockUserRequest, mockOAuth2User)
                }
                
                // InvocationTargetException의 cause가 실제 예외
                val actualException = exception.cause as RuntimeException
                actualException.message shouldContain "Failed to register user"
                actualException.message shouldContain errorMessage
                
                verify(exactly = 1) { mockUserRegistrationSaga.start(any()) }
            }
        }
        
        `when`("사용자 등록 SAGA가 진행 중이면") {
            val mockUserRegistrationSaga = mockk<UserRegistrationSaga>()
            val service = CustomOAuth2UserService(mockUserRegistrationSaga)
            val email = "test@example.com"
            val name = "Test User"
            
            val mockUserRequest = createMockOAuth2UserRequest()
            val mockOAuth2User = createMockOAuth2User(
                attributes = mapOf(
                    "email" to email,
                    "name" to name,
                    "sub" to "google_user_id_123"
                )
            )
            
            val sagaResult = UserRegistrationResult(
                sagaStatus = SagaStatus.IN_PROGRESS,
                userId = null,
                errorMessage = null
            )
            
            every { mockUserRegistrationSaga.start(any()) } returns sagaResult
            
            then("RuntimeException을 던져야 한다") {
                val processMethod = service::class.java.getDeclaredMethod(
                    "processOAuth2User", 
                    OAuth2UserRequest::class.java, 
                    OAuth2User::class.java
                )
                processMethod.isAccessible = true
                
                val exception = shouldThrow<java.lang.reflect.InvocationTargetException> {
                    processMethod.invoke(service, mockUserRequest, mockOAuth2User)
                }
                
                // InvocationTargetException의 cause가 실제 예외
                val actualException = exception.cause as RuntimeException
                actualException.message shouldContain "Failed to register user"
                
                verify(exactly = 1) { mockUserRegistrationSaga.start(any()) }
            }
        }
        
        `when`("SAGA에서 예외가 발생하면") {
            val mockUserRegistrationSaga = mockk<UserRegistrationSaga>()
            val service = CustomOAuth2UserService(mockUserRegistrationSaga)
            val email = "test@example.com" 
            val name = "Test User"
            
            val mockUserRequest = createMockOAuth2UserRequest()
            val mockOAuth2User = createMockOAuth2User(
                attributes = mapOf(
                    "email" to email,
                    "name" to name,
                    "sub" to "google_user_id_123"
                )
            )
            
            every { mockUserRegistrationSaga.start(any()) } throws RuntimeException("SAGA execution failed")
            
            then("예외를 다시 던져야 한다") {
                val processMethod = service::class.java.getDeclaredMethod(
                    "processOAuth2User", 
                    OAuth2UserRequest::class.java, 
                    OAuth2User::class.java
                )
                processMethod.isAccessible = true
                
                val exception = shouldThrow<java.lang.reflect.InvocationTargetException> {
                    processMethod.invoke(service, mockUserRequest, mockOAuth2User)
                }
                
                // InvocationTargetException의 cause가 실제 예외
                val actualException = exception.cause as RuntimeException
                actualException.message shouldBe "SAGA execution failed"
                
                verify(exactly = 1) { mockUserRegistrationSaga.start(any()) }
            }
        }
        
        `when`("다양한 Google 사용자 정보로 테스트할 때") {
            `and`("프로필 사진이 있는 사용자면") {
                val mockUserRegistrationSaga = mockk<UserRegistrationSaga>()
                val service = CustomOAuth2UserService(mockUserRegistrationSaga)
                val email = "user.with.picture@example.com"
                val name = "Picture User"
                val picture = "https://lh3.googleusercontent.com/a/profile.jpg"
                val userId = UUID.randomUUID()
                
                val mockUserRequest = createMockOAuth2UserRequest()
                val mockOAuth2User = createMockOAuth2User(
                    attributes = mapOf(
                        "email" to email,
                        "name" to name,
                        "picture" to picture,
                        "sub" to "google_user_id_456",
                        "given_name" to "Picture",
                        "family_name" to "User"
                    )
                )
                
                val sagaResult = UserRegistrationResult(
                    sagaStatus = SagaStatus.COMPLETED,
                    userId = userId,
                    errorMessage = null
                )
                
                every { mockUserRegistrationSaga.start(any()) } returns sagaResult
                
                then("모든 속성이 보존되어 반환되어야 한다") {
                    val processMethod = service::class.java.getDeclaredMethod(
                        "processOAuth2User", 
                        OAuth2UserRequest::class.java, 
                        OAuth2User::class.java
                    )
                    processMethod.isAccessible = true
                    
                    val result = processMethod.invoke(service, mockUserRequest, mockOAuth2User) as OAuth2User
                    
                    result.attributes["email"] shouldBe email
                    result.attributes["name"] shouldBe name
                    result.attributes["picture"] shouldBe picture
                    result.attributes["userId"] shouldBe userId
                    result.attributes["given_name"] shouldBe "Picture"
                    result.attributes["family_name"] shouldBe "User"
                }
            }
            
            `and`("특수 문자가 포함된 이메일이면") {
                val mockUserRegistrationSaga = mockk<UserRegistrationSaga>()
                val service = CustomOAuth2UserService(mockUserRegistrationSaga)
                val email = "user+test@example-domain.co.kr"
                val name = "Special Email User"
                
                val mockUserRequest = createMockOAuth2UserRequest()
                val mockOAuth2User = createMockOAuth2User(
                    attributes = mapOf(
                        "email" to email,
                        "name" to name,
                        "sub" to "google_user_id_789"
                    )
                )
                
                val sagaResult = UserRegistrationResult(
                    sagaStatus = SagaStatus.COMPLETED,
                    userId = UUID.randomUUID(),
                    errorMessage = null
                )
                
                every { mockUserRegistrationSaga.start(any()) } returns sagaResult
                
                then("이메일이 올바르게 처리되어야 한다") {
                    val processMethod = service::class.java.getDeclaredMethod(
                        "processOAuth2User", 
                        OAuth2UserRequest::class.java, 
                        OAuth2User::class.java
                    )
                    processMethod.isAccessible = true
                    
                    val result = processMethod.invoke(service, mockUserRequest, mockOAuth2User) as OAuth2User
                    
                    result.attributes["email"] shouldBe email
                    
                    // SAGA에 전달된 authCode 검증
                    verify {
                        mockUserRegistrationSaga.start(
                            match { request ->
                                request.authCode == "oauth2_google_$email" &&
                                request.email == email &&
                                request.nickname == name
                            }
                        )
                    }
                }
            }
        }
    }
})

/**
 * 테스트용 헬퍼 함수들
 */
private fun createMockOAuth2UserRequest(): OAuth2UserRequest {
    val clientRegistration = ClientRegistration.withRegistrationId("google")
        .clientId("client-id")
        .clientSecret("client-secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("http://localhost:8080/login/oauth2/code/google")
        .authorizationUri("https://accounts.google.com/o/oauth2/auth")
        .tokenUri("https://oauth2.googleapis.com/token")
        .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
        .userNameAttributeName("sub")
        .build()
    
    return OAuth2UserRequest(clientRegistration, mockk(relaxed = true))
}

private fun createMockOAuth2User(attributes: Map<String, Any>): OAuth2User {
    return DefaultOAuth2User(
        listOf(SimpleGrantedAuthority("ROLE_USER")),
        attributes,
        "sub"
    )
}