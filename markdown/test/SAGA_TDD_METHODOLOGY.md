# Saga 패턴 특화 TDD 방법론

이 문서는 **알고리포트의 15개 Saga에 대한 체계적이고 실전적인 TDD 접근법**을 제시합니다. 분산 트랜잭션의 복잡성을 고려하여 단계별 테스트 전략과 실제 구현 예시를 포함합니다.

---

## 🎯 **Saga TDD의 핵심 원칙**

### **1. 테스트 우선 개발 (Test-First Development)**
- Saga 시나리오를 먼저 테스트로 정의
- 비즈니스 요구사항을 실행 가능한 테스트로 변환
- 보상 트랜잭션까지 포함한 완전한 테스트 커버리지

### **2. 계층적 테스트 접근법**
```
Level 4: 전체 Saga 시나리오 테스트 (E2E)
Level 3: Saga 단계 간 통합 테스트 
Level 2: 개별 Saga 단계 테스트
Level 1: Saga 구성 요소 단위 테스트
```

### **3. 실패 우선 설계 (Failure-First Design)**
- Happy Path보다 실패 시나리오를 먼저 테스트
- 보상 트랜잭션의 멱등성 검증
- 부분 실패 상황의 일관성 보장

---

## 📋 **Saga TDD 워크플로우**

### **Phase 1: 비즈니스 시나리오 정의**

```kotlin
// 1단계: 비즈니스 요구사항을 Given-When-Then으로 정의
class UserRegistrationSagaSpecification {
    
    @Test
    fun `비즈니스 요구사항_사용자가_구글로_로그인하면_분석_프로필과_알림_설정이_자동_생성된다`() {
        // 이 테스트는 처음에는 컴파일조차 되지 않음 (TDD Red 단계)
        
        given {
            googleOAuth2SuccessfullyReturns {
                email = "newuser@gmail.com"
                name = "신규사용자"
                picture = "https://profile.jpg"
            }
            analysisServiceIsAvailable()
            notificationServiceIsAvailable()
        }
        
        `when` {
            userTriesToRegisterViaGoogle()
        }
        
        then {
            userShouldBeCreatedInDatabase()
            analysisProfileShouldBeInitialized()
            welcomeNotificationShouldBeSent()
            allStepsShouldCompleteWithin(30.seconds)
        }
    }
    
    @Test  
    fun `비즈니스 요구사항_분석_서비스_장애시_사용자_등록이_완전히_취소된다`() {
        given {
            googleOAuth2SuccessfullyReturns(validUserInfo())
            analysisServiceIsDown()  // 의도적 장애
        }
        
        `when` {
            userTriesToRegisterViaGoogle()
        }
        
        then {
            userRegistrationShouldFail()
            noUserDataShouldRemainInDatabase()  // 보상으로 정리됨
            userShouldReceiveErrorMessage()
        }
    }
}
```

### **Phase 2: Saga 인터페이스 설계**

```kotlin
// 2단계: 테스트가 통과할 수 있도록 인터페이스 정의
interface UserRegistrationSaga : Saga<UserRegistrationContext> {
    
    // 컴파일을 통과시키기 위한 최소한의 인터페이스
    override fun start(context: UserRegistrationContext): SagaResult
    override fun compensate(sagaId: UUID, failedStep: String): CompensationResult
    override fun getStatus(sagaId: UUID): SagaStatus
    
    // Saga 특화 메서드들
    fun handleUserCreated(event: UserCreatedEvent)
    fun handleAnalysisProfileCreated(event: AnalysisProfileCreatedEvent)
    fun handleWelcomeNotificationSent(event: WelcomeNotificationSentEvent)
    
    // 실패 처리 메서드들
    fun handleAnalysisProfileCreationFailed(event: AnalysisProfileCreationFailedEvent)
    fun handleNotificationSendingFailed(event: NotificationSendingFailedEvent)
}

data class UserRegistrationContext(
    val authCode: String,
    val userInfo: GoogleUserInfo,
    val ipAddress: String,
    val userAgent: String,
    val registrationSource: RegistrationSource = RegistrationSource.WEB
) : SagaContext(sagaType = "USER_REGISTRATION_SAGA")
```

### **Phase 3: 단계별 TDD 구현**

#### **3.1 Level 1: Saga 구성 요소 단위 테스트**

```kotlin
class UserServiceTest {
    
    @Test
    fun `구글_사용자_정보로_사용자_엔티티를_생성할_수_있다`() {
        // Given
        val googleUserInfo = GoogleUserInfo(
            email = "test@gmail.com",
            name = "테스터",
            picture = "https://profile.jpg"
        )
        
        // When
        val user = userService.createUserFromGoogleInfo(googleUserInfo)
        
        // Then
        assertThat(user.email).isEqualTo("test@gmail.com")
        assertThat(user.nickname).isNotBlank()
        assertThat(user.provider).isEqualTo(AuthProvider.GOOGLE)
        assertThat(user.isActive).isTrue()
    }
    
    @Test
    fun `중복된_이메일로_사용자_생성시_예외가_발생한다`() {
        // Given
        val existingEmail = "existing@gmail.com"
        userRepository.save(aUser { email = existingEmail })
        
        val googleUserInfo = GoogleUserInfo(email = existingEmail, name = "중복사용자")
        
        // When & Then
        assertThrows<UserAlreadyExistsException> {
            userService.createUserFromGoogleInfo(googleUserInfo)
        }
    }
}
```

#### **3.2 Level 2: 개별 Saga 단계 테스트**

```kotlin
class UserRegistrationSagaStepTest {
    
    @Test
    fun `1단계_사용자_생성_성공시_USER_CREATED_이벤트가_발행된다`() {
        // Given
        val context = aUserRegistrationContext()
        val saga = UserRegistrationSaga(userService, sagaCoordinator, outboxService)
        
        // When
        saga.executeStep1_CreateUser(context)
        
        // Then
        val outboxEvents = outboxRepository.findBySagaId(context.sagaId)
        val userCreatedEvent = outboxEvents.find { it.eventType == "USER_CREATED" }
        
        assertThat(userCreatedEvent).isNotNull()
        assertThat(userCreatedEvent!!.processed).isFalse()  // 아직 발행 전
        
        val eventData = objectMapper.readValue<UserCreatedEventData>(userCreatedEvent.eventData)
        assertThat(eventData.email).isEqualTo(context.userInfo.email)
    }
    
    @Test
    fun `2단계_분석_프로필_생성_실패시_보상_트랜잭션이_실행된다`() {
        // Given
        val context = aUserRegistrationContext()
        val saga = UserRegistrationSaga(userService, sagaCoordinator, outboxService)
        
        // 1단계는 성공했다고 가정
        val userId = saga.executeStep1_CreateUser(context)
        
        // 2단계에서 실패 이벤트 수신
        val failureEvent = AnalysisProfileCreationFailedEvent(
            userId = userId,
            error = "Database connection failed",
            sagaId = context.sagaId
        )
        
        // When
        saga.handleAnalysisProfileCreationFailed(failureEvent)
        
        // Then: 보상 트랜잭션 실행 확인
        val user = userRepository.findById(userId)
        assertThat(user).isNull()  // 사용자가 삭제되어야 함
        
        val compensationEvents = outboxRepository.findCompensationEvents(context.sagaId)
        assertThat(compensationEvents).anyMatch { it.eventType == "USER_CREATION_COMPENSATED" }
    }
}
```

#### **3.3 Level 3: Saga 단계 간 통합 테스트**

```kotlin
@SpringBootTest
@TestMethodOrder(OrderAnnotation::class)
class UserRegistrationSagaIntegrationTest {
    
    private lateinit var testSagaId: UUID
    
    @Test @Order(1)
    fun `전체_단계_통합_테스트_성공_시나리오`() {
        // Given: 모든 외부 의존성 준비
        mockGoogleOAuth2Service()
        ensureAnalysisServiceHealthy()
        ensureNotificationServiceHealthy()
        
        val context = aUserRegistrationContext()
        testSagaId = context.sagaId
        
        // When: Saga 시작
        val result = userRegistrationSaga.start(context)
        
        // Then: 첫 번째 단계만 즉시 완료
        assertThat(result.status).isEqualTo(SagaStatus.IN_PROGRESS)
        assertThat(result.completedSteps).contains("USER_CREATED")
        
        // 비동기 이벤트 처리 대기
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val sagaInstance = sagaRepository.findById(testSagaId)!!
            assertThat(sagaInstance.sagaStatus).isEqualTo(SagaStatus.COMPLETED)
            
            val completedSteps = objectMapper.readValue<List<String>>(sagaInstance.completedSteps)
            assertThat(completedSteps).containsAll(listOf(
                "USER_CREATED", 
                "ANALYSIS_PROFILE_CREATED", 
                "WELCOME_NOTIFICATION_SENT"
            ))
        }
    }
    
    @Test @Order(2)
    fun `중간_단계_실패시_이전_단계들이_보상된다`() {
        // Given: 분석 서비스만 의도적으로 실패시킴
        mockGoogleOAuth2Service()
        makeAnalysisServiceFail()  // 2단계에서 실패
        ensureNotificationServiceHealthy()
        
        val context = aUserRegistrationContext()
        
        // When
        val result = userRegistrationSaga.start(context)
        
        // Then: 보상 트랜잭션까지 완료
        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            val sagaInstance = sagaRepository.findById(result.sagaId)!!
            assertThat(sagaInstance.sagaStatus).isEqualTo(SagaStatus.COMPENSATED)
            
            // 1단계 결과가 보상되었는지 확인
            val user = userRepository.findByEmail(context.userInfo.email)
            assertThat(user).isNull()  // 생성된 사용자가 삭제됨
            
            val compensationEvents = outboxRepository.findCompensationEvents(result.sagaId)
            assertThat(compensationEvents).isNotEmpty()
        }
    }
}
```

#### **3.4 Level 4: 전체 Saga 시나리오 테스트**

```kotlin
@SpringBootTest
@Transactional
@TestPropertySource(properties = ["spring.profiles.active=e2e-test"])
class UserRegistrationSagaE2ETest {
    
    @Test
    fun `실제_환경과_유사한_조건에서_사용자_등록_완전한_플로우_테스트`() {
        // Given: 실제 환경과 최대한 비슷한 설정
        // - 실제 H2 데이터베이스 사용
        // - 실제 Spring 컨텍스트 로딩
        // - 실제 이벤트 발행/구독 (단, 외부 API는 Mock)
        
        stubGoogleOAuth2API()
        startEmbeddedKafka()
        
        val registrationRequest = UserRegistrationRequest(
            authCode = "mock_google_auth_code_123",
            clientIP = "127.0.0.1",
            userAgent = "Mozilla/5.0 (Test Browser)"
        )
        
        // When: 실제 REST API 호출
        val response = webTestClient
            .post()
            .uri("/api/v1/auth/register")
            .bodyValue(registrationRequest)
            .exchange()
            
        // Then: API 응답 확인
            .expectStatus().isCreated
            .expectBody<UserRegistrationResponse>()
            .value { response ->
                assertThat(response.userId).isNotBlank()
                assertThat(response.email).isEqualTo("mocked@gmail.com")
                assertThat(response.accessToken).isNotBlank()
            }
        
        // And: 전체 시스템 상태 확인
        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            // 사용자 데이터 확인
            val user = userRepository.findByEmail("mocked@gmail.com")
            assertThat(user).isNotNull()
            assertThat(user!!.isActive).isTrue()
            
            // 분석 프로필 확인
            val analysisProfile = analysisProfileRepository.findByUserId(user.id)
            assertThat(analysisProfile).isNotNull()
            
            // 알림 설정 확인
            val notificationSettings = notificationSettingsRepository.findByUserId(user.id)
            assertThat(notificationSettings).isNotNull()
            
            // 환영 알림 발송 확인
            val welcomeNotification = notificationHistoryRepository
                .findByUserIdAndType(user.id, NotificationType.WELCOME)
            assertThat(welcomeNotification).isNotNull()
        }
    }
}
```

---

## 🧪 **Saga 테스트 패턴 라이브러리**

### **Saga 테스트 DSL 구현**

```kotlin
// Saga 테스트를 위한 전용 DSL
class SagaTestDSL(
    private val sagaCoordinator: SagaCoordinator,
    private val outboxRepository: OutboxEventRepository,
    private val sagaRepository: SagaInstanceRepository
) {
    
    private lateinit var sagaId: UUID
    private lateinit var sagaType: String
    private val mockSetups = mutableListOf<() -> Unit>()
    private val verifications = mutableListOf<() -> Unit>()
    
    fun given(setup: SagaTestSetup.() -> Unit): SagaTestDSL {
        val testSetup = SagaTestSetup()
        testSetup.setup()
        mockSetups.addAll(testSetup.getMockSetups())
        return this
    }
    
    fun whenSagaStarts(sagaType: String, context: SagaContext): SagaTestDSL {
        // Mock 설정 적용
        mockSetups.forEach { it.invoke() }
        
        this.sagaType = sagaType
        this.sagaId = sagaCoordinator.startSaga(sagaType, context.correlationData)
        return this
    }
    
    fun thenSagaShould(assertions: SagaAssertions.() -> Unit): SagaTestDSL {
        val sagaAssertions = SagaAssertions(sagaId, sagaRepository, outboxRepository)
        sagaAssertions.assertions()
        return this
    }
    
    fun andSystemShould(assertions: SystemStateAssertions.() -> Unit): SagaTestDSL {
        val systemAssertions = SystemStateAssertions(sagaId)
        systemAssertions.assertions()
        return this
    }
    
    fun waitForCompletion(timeout: Duration = Duration.ofSeconds(30)) {
        await().atMost(timeout).untilAsserted {
            val saga = sagaRepository.findById(sagaId)!!
            assertThat(saga.sagaStatus).isIn(SagaStatus.COMPLETED, SagaStatus.FAILED, SagaStatus.COMPENSATED)
        }
    }
}

// 사용 예시
@Test
fun `JOIN_GROUP_SAGA_성공_시나리오`() {
    sagaTest {
        given {
            groupExists(groupId = "group-123") {
                maxMembers = 10
                currentMembers = 5
                isPublic = true
            }
            userExists(userId = "user-456") {
                isActive = true
                hasLinkedSolvedac = true
            }
            analysisServiceIsHealthy()
            notificationServiceIsHealthy()
        }
        
        whenSagaStarts("JOIN_GROUP_SAGA") {
            correlationData = mapOf(
                "groupId" to "group-123",
                "userId" to "user-456"
            )
        }
        
        thenSagaShould {
            completeSuccessfully()
            executeStepsInOrder("USER_VALIDATED", "MEMBER_ADDED", "PROFILE_SYNCED", "WELCOME_SENT")
            triggerEvents("USER_VALIDATION_REQUESTED", "MEMBER_JOINED", "PROFILE_SYNCED", "WELCOME_NOTIFICATION_SENT")
        }
        
        andSystemShould {
            haveUserInGroup("user-456", "group-123")
            haveUpdatedGroupMemberCount("group-123", 6)
            haveSyncedAnalysisProfile("user-456", "group-123")
            haveWelcomeNotificationSent("user-456")
        }
    }
}
```

### **Mock 및 Stub 헬퍼**

```kotlin
class SagaTestSetup {
    private val mockSetups = mutableListOf<() -> Unit>()
    
    fun googleOAuth2Returns(userInfo: GoogleUserInfo) {
        mockSetups.add {
            whenever(googleOAuth2Service.getUserInfo(any()))
                .thenReturn(userInfo)
        }
    }
    
    fun analysisServiceIsHealthy() {
        mockSetups.add {
            whenever(analysisService.createUserAnalysisProfile(any(), any(), any()))
                .thenReturn(AnalysisProfile(id = UUID.randomUUID(), userId = UUID.randomUUID()))
        }
    }
    
    fun analysisServiceFails(error: String) {
        mockSetups.add {
            whenever(analysisService.createUserAnalysisProfile(any(), any(), any()))
                .thenThrow(RuntimeException(error))
        }
    }
    
    fun userExists(userId: String, setup: TestUser.() -> Unit = {}) {
        val testUser = TestUser(userId).apply(setup)
        mockSetups.add {
            whenever(userRepository.findById(userId))
                .thenReturn(testUser.toEntity())
        }
    }
    
    fun groupExists(groupId: String, setup: TestGroup.() -> Unit = {}) {
        val testGroup = TestGroup(groupId).apply(setup)
        mockSetups.add {
            whenever(studyGroupRepository.findById(groupId))
                .thenReturn(testGroup.toEntity())
        }
    }
    
    fun getMockSetups(): List<() -> Unit> = mockSetups
}

// 테스트 데이터 빌더
class TestUser(val userId: String) {
    var isActive: Boolean = true
    var hasLinkedSolvedac: Boolean = false
    var nickname: String = "테스터${Random.nextInt(1000)}"
    var email: String = "test${Random.nextInt(1000)}@example.com"
    
    fun toEntity(): User {
        return User(
            id = UUID.fromString(userId),
            email = email,
            nickname = nickname,
            isActive = isActive,
            provider = AuthProvider.GOOGLE
        ).apply {
            if (hasLinkedSolvedac) {
                this.solvedacHandle = "test_handle_${Random.nextInt(1000)}"
            }
        }
    }
}
```

---

## 🚨 **Saga 테스트의 일반적인 함정과 해결책**

### **1. 비동기 처리로 인한 타이밍 이슈**

**문제**: 이벤트 기반 비동기 처리로 인해 테스트 결과를 언제 확인해야 할지 모호함

**해결책**: Awaitility를 활용한 조건부 대기

```kotlin
// ❌ 잘못된 방법: Thread.sleep() 사용
@Test
fun `이벤트_처리_테스트_잘못된_방법`() {
    publishEvent(event)
    Thread.sleep(1000)  // 불확실한 대기
    assertThat(result).isNotNull()
}

// ✅ 올바른 방법: 조건부 대기
@Test
fun `이벤트_처리_테스트_올바른_방법`() {
    publishEvent(event)
    
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted {
            val result = resultRepository.findByEventId(event.id)
            assertThat(result).isNotNull()
            assertThat(result.status).isEqualTo(ProcessingStatus.COMPLETED)
        }
}
```

### **2. 테스트 간 상태 격리 문제**

**문제**: 이전 테스트의 Saga 상태가 다음 테스트에 영향을 미침

**해결책**: 테스트별 독립적인 데이터베이스와 명시적 정리

```kotlin
@TestMethodOrder(OrderAnnotation::class)
class SagaTestWithProperIsolation {
    
    @AfterEach
    fun cleanupSagaState() {
        // 진행 중인 모든 Saga 정리
        sagaRepository.deleteAll()
        outboxRepository.deleteAll()
        
        // 테스트 데이터 정리
        userRepository.deleteAll()
        studyGroupRepository.deleteAll()
        
        // 외부 Mock 상태 초기화
        reset(googleOAuth2Service, analysisService, notificationService)
    }
    
    @Test
    fun `각_테스트는_깨끗한_상태에서_시작된다`() {
        // 테스트 전에 데이터베이스가 비어있음을 확인
        assertThat(sagaRepository.count()).isEqualTo(0)
        assertThat(outboxRepository.count()).isEqualTo(0)
        
        // 테스트 실행...
    }
}
```

### **3. 보상 트랜잭션의 멱등성 검증 부족**

**문제**: 보상 트랜잭션이 여러 번 실행되어도 안전한지 검증하지 않음

**해결책**: 보상 트랜잭션 중복 실행 시나리오 테스트

```kotlin
@Test
fun `보상_트랜잭션_멱등성_테스트`() {
    // Given: 실패한 Saga
    val saga = createFailedSaga()
    
    // When: 보상 트랜잭션을 여러 번 실행
    val firstCompensation = sagaCoordinator.compensate(saga.sagaId, "ANALYSIS_PROFILE_CREATION")
    val secondCompensation = sagaCoordinator.compensate(saga.sagaId, "ANALYSIS_PROFILE_CREATION")  // 중복 실행
    
    // Then: 두 번째 실행도 안전해야 함
    assertThat(firstCompensation.success).isTrue()
    assertThat(secondCompensation.success).isTrue()
    
    // 시스템 상태는 동일해야 함
    assertSystemStateIsConsistent()
}

private fun assertSystemStateIsConsistent() {
    // 사용자가 정확히 한 번만 삭제되었는지 확인
    val deletedUserCount = userRepository.countByEmail("test@example.com")
    assertThat(deletedUserCount).isEqualTo(0)
    
    // 보상 이벤트가 중복 발행되지 않았는지 확인
    val compensationEvents = outboxRepository.findByEventType("USER_CREATION_COMPENSATED")
    assertThat(compensationEvents).hasSize(1)  // 한 번만 발행되어야 함
}
```

---

## 📊 **Saga TDD 메트릭 및 품질 관리**

### **코드 커버리지 목표**

```yaml
Saga TDD 품질 기준:
  전체_코드_커버리지: ≥ 85%
  Saga_로직_커버리지: ≥ 95%
  보상_트랜잭션_커버리지: ≥ 90%
  
  테스트_타입별_비율:
    단위_테스트: 40%
    단계_테스트: 30%
    통합_테스트: 25%
    E2E_테스트: 5%
  
  시나리오_커버리지:
    Happy_Path: 100% (필수)
    실패_시나리오: ≥ 80%
    보상_시나리오: ≥ 90%
    동시성_시나리오: ≥ 70%
```

### **테스트 성능 기준**

```kotlin
// 테스트 실행 시간 모니터링
class SagaTestMetrics {
    
    companion object {
        const val UNIT_TEST_MAX_DURATION = 1000L  // 1초
        const val INTEGRATION_TEST_MAX_DURATION = 10000L  // 10초
        const val E2E_TEST_MAX_DURATION = 30000L  // 30초
    }
    
    @TestExecutionListeners(value = [SagaTestTimingListener::class])
    abstract class SagaTestBase
}

class SagaTestTimingListener : TestExecutionListener {
    
    override fun afterTestExecution(testContext: TestContext) {
        val duration = getDuration(testContext)
        val testType = getTestType(testContext)
        
        val maxDuration = when (testType) {
            TestType.UNIT -> SagaTestMetrics.UNIT_TEST_MAX_DURATION
            TestType.INTEGRATION -> SagaTestMetrics.INTEGRATION_TEST_MAX_DURATION
            TestType.E2E -> SagaTestMetrics.E2E_TEST_MAX_DURATION
        }
        
        if (duration > maxDuration) {
            logger.warn(
                "Slow Saga test detected: ${testContext.testMethod.name} " +
                "took ${duration}ms (max: ${maxDuration}ms)"
            )
        }
    }
}
```

---

## 🎯 **Phase별 Saga TDD 로드맵**

### **Phase 1: 기본 Saga TDD 구축**
```kotlin
// 우선 구현할 Saga들
1. USER_REGISTRATION_SAGA (복잡도: Medium)
   - 테스트 패턴 정립의 기준점
   - 보상 트랜잭션 패턴 확립
   
2. CREATE_GROUP_SAGA (복잡도: Medium)  
   - 4단계 Saga 테스트 경험
   - 모듈 간 협력 테스트 패턴

3. JOIN_GROUP_SAGA (복잡도: High)
   - 복잡한 검증 로직 테스트
   - 동시성 이슈 테스트 패턴
```

### **Phase 2: 고급 Saga TDD 패턴**
```kotlin
// 복잡한 Saga들
4. INITIAL_DATA_SYNC_SAGA (복잡도: Very High)
   - 대용량 데이터 처리 테스트
   - 체크포인트 기반 복구 테스트
   
5. RULE_VIOLATION_SAGA (복잡도: High)
   - 스케줄러 기반 Saga 테스트
   - 조건부 실행 로직 테스트
```

### **Phase 3: Saga TDD 최적화**
```kotlin
// 나머지 Saga들 + 성능 최적화
6-15. 나머지 Saga들
   - 확립된 패턴 적용
   - 테스트 자동화 도구 활용
   - 성능 테스트 강화
```

---

이 Saga TDD 방법론을 통해 분산 트랜잭션의 복잡성을 체계적으로 다루면서도 높은 품질의 코드를 작성할 수 있습니다. 특히 실패 시나리오와 보상 트랜잭션에 대한 철저한 테스트로 운영 환경에서의 안정성을 보장할 수 있습니다.

📝 **문서 버전**: v1.0  
📅 **최종 수정일**: 2025-07-23  
👤 **작성자**: 채기훈