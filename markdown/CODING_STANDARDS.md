# 코딩 표준 및 컨벤션

## 1. 코드 스타일 (Kotlin)

### 1.1 네이밍 컨벤션

- **클래스**: PascalCase (`StudyGroupService`)
    
- **함수/변수**: camelCase (`createStudyGroup`)
    
- **상수**: UPPER_SNAKE_CASE (`MAX_GROUP_MEMBERS`)
    
- **패키지**: lowercase (`com.algoreport.module.studygroup`)
    

### 1.2 클래스/함수 문서화 (KDoc)

```
/**
 * 스터디 그룹 관련 비즈니스 로직을 처리하는 서비스
 *
 * @property studyGroupRepository 스터디 그룹 데이터 접근을 위한 리포지토리
 * @property userRepository 사용자 데이터 접근을 위한 리포지토리
 */
class StudyGroupService(
    private val studyGroupRepository: StudyGroupRepository,
    private val userRepository: UserRepository
) {
    /**
     * 새로운 스터디 그룹을 생성한다.
     *
     * @param ownerId 그룹을 생성하는 사용자의 ID
     * @param requestDto 그룹 생성을 위한 요청 데이터
     * @return 생성된 스터디 그룹 엔티티
     * @throws CustomException 사용자를 찾을 수 없을 경우
     */
    fun createStudyGroup(ownerId: Long, requestDto: CreateStudyGroupRequest): StudyGroup {
        // ...
    }
}
```

## 2. 테스트 코드 작성 규칙

### 2.1 테스트 클래스 네이밍

- `[대상클래스명]Test.kt`
    

### 2.2 테스트 함수 네이밍

- `@DisplayName`을 사용하여 한글로 명확하게 기술
    
- 함수명은 `[테스트상황]_[예상결과]` 형식으로 작성
    
    - 예: `joinStudyGroup_whenAlreadyJoined_shouldThrowException()`
        

### 2.3 Kotest BehaviorSpec 사용 규칙 (필수)

**🚨 중요: 모든 테스트는 Kotest BehaviorSpec을 사용합니다** (JUnit 5 금지)
**🔥 주의: 아래 데이터 생명주기 규칙을 지키지 않으면 100% 테스트 실패합니다!**

#### **Kotest BehaviorSpec 실행 순서 및 데이터 생명주기 (필수 숙지)**

**실행 순서:**
1. `beforeEach` → 각 `then` 블록 실행 전마다 호출
2. `given` 블록 → 테스트 컨텍스트 정의 (데이터 생성 위치 주의!)
3. `when` 블록 → 테스트 시나리오 정의
4. `then` 블록 → 실제 테스트 실행 및 검증 (독립적으로 실행)
5. `afterEach` → 각 `then` 블록 실행 후마다 호출

**⚠️ 자주 발생하는 함정들:**

```kotlin
// ❌ 잘못된 방식 - 데이터 생명주기 오류
init {
    beforeEach {
        userService.clear() // 모든 then 실행 전마다 데이터 삭제!
    }
    
    given("사용자가 존재할 때") {
        val testUser = userService.createUser(...) // 여기서 생성하지만...
        val userId = testUser.id
        
        then("테스트 1") {
            // beforeEach에서 이미 삭제됨! userId는 존재하지 않는 사용자 ID
            val result = someService.doSomething(userId) // 💥 실패
        }
        
        then("테스트 2") {
            // 이 테스트도 마찬가지로 beforeEach에서 데이터 삭제됨
            val result = someService.doSomething(userId) // 💥 실패
        }
    }
}

// ✅ 올바른 방식 1 - 각 then 블록에서 데이터 생성
init {
    beforeEach {
        userService.clear()
    }
    
    given("사용자가 존재할 때") {
        then("테스트 1") {
            val testUser = userService.createUser(...) // then 블록 내부에서 생성
            val userId = testUser.id
            
            val result = someService.doSomething(userId) // ✅ 성공
        }
        
        then("테스트 2") {
            val testUser = userService.createUser(...) // 독립적으로 생성
            val userId = testUser.id
            
            val result = someService.doSomething(userId) // ✅ 성공
        }
    }
}

// ✅ 올바른 방식 2 - beforeEach를 given 내부로 이동
init {
    given("사용자가 존재할 때") {
        beforeEach {
            userService.clear() // given 스코프 내에서만 실행
        }
        
        val testUser = userService.createUser(...)
        val userId = testUser.id
        
        then("테스트 1") {
            val result = someService.doSomething(userId) // ✅ 성공
        }
    }
}
```

#### **📋 Kotest BehaviorSpec 체크리스트 (작업 전 필수 확인)**

- [ ] `beforeEach`/`afterEach`가 어느 스코프에 있는지 확인했는가?
- [ ] 테스트 데이터를 `given` 블록에서 생성하는 경우, `beforeEach`에서 삭제되지 않는지 확인했는가?
- [ ] 각 `then` 블록이 독립적으로 실행됨을 이해하고 있는가?
- [ ] 테스트 간 데이터 공유가 필요한 경우 적절한 스코프에 배치했는가?
- [ ] `BehaviorSpec` 상속 및 `SpringExtension` 추가했는가?
- [ ] `given-when-then` BDD 스타일을 사용했는가?
- [ ] `shouldBe, shouldNotBe` 등 Kotest 매처를 사용했는가?

### 2.4 테스트 구조 (Given-When-Then)

```kotlin
// ✅ 올바른 Kotest 테스트 작성법
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest(
    private val userService: UserService
) : BehaviorSpec() {
    
    override fun extensions() = listOf(SpringExtension)
    
    init {
        beforeEach {
            userService.clear() // 각 then 블록 전에 초기화
        }
        
        given("사용자가 회원가입할 때") {
            `when`("유효한 정보를 제공하면") {
                then("사용자가 정상적으로 저장되어야 한다") {
                    // 각 then 블록에서 독립적으로 데이터 생성
                    val userData = UserCreateRequest("test@example.com", "닉네임", AuthProvider.GOOGLE)
                    val savedUser = userService.createUser(userData)
                    
                    savedUser.id shouldNotBe null
                    savedUser.email shouldBe "test@example.com"
                }
            }
        }
    }
}
```

## 3. 비즈니스 로직 구현 규칙

### 3.1 불변성(Immutability) 지향

- `val`을 우선적으로 사용하고, 변경이 꼭 필요한 경우에만 `var` 사용
    
- 엔티티의 상태 변경은 명확한 의도를 가진 메서드를 통해 수행
    

### 3.2 Null 안전성

- Kotlin의 Nullable 타입(`?`)을 적극 활용하여 NPE 방지
    
- `?.`(Safe Call)와 `?:`(Elvis Operator)를 적절히 사용
    

## 4. TODO 주석 활용 규칙

### 4.1 TDD 단계별 TODO 주석 (필수)

**목적**: TDD 진행 상황을 명확히 추적하고 다음 할 일을 체계적으로 관리

**컨벤션**:
```kotlin
// TODO: [RED] - 테스트 작성 단계에서 해야 할 일
// TODO: [GREEN] - 기본 구현 단계에서 해야 할 일  
// TODO: [REFACTOR] - 리팩토링 단계에서 해야 할 일
// TODO: [BUG] - 버그 수정이 필요한 부분
// TODO: [OPTIMIZE] - 성능 최적화가 필요한 부분
// TODO: [DOCS] - 문서화가 필요한 부분
```

**작성 규칙**:
- 구체적이고 실행 가능한 내용으로 작성
- 다른 개발자가 봐도 이해할 수 있게 명확히 기술
- 관련된 TODO는 같은 위치에 그룹화
- 완료된 TODO는 즉시 제거하거나 `✅ GREEN:` 형태로 완료 표시

**예시**:
```kotlin
class UserService {
    fun updateUserProfile(userId: String, profileData: ProfileData): User {
        // TODO: [GREEN] 사용자 존재 여부 검증 로직 구현 필요
        // TODO: [GREEN] 프로필 데이터 유효성 검사 로직 구현 필요
        // TODO: [REFACTOR] 트랜잭션 처리 및 예외 핸들링 강화 필요
        
        val user = userRepository.findById(userId) // TODO: [GREEN] null 체크 추가
        return user.copy(profile = profileData)    // TODO: [GREEN] 실제 업데이트 로직 구현
    }
}
```

### 4.2 TODO 주석 관리

- **작업 시작 전**: 현재 단계에 맞는 TODO 확인 및 계획 수립
- **작업 진행 중**: 새로 발견된 작업은 적절한 TODO로 즉시 추가
- **작업 완료 후**: 완료된 TODO 정리 및 다음 단계 TODO 준비
- **코드 리뷰 시**: TODO 주석의 적절성과 완료도 함께 검토

## 5. 커밋 메시지 규칙

### 5.1 TDD 사이클별 커밋 메시지

```
🔴 Red:     test: Red - [설명]
🟢 Green:   feat: Green - [설명]
🔵 Refactor: refactor: Refactor - [설명]
```

### 5.2 일반 커밋 메시지

```
feat: 새로운 기능 추가
fix: 버그 수정
docs: 문서 수정
style: 코드 포맷팅
refactor: 코드 리팩토링
test: 테스트 코드
chore: 빌드 설정 등
```

---

## 6. 테스트 전략: 단위 테스트 vs 통합 테스트

### 6.1 알고리포트 프로젝트 테스트 방침

**기본 원칙**: 모든 테스트는 **단위 테스트(Unit Test)** 우선으로 작성하며, Redis/Kafka 등 외부 인프라는 Mock으로 대체합니다.

#### **6.1.1 단위 테스트 (현재 사용 중) ✅**

**적용 범위**: 모든 비즈니스 로직, 서비스 레이어, SAGA 패턴
**외부 의존성**: Mock 사용 (Redis, Kafka, solved.ac API 등)
**테스트 프레임워크**: Kotest BehaviorSpec

**장점**:
- ⚡ **빠른 실행**: 전체 테스트 < 30초
- 🎯 **비즈니스 로직 집중**: 인프라 문제에 방해받지 않음
- 🔒 **높은 안정성**: 외부 환경 변화에 영향받지 않음
- 🚀 **효율적인 TDD**: Red-Green-Refactor 사이클이 2-5초

#### **6.1.2 통합 테스트 (선택적 사용) ⚠️**

**적용 범위**: 핵심 비즈니스 플로우 검증 (필요시에만)
**외부 의존성**: TestContainers 사용
**실행 빈도**: CI/CD 파이프라인에서만

**사용 시점**:
- 매우 중요한 데이터 플로우 (예: ANALYSIS_UPDATE_SAGA 전체 플로우)
- 성능 검증이 필요한 경우
- 프로덕션 배포 전 최종 검증

### 6.2 분석 모듈 테스트 전략 (구체적 예시)

#### **6.2.1 AnalysisCacheService - 단위 테스트 ✅**

```kotlin
// ✅ 올바른 단위 테스트: Redis Mock 사용
class AnalysisCacheServiceTest : BehaviorSpec() {
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var objectMapper: ObjectMapper
    
    init {
        beforeEach {
            redisTemplate = mockk()
            objectMapper = ObjectMapper().apply {
                registerModule(JavaTimeModule())  // 중요!
            }
            // Mock 설정...
        }
        
        given("개인 분석 데이터 캐싱") {
            then("올바른 키와 TTL로 저장되어야 한다") {
                // 비즈니스 로직만 검증
                // - 캐시 키 패턴: analysis:personal:{userId}
                // - TTL: 6시간
                // - JSON 직렬화 정확성
            }
        }
    }
}
```

**테스트하는 것**:
- ✅ 캐시 키 패턴이 올바른가?
- ✅ TTL이 정확히 설정되는가?
- ✅ 데이터가 올바르게 직렬화/역직렬화되는가?

**테스트하지 않는 것**:
- ❌ Redis 서버가 실제로 동작하는가? (인프라 관심사)
- ❌ 네트워크 연결이 안정적인가? (환경 문제)

#### **6.2.2 AnalysisUpdateSaga - 단위 테스트 ✅**

```kotlin
// ✅ 올바른 단위 테스트: 모든 외부 의존성 Mock
@SpringBootTest(classes = [..., TestConfiguration::class])
class AnalysisUpdateSagaTest : BehaviorSpec() {
    // TestConfiguration에서 모든 Mock 제공
    // - OutboxService: Mock
    // - AnalysisCacheService: Mock  
    // - UserRepository: Mock
    
    given("분석 업데이트 SAGA 실행") {
        then("5단계가 순서대로 실행되어야 한다") {
            // SAGA 비즈니스 로직만 검증
            // - 단계별 실행 순서
            // - 보상 트랜잭션
            // - 이벤트 발행
        }
    }
}
```

**테스트하는 것**:
- ✅ SAGA 단계가 올바른 순서로 실행되는가?
- ✅ 실패 시 보상 트랜잭션이 동작하는가?
- ✅ 올바른 이벤트가 발행되는가?

**테스트하지 않는 것**:
- ❌ Kafka가 실제로 메시지를 전송하는가?
- ❌ Redis가 실제로 캐시를 저장하는가?

### 6.3 테스트 작성 가이드라인

#### **6.3.1 단위 테스트 작성 규칙**

1. **Mock 우선 사용**
   - 모든 외부 의존성은 Mock으로 대체
   - MockK 라이브러리 사용
   - TestConfiguration에서 공통 Mock 설정

2. **비즈니스 로직 집중**
   - 인프라 연결이 아닌 비즈니스 규칙 검증
   - 도메인 객체의 상태 변화 검증
   - 계산 로직의 정확성 검증

3. **빠른 실행 보장**
   - 전체 테스트 30초 이내 실행
   - 개별 테스트 1초 이내 실행
   - 네트워크 I/O 완전 제거

#### **6.3.2 통합 테스트 작성 규칙 (필요시에만)**

1. **TestContainers 사용**
   ```kotlin
   @SpringBootTest
   @Testcontainers
   class CriticalPathIntegrationTest : BehaviorSpec() {
       @Container
       val redis = GenericContainer<Nothing>("redis:7-alpine")
       
       @Container 
       val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"))
   }
   ```

2. **제한적 사용**
   - 매우 중요한 플로우만 (예: 하루 1번 실행되는 분석 SAGA)
   - 로컬 개발 시에는 실행하지 않음
   - CI/CD에서만 실행

### 6.4 테스트 실행 전략

#### **6.4.1 개발 중 (로컬)**
```bash
# 단위 테스트만 실행 (빠른 피드백)
./gradlew test
```

#### **6.4.2 CI/CD 파이프라인**
```bash
# 1. 단위 테스트 실행
./gradlew test

# 2. 통합 테스트 실행 (선택적)
./gradlew integrationTest

# 3. 커버리지 검증
./gradlew jacocoTestCoverageVerification
```

### 6.5 현재 알고리포트 테스트 현황 ✅

- **총 테스트**: 156개 (모두 단위 테스트)
- **성공률**: 100% (failures=0, errors=0)
- **실행 시간**: < 30초
- **코드 커버리지**: Branch 75%, Line 80% 달성
- **TDD 적용률**: 100% (모든 기능이 Red-Green-Refactor 사이클 적용)

**결론**: 현재 단위 테스트 전략이 알고리포트 프로젝트에 완벽하게 적합하며, 통합 테스트는 선택적으로만 추가하는 것이 바람직합니다.