# Saga 구현 가이드 및 운영 도구

이 문서는 **알고리포트의 15개 Saga 구현을 위한 실전 가이드**입니다. 실제 코드 예제, 테스트 방법론, 운영 도구, 모니터링 설정을 포함합니다.

---

## 🏗️ **구현 아키텍처**

### **Saga 분류 및 선택 가이드 (신규 설계 원칙)**

**중요**: 모든 분산 트랜잭션을 Saga 패턴으로 구현할 필요는 없습니다. 아래 가이드에 따라 적절한 패턴을 선택합니다.

- **언제 Saga를 사용하는가? (Critical Saga)**
  - **조건**: 여러 모듈에 걸쳐 데이터가 변경되며, **중간 단계 실패 시 반드시 이전 단계의 작업이 롤백(보상)되어야 하는 경우**에만 사용합니다.
  - **예시**: `USER_REGISTRATION_SAGA` (사용자 생성 실패 시 분석 프로필, 알림 설정도 생성되면 안 됨), `JOIN_GROUP_SAGA` (멤버 추가 실패 시 그룹 정원 예약 등 롤백 필요)

- **언제 단순 이벤트를 사용하는가? (Simple Event)**
  - **조건**: **롤백이 불필요한** 정보 동기화나 부가 기능 호출 시 사용합니다. Producer는 자신의 작업을 완료하고 이벤트를 발행하면 책임이 끝납니다. Consumer는 알아서 정보를 동기화합니다.
  - **예시**: `USER_PROFILE_UPDATE_SAGA` (프로필 업데이트 후 알림 모듈 동기화 실패가 프로필 업데이트 자체를 롤백시킬 필요 없음), `DISCUSSION_CREATE_SAGA` (토론 생성 후 알림 실패가 토론 삭제로 이어질 필요 없음)

**[리팩토링 계획]** 현재 설계된 Saga 중 일부는 **Simple Event** 방식으로 전환하여 시스템 복잡성을 낮출 예정입니다. (Phase 6 참조)

### **핵심 컴포넌트 구조**

```kotlin
// 1. Saga 기본 인터페이스
interface Saga<T : SagaContext> {
    fun start(context: T): SagaResult
    fun compensate(sagaId: UUID, failedStep: String): CompensationResult
    fun getStatus(sagaId: UUID): SagaStatus
}

// 2. Saga 컨텍스트
abstract class SagaContext(
    val sagaId: UUID = UUID.randomUUID(),
    val sagaType: String,
    val startedAt: LocalDateTime = LocalDateTime.now(),
    val correlationData: Map<String, Any> = emptyMap()
)

// 3. Saga 결과
data class SagaResult(
    val sagaId: UUID,
    val status: SagaStatus,
    val completedSteps: List<String>,
    val failedStep: String? = null,
    val errorMessage: String? = null,
    val result: Any? = null
)

enum class SagaStatus {
    STARTED, IN_PROGRESS, COMPLETED, 
    FAILED, COMPENSATING, COMPENSATED
}
```

---

## 📋 **Step-by-Step 구현 가이드**

### **Step 1: 기본 인프라 구현**

#### **1.1 Outbox Event Publisher**

```kotlin
@Entity
@Table(name = "OUTBOX_EVENTS")
data class OutboxEvent(
    @Id
    val eventId: UUID = UUID.randomUUID(),
    
    @Column(name = "aggregate_type")
    val aggregateType: String,
    
    @Column(name = "aggregate_id")
    val aggregateId: String,
    
    @Column(name = "event_type")
    val eventType: String,
    
    @Column(name = "event_data", columnDefinition = "jsonb")
    val eventData: String,
    
    @Column(name = "saga_id")
    val sagaId: UUID? = null,
    
    @Column(name = "saga_type")
    val sagaType: String? = null,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "processed")
    var processed: Boolean = false,
    
    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null,
    
    @Column(name = "retry_count")
    var retryCount: Int = 0,
    
    @Column(name = "max_retries")
    val maxRetries: Int = 3,
    
    @Column(name = "next_retry_at")
    var nextRetryAt: LocalDateTime? = null,
    
    @Column(name = "error_message", length = 1000)
    var errorMessage: String? = null,
    
    @Version
    val version: Long = 1
)

@Repository
interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID> {
    
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.processed = false 
        AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)
        AND e.retryCount < e.maxRetries
        ORDER BY e.createdAt ASC
        LIMIT :limit
    """)
    fun findUnprocessedEvents(
        @Param("now") now: LocalDateTime = LocalDateTime.now(),
        @Param("limit") limit: Int = 100
    ): List<OutboxEvent>
    
    @Query("SELECT e FROM OutboxEvent e WHERE e.sagaId = :sagaId ORDER BY e.createdAt")
    fun findBySagaId(@Param("sagaId") sagaId: UUID): List<OutboxEvent>
    
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.processed = true, e.processedAt = :processedAt WHERE e.eventId = :eventId")
    fun markAsProcessed(@Param("eventId") eventId: UUID, @Param("processedAt") processedAt: LocalDateTime)
}
```

#### **1.2 Saga State Manager**

```kotlin
@Entity
@Table(name = "SAGA_INSTANCES")
data class SagaInstance(
    @Id
    val sagaId: UUID,
    
    @Column(name = "saga_type")
    val sagaType: String,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "saga_status")
    var sagaStatus: SagaStatus,
    
    @Column(name = "correlation_data", columnDefinition = "jsonb")
    val correlationData: String,
    
    @Column(name = "current_step")
    var currentStep: String? = null,
    
    @Column(name = "completed_steps", columnDefinition = "jsonb")
    var completedSteps: String = "[]",
    
    @Column(name = "failed_step")
    var failedStep: String? = null,
    
    @Column(name = "compensation_steps", columnDefinition = "jsonb")
    var compensationSteps: String = "[]",
    
    @Column(name = "step_history", columnDefinition = "jsonb")
    var stepHistory: String = "[]",
    
    @Column(name = "started_at")
    val startedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,
    
    @Column(name = "timeout_at")
    var timeoutAt: LocalDateTime? = null,
    
    @Column(name = "error_message", length = 2000)
    var errorMessage: String? = null,
    
    @Version
    val version: Long = 1
) {
    fun updateStep(stepName: String, stepStatus: SagaStepStatus, stepData: Any? = null) {
        this.currentStep = stepName
        this.updatedAt = LocalDateTime.now()
        
        // 완료된 단계 추가
        if (stepStatus == SagaStepStatus.COMPLETED) {
            val completed = objectMapper.readValue<MutableList<String>>(completedSteps)
            completed.add(stepName)
            this.completedSteps = objectMapper.writeValueAsString(completed)
        }
        
        // 단계 이력 추가
        val history = objectMapper.readValue<MutableList<Map<String, Any>>>(stepHistory)
        history.add(mapOf(
            "step" to stepName,
            "status" to stepStatus.name,
            "timestamp" to LocalDateTime.now().toString(),
            "data" to (stepData ?: emptyMap<String, Any>())
        ))
        this.stepHistory = objectMapper.writeValueAsString(history)
    }
}

enum class SagaStepStatus {
    STARTED, IN_PROGRESS, COMPLETED, FAILED, COMPENSATED
}
```

#### **1.3 Event-driven Saga Coordinator**

```kotlin
@Service
@Transactional
class ChoreographySagaCoordinator(
    private val sagaRepository: SagaInstanceRepository,
    private val outboxRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) : SagaCoordinator {
    
    companion object {
        private val logger = LoggerFactory.getLogger(ChoreographySagaCoordinator::class.java)
    }
    
    private val sagaMetrics = SagaMetrics()
    
    override fun startSaga(sagaType: String, correlationData: Map<String, Any>, timeoutHours: Long): UUID {
        val sagaId = UUID.randomUUID()
        
        // 1. Saga 인스턴스 생성
        val sagaInstance = SagaInstance(
            sagaId = sagaId,
            sagaType = sagaType,
            sagaStatus = SagaStatus.STARTED,
            correlationData = objectMapper.writeValueAsString(correlationData),
            timeoutAt = LocalDateTime.now().plusHours(timeoutHours)
        )
        
        sagaRepository.save(sagaInstance)
        
        // 2. 시작 이벤트 발행
        publishSagaEvent(sagaId, sagaType, "${sagaType}_STARTED", correlationData)
        
        // 3. 메트릭 업데이트
        sagaMetrics.incrementSagaStarted(sagaType)
        
        logger.info("Started saga: {} of type: {} with correlation: {}", 
                   sagaId, sagaType, correlationData)
        
        return sagaId
    }
    
    override fun updateSagaStep(sagaId: UUID, stepName: String, stepStatus: SagaStepStatus, stepData: Any?) {
        val saga = sagaRepository.findById(sagaId)
            ?: throw SagaNotFoundException("Saga not found: $sagaId")
        
        saga.updateStep(stepName, stepStatus, stepData)
        
        // Saga 상태 업데이트
        when (stepStatus) {
            SagaStepStatus.FAILED -> {
                saga.sagaStatus = SagaStatus.FAILED
                saga.failedStep = stepName
                saga.errorMessage = stepData?.toString()
                sagaMetrics.incrementSagaFailed(saga.sagaType, stepName)
                
                // 보상 트랜잭션 시작
                startCompensation(saga)
            }
            SagaStepStatus.COMPLETED -> {
                // 모든 단계가 완료되었는지 확인
                if (isAllStepsCompleted(saga)) {
                    completeSaga(sagaId, stepData)
                }
            }
            else -> {
                saga.sagaStatus = SagaStatus.IN_PROGRESS
            }
        }
        
        sagaRepository.save(saga)
        
        logger.debug("Updated saga {} step {} with status {}", sagaId, stepName, stepStatus)
    }
    
    private fun startCompensation(saga: SagaInstance) {
        saga.sagaStatus = SagaStatus.COMPENSATING
        
        // 보상 이벤트 발행
        val compensationData = mapOf(
            "sagaId" to saga.sagaId,
            "sagaType" to saga.sagaType,
            "failedStep" to saga.failedStep,
            "completedSteps" to objectMapper.readValue<List<String>>(saga.completedSteps)
        )
        
        publishSagaEvent(
            saga.sagaId, 
            saga.sagaType, 
            "${saga.sagaType}_COMPENSATION_STARTED", 
            compensationData
        )
    }
    
    private fun publishSagaEvent(sagaId: UUID, sagaType: String, eventType: String, data: Any) {
        val event = OutboxEvent(
            aggregateType = "SAGA",
            aggregateId = sagaId.toString(),
            eventType = eventType,
            eventData = objectMapper.writeValueAsString(data),
            sagaId = sagaId,
            sagaType = sagaType
        )
        
        outboxRepository.save(event)
    }
    
    @Scheduled(fixedDelay = 60000) // 1분마다 타임아웃 체크
    fun handleTimeouts() {
        val timedOutSagas = sagaRepository.findTimedOutSagas()
        
        timedOutSagas.forEach { saga ->
            logger.warn("Saga timeout detected: {} of type: {}", saga.sagaId, saga.sagaType)
            
            saga.sagaStatus = SagaStatus.FAILED
            saga.failedStep = saga.currentStep ?: "TIMEOUT"
            saga.errorMessage = "Saga timeout after ${saga.timeoutAt}"
            
            sagaRepository.save(saga)
            
            // 보상 시작
            startCompensation(saga)
            
            sagaMetrics.incrementSagaTimeout(saga.sagaType)
        }
    }
}
```

---

### **Step 2: 구체적인 Saga 구현**

#### **2.1 USER_REGISTRATION_SAGA 구현**

```kotlin
data class UserRegistrationContext(
    val authCode: String,
    val userInfo: GoogleUserInfo,
    val ipAddress: String
) : SagaContext(sagaType = "USER_REGISTRATION_SAGA")

data class GoogleUserInfo(
    val email: String,
    val name: String,
    val picture: String
)

@Component
class UserRegistrationSaga(
    private val userService: UserService,
    private val sagaCoordinator: SagaCoordinator,
    private val outboxService: OutboxService
) : Saga<UserRegistrationContext> {
    
    companion object {
        private val logger = LoggerFactory.getLogger(UserRegistrationSaga::class.java)
    }
    
    override fun start(context: UserRegistrationContext): SagaResult {
        logger.info("Starting User Registration Saga for email: {}", context.userInfo.email)
        
        try {
            // 1. Saga 시작 등록
            val sagaId = sagaCoordinator.startSaga(
                sagaType = context.sagaType,
                correlationData = mapOf(
                    "email" to context.userInfo.email,
                    "authCode" to context.authCode,
                    "ipAddress" to context.ipAddress
                ),
                timeoutHours = 1 // 1시간 타임아웃
            )
            context.sagaId = sagaId
            
            // 2. Step 1: 사용자 계정 생성
            val user = createUserAccount(context)
            sagaCoordinator.updateSagaStep(sagaId, "USER_ACCOUNT_CREATED", SagaStepStatus.COMPLETED, user.id)
            
            // 3. 후속 단계들은 이벤트 기반으로 처리
            publishUserRegisteredEvent(context, user)
            
            return SagaResult(
                sagaId = sagaId,
                status = SagaStatus.IN_PROGRESS,
                completedSteps = listOf("USER_ACCOUNT_CREATED"),
                result = user
            )
            
        } catch (ex: Exception) {
            logger.error("User Registration Saga failed", ex)
            sagaCoordinator.updateSagaStep(context.sagaId, "USER_ACCOUNT_CREATED", SagaStepStatus.FAILED, ex.message)
            
            return SagaResult(
                sagaId = context.sagaId,
                status = SagaStatus.FAILED,
                completedSteps = emptyList(),
                failedStep = "USER_ACCOUNT_CREATED",
                errorMessage = ex.message
            )
        }
    }
    
    @Transactional
    private fun createUserAccount(context: UserRegistrationContext): User {
        // 1. 중복 이메일 체크
        if (userService.existsByEmail(context.userInfo.email)) {
            throw UserAlreadyExistsException("User with email ${context.userInfo.email} already exists")
        }
        
        // 2. 사용자 엔티티 생성
        val user = User(
            email = context.userInfo.email,
            nickname = generateUniqueNickname(context.userInfo.name),
            profileImageUrl = context.userInfo.picture,
            provider = AuthProvider.GOOGLE,
            isActive = true
        )
        
        // 3. 데이터베이스에 저장
        val savedUser = userService.save(user)
        
        // 4. JWT 토큰 생성 (옵션)
        val token = jwtTokenProvider.generateToken(savedUser)
        
        logger.info("Created user account: {} with id: {}", savedUser.email, savedUser.id)
        
        return savedUser
    }
    
    private fun publishUserRegisteredEvent(context: UserRegistrationContext, user: User) {
        val eventData = UserRegisteredEventData(
            userId = user.id,
            email = user.email,
            nickname = user.nickname,
            profileImageUrl = user.profileImageUrl,
            provider = user.provider.name,
            registeredAt = user.createdAt
        )
        
        outboxService.publishEvent(
            aggregateType = "USER",
            aggregateId = user.id.toString(),
            eventType = "USER_REGISTERED",
            eventData = eventData,
            sagaId = context.sagaId,
            sagaType = context.sagaType
        )
    }
    
    // Event Listeners for subsequent steps
    
    @EventListener
    @Transactional
    fun handleAnalysisProfileCreated(event: AnalysisProfileCreatedEvent) {
        if (event.sagaId != null) {
            sagaCoordinator.updateSagaStep(
                event.sagaId, 
                "ANALYSIS_PROFILE_CREATED", 
                SagaStepStatus.COMPLETED,
                event.profileId
            )
            
            // 다음 단계 확인
            checkSagaCompletion(event.sagaId)
        }
    }
    
    @EventListener
    @Transactional
    fun handleWelcomeNotificationSent(event: WelcomeNotificationSentEvent) {
        if (event.sagaId != null) {
            sagaCoordinator.updateSagaStep(
                event.sagaId,
                "WELCOME_NOTIFICATION_SENT",
                SagaStepStatus.COMPLETED,
                event.notificationId
            )
            
            // 다음 단계 확인
            checkSagaCompletion(event.sagaId)
        }
    }
    
    private fun checkSagaCompletion(sagaId: UUID) {
        val saga = sagaCoordinator.getSagaStatus(sagaId)
        val completedSteps = saga?.completedSteps ?: emptyList()
        
        val requiredSteps = setOf(
            "USER_ACCOUNT_CREATED",
            "ANALYSIS_PROFILE_CREATED", 
            "WELCOME_NOTIFICATION_SENT"
        )
        
        if (completedSteps.containsAll(requiredSteps)) {
            sagaCoordinator.completeSaga(sagaId, "User registration completed successfully")
            logger.info("User Registration Saga completed: {}", sagaId)
        }
    }
    
    // Compensation Logic
    
    override fun compensate(sagaId: UUID, failedStep: String): CompensationResult {
        logger.warn("Starting compensation for User Registration Saga: {}, failed step: {}", sagaId, failedStep)
        
        return when (failedStep) {
            "ANALYSIS_PROFILE_CREATED" -> {
                compensateUserCreation(sagaId)
            }
            "WELCOME_NOTIFICATION_SENT" -> {
                // 알림은 보상이 필요 없음 (이미 발송된 알림은 취소할 수 없음)
                CompensationResult.success(sagaId, "Notification compensation not required")
            }
            else -> {
                CompensationResult.failure(sagaId, "Unknown failed step: $failedStep")
            }
        }
    }
    
    private fun compensateUserCreation(sagaId: UUID): CompensationResult {
        try {
            val saga = sagaCoordinator.getSagaStatus(sagaId)
            val correlationData = saga?.correlationData ?: return CompensationResult.failure(sagaId, "Saga not found")
            
            val email = correlationData["email"] as String
            val user = userService.findByEmail(email)
            
            if (user != null) {
                // 사용자 삭제
                userService.delete(user)
                
                // JWT 토큰 무효화 (Redis에서 블랙리스트 처리)
                jwtTokenProvider.blacklistUser(user.id)
                
                // 보상 이벤트 발행
                outboxService.publishEvent(
                    aggregateType = "USER",
                    aggregateId = user.id.toString(),
                    eventType = "USER_REGISTRATION_CANCELLED",
                    eventData = mapOf("reason" to "Analysis profile creation failed"),
                    sagaId = sagaId,
                    sagaType = "USER_REGISTRATION_SAGA"
                )
                
                logger.info("Compensated user creation for saga: {}, deleted user: {}", sagaId, user.id)
                return CompensationResult.success(sagaId, "User account deleted successfully")
            }
            
            return CompensationResult.success(sagaId, "User account not found, compensation not needed")
            
        } catch (ex: Exception) {
            logger.error("Failed to compensate user creation for saga: {}", sagaId, ex)
            return CompensationResult.failure(sagaId, "Compensation failed: ${ex.message}")
        }
    }
}

data class CompensationResult(
    val sagaId: UUID,
    val success: Boolean,
    val message: String,
    val compensatedAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun success(sagaId: UUID, message: String) = CompensationResult(sagaId, true, message)
        fun failure(sagaId: UUID, message: String) = CompensationResult(sagaId, false, message)
    }
}
```

#### **2.2 Event Handlers (다른 모듈들)**

```kotlin
// Analysis Module에서 USER_REGISTERED 이벤트 처리
@Component
class AnalysisEventHandler(
    private val analysisService: AnalysisService,
    private val outboxService: OutboxService
) {
    
    @KafkaListener(topics = ["USER_REGISTERED"])
    @Transactional
    fun handleUserRegistered(
        @Payload message: String,
        @Header headers: Map<String, Any>
    ) {
        try {
            val event = objectMapper.readValue<UserRegisteredEvent>(message)
            logger.info("Processing USER_REGISTERED event for user: {}", event.data.userId)
            
            // 분석 프로필 생성
            val profile = analysisService.createUserAnalysisProfile(
                userId = event.data.userId,
                email = event.data.email,
                nickname = event.data.nickname
            )
            
            // 성공 이벤트 발행
            outboxService.publishEvent(
                aggregateType = "ANALYSIS_PROFILE",
                aggregateId = profile.id.toString(),
                eventType = "ANALYSIS_PROFILE_CREATED",
                eventData = AnalysisProfileCreatedEventData(
                    userId = event.data.userId,
                    profileId = profile.id,
                    initializedAt = LocalDateTime.now()
                ),
                sagaId = event.sagaId,
                sagaType = event.sagaType
            )
            
            logger.info("Successfully created analysis profile: {} for user: {}", profile.id, event.data.userId)
            
        } catch (ex: Exception) {
            logger.error("Failed to process USER_REGISTERED event", ex)
            
            // 실패 이벤트 발행
            outboxService.publishEvent(
                aggregateType = "ANALYSIS_PROFILE",
                aggregateId = "failed",
                eventType = "ANALYSIS_PROFILE_CREATION_FAILED",
                eventData = mapOf(
                    "userId" to (headers["userId"] ?: "unknown"),
                    "error" to ex.message
                ),
                sagaId = headers["sagaId"] as UUID?,
                sagaType = headers["sagaType"] as String?
            )
            
            throw ex // 이벤트 재처리를 위해 예외 재발생
        }
    }
}

// Notification Module에서 USER_REGISTERED 이벤트 처리
@Component  
class NotificationEventHandler(
    private val notificationService: NotificationService,
    private val emailService: EmailService,
    private val outboxService: OutboxService
) {
    
    @KafkaListener(topics = ["USER_REGISTERED"])
    @Transactional
    fun handleUserRegistered(
        @Payload message: String,
        @Header headers: Map<String, Any>
    ) {
        try {
            val event = objectMapper.readValue<UserRegisteredEvent>(message)
            logger.info("Processing USER_REGISTERED event for notification: {}", event.data.userId)
            
            // 1. 알림 설정 생성
            val settings = notificationService.createDefaultSettings(event.data.userId)
            
            // 2. 환영 이메일 발송
            val notification = emailService.sendWelcomeEmail(
                to = event.data.email,
                nickname = event.data.nickname,
                userId = event.data.userId
            )
            
            // 3. 성공 이벤트 발행
            outboxService.publishEvent(
                aggregateType = "NOTIFICATION",
                aggregateId = notification.id.toString(),
                eventType = "WELCOME_NOTIFICATION_SENT",
                eventData = WelcomeNotificationSentEventData(
                    userId = event.data.userId,
                    notificationId = notification.id,
                    channel = NotificationChannel.EMAIL,
                    sentAt = LocalDateTime.now()
                ),
                sagaId = event.sagaId,
                sagaType = event.sagaType
            )
            
            logger.info("Successfully sent welcome notification: {} for user: {}", notification.id, event.data.userId)
            
        } catch (ex: Exception) {
            logger.error("Failed to process USER_REGISTERED event for notification", ex)
            
            // 실패 이벤트 발행 (필요시)
            outboxService.publishEvent(
                aggregateType = "NOTIFICATION",
                aggregateId = "failed",
                eventType = "WELCOME_NOTIFICATION_FAILED",
                eventData = mapOf(
                    "userId" to (headers["userId"] ?: "unknown"),
                    "error" to ex.message
                ),
                sagaId = headers["sagaId"] as UUID?,
                sagaType = headers["sagaType"] as String?
            )
            
            throw ex
        }
    }
}
```

---

## 🧪 **테스트 전략**

### **1. 단위 테스트**

```kotlin
@ExtendWith(MockitoExtension::class)
class UserRegistrationSagaTest {
    
    @Mock
    private lateinit var userService: UserService
    
    @Mock
    private lateinit var sagaCoordinator: SagaCoordinator
    
    @Mock
    private lateinit var outboxService: OutboxService
    
    @InjectMocks
    private lateinit var userRegistrationSaga: UserRegistrationSaga
    
    @Test
    fun `사용자 등록 Saga 성공 시나리오`() {
        // Given
        val context = UserRegistrationContext(
            authCode = "mock_auth_code",
            userInfo = GoogleUserInfo(
                email = "test@example.com",
                name = "테스트 사용자",
                picture = "https://example.com/profile.jpg"
            ),
            ipAddress = "127.0.0.1"
        )
        
        val mockUser = User(
            id = UUID.randomUUID(),
            email = "test@example.com",
            nickname = "테스트사용자123",
            profileImageUrl = "https://example.com/profile.jpg",
            provider = AuthProvider.GOOGLE,
            isActive = true
        )
        
        whenever(userService.existsByEmail("test@example.com")).thenReturn(false)
        whenever(userService.save(any<User>())).thenReturn(mockUser)
        whenever(sagaCoordinator.startSaga(any(), any(), any())).thenReturn(UUID.randomUUID())
        
        // When
        val result = userRegistrationSaga.start(context)
        
        // Then
        assertThat(result.status).isEqualTo(SagaStatus.IN_PROGRESS)
        assertThat(result.completedSteps).contains("USER_ACCOUNT_CREATED")
        
        verify(userService).save(any<User>())
        verify(outboxService).publishEvent(
            aggregateType = "USER",
            aggregateId = mockUser.id.toString(),
            eventType = "USER_REGISTERED",
            eventData = any(),
            sagaId = any(),
            sagaType = "USER_REGISTRATION_SAGA"
        )
    }
    
    @Test
    fun `중복 이메일로 인한 실패 시나리오`() {
        // Given
        val context = UserRegistrationContext(
            authCode = "mock_auth_code",
            userInfo = GoogleUserInfo(
                email = "existing@example.com",
                name = "기존 사용자",
                picture = "https://example.com/profile.jpg"
            ),
            ipAddress = "127.0.0.1"
        )
        
        whenever(userService.existsByEmail("existing@example.com")).thenReturn(true)
        whenever(sagaCoordinator.startSaga(any(), any(), any())).thenReturn(UUID.randomUUID())
        
        // When
        val result = userRegistrationSaga.start(context)
        
        // Then
        assertThat(result.status).isEqualTo(SagaStatus.FAILED)
        assertThat(result.errorMessage).contains("already exists")
        assertThat(result.failedStep).isEqualTo("USER_ACCOUNT_CREATED")
        
        verify(userService, never()).save(any<User>())
        verify(sagaCoordinator).updateSagaStep(any(), eq("USER_ACCOUNT_CREATED"), eq(SagaStepStatus.FAILED), any())
    }
}
```

### **2. 통합 테스트**

```kotlin
@SpringBootTest
@Transactional
@TestPropertySource(properties = [
    "kafka.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
])
class UserRegistrationSagaIntegrationTest {
    
    @Autowired
    private lateinit var userRegistrationSaga: UserRegistrationSaga
    
    @Autowired
    private lateinit var userRepository: UserRepository
    
    @Autowired
    private lateinit var sagaRepository: SagaInstanceRepository
    
    @Autowired
    private lateinit var outboxRepository: OutboxEventRepository
    
    @MockBean
    private lateinit var analysisService: AnalysisService
    
    @MockBean
    private lateinit var notificationService: NotificationService
    
    @Test
    @Rollback(false) // 실제 데이터베이스 상태 확인을 위해
    fun `전체 사용자 등록 플로우 통합 테스트`() {
        // Given
        val context = UserRegistrationContext(
            authCode = "integration_test_code",
            userInfo = GoogleUserInfo(
                email = "integration@test.com",
                name = "통합테스트",
                picture = "https://test.com/profile.jpg"
            ),
            ipAddress = "127.0.0.1"
        )
        
        // Mock 분석 서비스 응답
        val mockAnalysisProfile = AnalysisProfile(id = UUID.randomUUID(), userId = UUID.randomUUID())
        whenever(analysisService.createUserAnalysisProfile(any(), any(), any())).thenReturn(mockAnalysisProfile)
        
        // Mock 알림 서비스 응답
        val mockNotification = Notification(id = UUID.randomUUID(), userId = UUID.randomUUID())
        whenever(notificationService.createDefaultSettings(any())).thenReturn(NotificationSettings())
        
        // When
        val result = userRegistrationSaga.start(context)
        
        // Then - 즉시 확인 가능한 것들
        assertThat(result.status).isIn(SagaStatus.IN_PROGRESS, SagaStatus.COMPLETED)
        assertThat(result.sagaId).isNotNull()
        
        // 데이터베이스 상태 확인
        val savedUser = userRepository.findByEmail("integration@test.com")
        assertThat(savedUser).isNotNull()
        assertThat(savedUser!!.nickname).isNotEmpty()
        assertThat(savedUser.isActive).isTrue()
        
        val sagaInstance = sagaRepository.findById(result.sagaId)
        assertThat(sagaInstance).isPresent()
        assertThat(sagaInstance.get().sagaType).isEqualTo("USER_REGISTRATION_SAGA")
        assertThat(sagaInstance.get().sagaStatus).isIn(SagaStatus.IN_PROGRESS, SagaStatus.COMPLETED)
        
        // Outbox 이벤트 확인
        val outboxEvents = outboxRepository.findBySagaId(result.sagaId)
        assertThat(outboxEvents).hasSizeGreaterThan(0)
        assertThat(outboxEvents).anyMatch { it.eventType == "USER_REGISTERED" }
        
        // 비동기 처리 대기 (실제 환경에서는 이벤트 처리 시간 고려)
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val updatedSaga = sagaRepository.findById(result.sagaId).orElseThrow()
            val completedSteps = objectMapper.readValue<List<String>>(updatedSaga.completedSteps)
            
            // 모든 단계가 완료되었는지 확인
            assertThat(completedSteps).containsAll(listOf(
                "USER_ACCOUNT_CREATED",
                "ANALYSIS_PROFILE_CREATED", 
                "WELCOME_NOTIFICATION_SENT"
            ))
            
            assertThat(updatedSaga.sagaStatus).isEqualTo(SagaStatus.COMPLETED)
        }
    }
    
    @Test
    fun `분석 프로필 생성 실패 시 보상 트랜잭션 테스트`() {
        // Given
        val context = UserRegistrationContext(
            authCode = "compensation_test_code",
            userInfo = GoogleUserInfo(
                email = "compensation@test.com",
                name = "보상테스트",
                picture = "https://test.com/profile.jpg"
            ),
            ipAddress = "127.0.0.1"
        )
        
        // 분석 서비스에서 예외 발생 시뮬레이션
        whenever(analysisService.createUserAnalysisProfile(any(), any(), any()))
            .thenThrow(RuntimeException("Analysis service unavailable"))
        
        // When
        val result = userRegistrationSaga.start(context)
        
        // 비동기 이벤트 처리로 인한 최종 상태 대기
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val sagaInstance = sagaRepository.findById(result.sagaId).orElseThrow()
            
            // Then
            assertThat(sagaInstance.sagaStatus).isEqualTo(SagaStatus.COMPENSATED)
            assertThat(sagaInstance.failedStep).isEqualTo("ANALYSIS_PROFILE_CREATED")
            
            // 사용자가 삭제되었는지 확인 (보상 트랜잭션)
            val user = userRepository.findByEmail("compensation@test.com")
            assertThat(user).isNull()
            
            // 보상 관련 이벤트 확인
            val outboxEvents = outboxRepository.findBySagaId(result.sagaId)
            assertThat(outboxEvents).anyMatch { it.eventType == "USER_REGISTRATION_CANCELLED" }
        }
    }
}
```

### **3. 성능 테스트**

```kotlin
@SpringBootTest
@TestPropertySource(properties = [
    "kafka.enabled=true",
    "logging.level.com.algoreport=WARN" // 로그 레벨 낮춰서 성능 확보
])
class SagaPerformanceTest {
    
    @Autowired
    private lateinit var userRegistrationSaga: UserRegistrationSaga
    
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `동시 사용자 등록 Saga 처리 성능 테스트`() {
        val userCount = 100
        val latch = CountDownLatch(userCount)
        val results = ConcurrentHashMap<Int, SagaResult>()
        val executor = Executors.newFixedThreadPool(10)
        
        val startTime = System.currentTimeMillis()
        
        try {
            // 100명의 사용자 동시 등록
            repeat(userCount) { index ->
                executor.submit {
                    try {
                        val context = UserRegistrationContext(
                            authCode = "perf_test_$index",
                            userInfo = GoogleUserInfo(
                                email = "perf_test_$index@example.com",
                                name = "성능테스트$index",
                                picture = "https://example.com/profile_$index.jpg"
                            ),
                            ipAddress = "127.0.0.1"
                        )
                        
                        val result = userRegistrationSaga.start(context)
                        results[index] = result
                        
                    } catch (ex: Exception) {
                        logger.error("Performance test failed for user $index", ex)
                    } finally {
                        latch.countDown()
                    }
                }
            }
            
            // 모든 요청 완료 대기
            latch.await(25, TimeUnit.SECONDS)
            
            val endTime = System.currentTimeMillis()
            val totalTime = endTime - startTime
            
            // 성능 검증
            assertThat(results).hasSize(userCount)
            assertThat(totalTime).isLessThan(25000) // 25초 이내
            
            val avgTimePerUser = totalTime.toDouble() / userCount
            assertThat(avgTimePerUser).isLessThan(250.0) // 사용자당 평균 250ms 이내
            
            // 성공률 검증
            val successfulSagas = results.values.count { 
                it.status == SagaStatus.IN_PROGRESS || it.status == SagaStatus.COMPLETED 
            }
            val successRate = successfulSagas.toDouble() / userCount
            assertThat(successRate).isGreaterThan(0.95) // 95% 이상 성공률
            
            logger.info("Performance test results:")
            logger.info("Total users: {}", userCount)
            logger.info("Total time: {}ms", totalTime)
            logger.info("Average time per user: {}ms", avgTimePerUser)
            logger.info("Success rate: {}%", successRate * 100)
            
        } finally {
            executor.shutdown()
        }
    }
    
    @Test
    fun `Saga 메모리 사용량 테스트`() {
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // 대량의 Saga 실행
        val sagaCount = 1000
        val results = mutableListOf<SagaResult>()
        
        repeat(sagaCount) { index ->
            val context = UserRegistrationContext(
                authCode = "memory_test_$index",
                userInfo = GoogleUserInfo(
                    email = "memory_test_$index@example.com",
                    name = "메모리테스트$index",
                    picture = "https://example.com/profile_$index.jpg"
                ),
                ipAddress = "127.0.0.1"
            )
            
            val result = userRegistrationSaga.start(context)
            results.add(result)
            
            // 주기적으로 GC 실행
            if (index % 100 == 0) {
                System.gc()
                Thread.sleep(100)
            }
        }
        
        System.gc()
        Thread.sleep(1000) // GC 완료 대기
        
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = (finalMemory - initialMemory) / 1024 / 1024 // MB 단위
        
        // 메모리 사용량 검증 (Saga당 평균 1MB 미만)
        val avgMemoryPerSaga = memoryIncrease.toDouble() / sagaCount
        assertThat(avgMemoryPerSaga).isLessThan(1.0)
        
        logger.info("Memory usage test results:")
        logger.info("Saga count: {}", sagaCount)
        logger.info("Initial memory: {}MB", initialMemory / 1024 / 1024)
        logger.info("Final memory: {}MB", finalMemory / 1024 / 1024)
        logger.info("Memory increase: {}MB", memoryIncrease)
        logger.info("Average memory per saga: {}KB", avgMemoryPerSaga * 1024)
    }
}
```

---

## 📊 **모니터링 및 운영 (신규 전략: Elastic APM)**

**핵심 전략 변경**: 기존의 `SAGA_INSTANCES` 테이블을 이용한 상태 추적 방식에서, **Elastic APM을 활용한 분산 추적(Distributed Tracing)** 방식으로 전환합니다. 이 방식은 더 적은 코드 침투로 더 강력한 관측 가능성을 제공합니다.

### **1. 분산 추적을 통한 Saga 모니터링**

- **Trace ID (Correlation ID)**: APM 에이전트가 모든 비즈니스 트랜잭션의 시작점에서 고유한 `Trace ID`를 생성합니다. 이 ID는 모든 이벤트와 모듈 간 호출에 걸쳐 자동으로 전파됩니다.
- **Spans**: 각 모듈에서 수행되는 작업(DB 쿼리, 이벤트 발행 등)은 `Span`으로 측정되어 `Trace ID`에 연결됩니다.
- **시각화**: Kibana APM 대시보드에서 전체 트랜잭션 흐름을 폭포수(Waterfall) 차트로 시각화하여 볼 수 있습니다. 이를 통해 어떤 단계에서 병목이 발생하거나 실패했는지 직관적으로 파악할 수 있습니다.

### **2. 주요 모니터링 시나리오**

- **성공한 Saga 흐름 분석**: Kibana에서 특정 `Trace ID`를 검색하여, `USER_REGISTERED` 이벤트부터 `WELCOME_NOTIFICATION_SENT` 이벤트까지 전체 흐름과 각 단계별 소요 시간을 확인합니다.
- **실패한 Saga 추적**: 에러가 발생한 트랜잭션을 필터링하고, 어떤 `Span`에서 예외가 발생했는지, 당시의 컨텍스트(파라미터, 쿼리 등)는 무엇이었는지 상세히 분석합니다.
- **성능 병목 식별**: 트랜잭션 목록에서 평균 소요 시간이 가장 긴 트랜잭션을 찾아내고, 내부 Span들을 분석하여 DB 쿼리가 느린지, 특정 로직이 오래 걸리는지 등을 파악합니다.

### **3. 로그와 추적 데이터의 연동**

APM 에이전트는 모든 로그 메시지에 `trace.id`와 `transaction.id`를 자동으로 추가합니다. 이를 통해 다음과 같은 강력한 분석이 가능해집니다.

```json
// Kibana에서 볼 수 있는 로그 예시
{
  "@timestamp": "2025-07-24T10:30:00.123Z",
  "log.level": "ERROR",
  "message": "Failed to process event ANALYSIS_PROFILE_CREATED",
  "service.name": "algoreport-backend",
  "trace.id": "a1b2c3d4e5f6...", // <-- 이 ID로 전체 흐름 추적
  "transaction.id": "f6e5d4c3b2a1...",
  "error.message": "User not found in cache"
}
```

> 특정 에러 로그를 발견하면, `trace.id`를 클릭하는 것만으로 해당 에러가 발생한 전체 분산 트랜잭션의 타임라인으로 즉시 이동할 수 있습니다.
```

---

## 🚀 **배포 및 운영 가이드**

### **1. 환경별 설정**

```yaml
# application-production.yml
saga:
  timeout:
    user-registration: 2h
    group-join: 1h
    leave-group: 30m
  retry:
    max-attempts: 5
    backoff-multiplier: 2.0
    max-delay: 300000 # 5분
  monitoring:
    metrics-enabled: true
    dashboard-enabled: true
  recovery:
    auto-recovery-enabled: true
    recovery-check-interval: 300000 # 5분

outbox:
  publisher:
    fixed-delay: 5000
    batch-size: 100
  cleanup:
    retention-days: 30
    cleanup-interval: 86400000 # 1일

kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  producer:
    retries: 3
    acks: all
    batch-size: 16384
  consumer:
    group-id: algoreport-sagas
    auto-offset-reset: earliest
    max-poll-records: 10
```

### **2. 모니터링 설정**

```yaml
# Prometheus 설정 (prometheus.yml)
scrape_configs:
  - job_name: 'algoreport-saga-metrics'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s

# Grafana 대시보드 쿼리 예제
# Saga 성공률
(saga_completed_total / (saga_started_total)) * 100

# 평균 Saga 실행 시간
rate(saga_duration_seconds_sum[5m]) / rate(saga_duration_seconds_count[5m])

# 실패율이 높은 Saga 타입
topk(5, rate(saga_failed_total[5m]))
```

### **3. 로그 설정**

```xml
<!-- logback-spring.xml -->
<configuration>
    <springProfile name="production">
        <appender name="SAGA_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <file>logs/saga.log</file>
            <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                <fileNamePattern>logs/saga.%d{yyyy-MM-dd}.%i.gz</fileNamePattern>
                <maxFileSize>100MB</maxFileSize>
                <maxHistory>30</maxHistory>
            </rollingPolicy>
            <encoder>
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{sagaId}] %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        
        <logger name="com.algoreport.saga" level="INFO" additivity="false">
            <appender-ref ref="SAGA_FILE"/>
            <appender-ref ref="CONSOLE"/>
        </logger>
    </springProfile>
</configuration>
```

### **4. 운영 체크리스트**

#### **배포 전 점검**
- [ ] 모든 Saga 단위 테스트 통과
- [ ] 통합 테스트 100% 성공률
- [ ] 성능 테스트 기준 만족
- [ ] 데이터베이스 마이그레이션 스크립트 준비
- [ ] Kafka 토픽 생성 확인
- [ ] 모니터링 대시보드 설정 완료

#### **배포 후 확인**
- [ ] 모든 서비스 정상 기동
- [ ] Saga 메트릭 수집 정상 동작
- [ ] Outbox Publisher 정상 동작
- [ ] 이벤트 발행/구독 정상 동작
- [ ] 데이터베이스 연결 정상
- [ ] 실시간 모니터링 대시보드 확인

#### **일일 운영 체크**
- [ ] 실패한 Saga 없는지 확인
- [ ] 장시간 실행 중인 Saga 확인
- [ ] Outbox 적체 이벤트 확인
- [ ] 시스템 리소스 사용률 확인
- [ ] 알림 및 경고 메시지 확인

---

📝 **문서 버전**: v1.0  
📅 **최종 수정일**: 2025-07-22  
👤 **작성자**: 채기훈