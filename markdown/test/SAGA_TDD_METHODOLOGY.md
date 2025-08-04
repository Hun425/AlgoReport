# Saga 패턴 특화 TDD 방법론

이 문서는 **알고리포트의 15개 Saga에 대한 체계적이고 실전적인 TDD 접근법**을 제시합니다. 분산 트랜잭션의 복잡성을 고려하여 단계별 테스트 전략과 실제 구현 예시를 포함합니다.

---

## 🎯 **Saga TDD의 핵심 원칙**

### **1. 테스트 우선 개발 (Test-First Development)**
- Saga 시나리오를 먼저 테스트로 정의
- 비즈니스 요구사항을 실행 가능한 테스트로 변환
- 보상 트랜잭션까지 포함한 완전한 테스트 커버리지

### **2. 계층적 테스트 접근법 (테스트 피라미드)**
- **Level 4: E2E 테스트 (5%)** - 실제 사용자 시나리오 검증
- **Level 3: Saga 통합 테스트 (20%)** - Saga와 실제 의존성(DB, 외부 API Mock) 연동 검증
- **Level 2: Saga 단위 테스트 (35%)** - Saga의 내부 로직과 흐름을 Mock을 통해 검증
- **Level 1: 컴포넌트 단위 테스트 (40%)** - Saga를 구성하는 각 서비스, 레포지토리의 개별 로직 검증

### **3. 실패 우선 설계 (Failure-First Design)**
- Happy Path보다 실패 시나리오를 먼저 테스트
- 보상 트랜잭션의 멱등성 검증
- 부분 실패 상황의 일관성 보장

---

## 📋 **Saga TDD 워크플로우**

(기존 내용 유지)

### **Phase 3: 단계별 TDD 구현**

#### **3.1 Level 1: 컴포넌트 단위 테스트 (Component Unit Tests)**
Saga를 구성하는 각 서비스(`UserService`, `AnalysisService` 등)와 리포지토리의 개별 메서드를 테스트합니다. 이 테스트들은 다른 컴포넌트를 Mocking하여 순수하게 해당 컴포넌트의 로직만 검증합니다.

```kotlin
// 예시: UserService의 단위 테스트
class UserServiceTest {
    @Test
    fun `구글_사용자_정보로_사용자_엔티티를_생성할_수_있다`() {
        // ... (기존 내용과 동일)
    }
}
```

#### **3.2 Level 2: Saga 단위 테스트 (Saga Unit Tests) - 신규 추가 및 강조**

**목적: Saga의 오케스트레이션 로직 자체를 검증합니다.**
- `@SpringBootTest` 없이 실행하여 **매우 빠른 피드백**을 얻습니다.
- Saga가 의존하는 모든 컴포넌트(`UserRepository`, `AnalysisService` 등)를 **Mock 객체로 대체**합니다.
- **검증 대상:**
    - 올바른 순서로 의존성 메서드를 호출하는가?
    - 특정 조건에 따라 분기 처리를 올바르게 하는가?
    - 예외 발생 시 보상 로직을 정확히 호출하는가?
    - DTO를 올바르게 생성하고 전달하는가?

```kotlin
// 예시: PersonalStatsRefreshSaga의 순수 단위 테스트
@ExtendWith(MockitoExtension::class)
class PersonalStatsRefreshSagaUnitTest {

    @Mock
    private lateinit var userRepository: UserRepository
    @Mock
    private lateinit var analysisService: AnalysisService
    @Mock
    private lateinit var analysisCacheService: AnalysisCacheService
    @Mock
    private lateinit var elasticsearchService: ElasticsearchService
    @Mock
    private lateinit var solvedacApiClient: SolvedacApiClient
    @Mock
    private lateinit var outboxService: OutboxService

    @InjectMocks
    private lateinit var personalStatsRefreshSaga: PersonalStatsRefreshSaga

    @Test
    fun `Saga 시작 시 사용자가 존재하지 않으면 즉시 실패하고 보상 트랜잭션이 호출되어야 한다`() {
        // Given: Mock 설정
        val request = PersonalStatsRefreshRequest(userId = "non-existent-user")
        whenever(userRepository.findAllActiveUserIds()).thenReturn(emptyList())

        // When: Saga 실행
        val result = personalStatsRefreshSaga.start(request)

        // Then: 결과 검증
        assertThat(result.sagaStatus).isEqualTo(SagaStatus.FAILED)
        assertThat(result.errorMessage).contains("User not found")
        assertThat(result.compensationExecuted).isTrue()

        // Then: 상호작용 검증
        verify(analysisService).deletePersonalAnalysis("non-existent-user")
        verify(analysisCacheService).evictPersonalAnalysis("non-existent-user")
        verify(outboxService).publishEvent(
            eventType = "PERSONAL_STATS_REFRESH_COMPENSATED",
            aggregateId = "non-existent-user",
            // ...
        )
        verify(solvedacApiClient, never()).getSubmissions(any(), any()) // 데이터 수집 로직은 호출되지 않아야 함
    }
    
    @Test
    fun `Elasticsearch 인덱싱 실패 시 부분 성공으로 완료되고 보상 트랜잭션은 실행되지 않아야 한다`() {
        // Given
        val request = PersonalStatsRefreshRequest(userId = "test-user")
        whenever(userRepository.findAllActiveUserIds()).thenReturn(listOf("test-user"))
        whenever(elasticsearchService.indexPersonalAnalysis(any())).thenThrow(RuntimeException("ES connection failed"))
        // ... 기타 성공 경로 Mock 설정 ...

        // When
        val result = personalStatsRefreshSaga.start(request)

        // Then
        assertThat(result.sagaStatus).isEqualTo(SagaStatus.PARTIAL_SUCCESS)
        assertThat(result.elasticsearchIndexingCompleted).isFalse()
        assertThat(result.compensationExecuted).isFalse() // 보상 로직은 호출되면 안 됨

        verify(analysisService, never()).deletePersonalAnalysis(any()) // 보상 로직 미호출 검증
    }
}
```

#### **3.3 Level 3: Saga 통합 테스트 (Saga Integration Tests)**
**목적: Saga가 실제 스프링 컨텍스트 내에서 다른 컴포넌트들과 올바르게 연동되는지 검증합니다.**
- `@SpringBootTest`를 사용하여 테스트용 컨텍스트를 로드합니다.
- 데이터베이스, 캐시 등은 내장(Embedded) 버전을 사용하거나 Testcontainers를 활용합니다.
- 외부 API와 같이 제어하기 어려운 의존성은 `@MockBean`으로 대체합니다.
- **검증 대상:**
    - Saga 실행 후 DB 상태가 올바르게 변경되었는가?
    - 캐시에 데이터가 정상적으로 저장/삭제되었는가?
    - Outbox 테이블에 이벤트가 정확히 기록되었는가?

```kotlin
// 예시: PersonalStatsRefreshSaga의 통합 테스트
@SpringBootTest
@ActiveProfiles("test")
class PersonalStatsRefreshSagaIntegrationTest {

    @Autowired
    private lateinit var personalStatsRefreshSaga: PersonalStatsRefreshSaga
    
    @Autowired
    private lateinit var userRepository: UserRepository // 실제 DB와 연동
    
    @MockBean
    private lateinit var solvedacApiClient: SolvedacApiClient // 외부 API는 Mock

    @Test
    fun `Saga 전체 플로우 성공 시 개인 분석 결과가 DB에 저장되고 캐시가 업데이트되어야 한다`() {
        // Given
        val user = userRepository.save(User(email = "test@test.com", ...))
        val request = PersonalStatsRefreshRequest(userId = user.id)
        whenever(solvedacApiClient.getSubmissions(any(), any())).thenReturn(emptySubmissionList())

        // When
        personalStatsRefreshSaga.start(request)

        // Then
        val analysis = analysisRepository.findByUserId(user.id)
        assertThat(analysis).isNotNull()

        val cachedAnalysis = analysisCacheService.getPersonalAnalysisFromCache(user.id)
        assertThat(cachedAnalysis).isNotNull()
        assertThat(cachedAnalysis.userId).isEqualTo(user.id)
    }
}
```

#### **3.4 Level 4: 전체 Saga 시나리오 테스트 (E2E)**
(기존 내용 유지)

---
(이하 문서의 나머지 부분은 기존 내용 유지)
