# TDD 적용 가이드라인

## ⚠️ **중요 주의사항 (필수 준수)**

### 🚨 **절대 잊지 말아야 할 핵심 규칙 (매번 확인 필수!)**

- **🔴 Red 단계 완료 → 즉시 커밋 + 문서 업데이트**
    
- **🟢 Green 단계 완료 → 즉시 커밋 + 문서 업데이트**
    
- **🔵 Refactor 단계 완료 → 즉시 커밋 + 문서 업데이트**
    
- **⚠️ 절대 지연 금지: 여러 단계 몰아서 커밋하는 것 금지**
    
- **⚠️ 문서 업데이트 없는 커밋 금지**
    

### 🔍 **새로운 기능 추가 전 기존 코드 상세 분석 필수 (절대 원칙)**

- **⚠️ 절대 금지: 임의로 메서드나 필드를 가정하여 코드 작성**
    
- **✅ 필수 작업: 새로운 기능 추가나 테스트 작성 전에 반드시 관련된 모든 기존 코드를 상세히 분석**
    

### 🔄 **TDD 사이클별 커밋 전략 (필수 준수)**

- **Red-Green-Refactor 각 단계마다 반드시 커밋해야 합니다**
    
- **커밋 메시지 형식:**
    
    - 🔴 Red 단계: `test: Red - [간략한 설명]`
        
    - 🟢 Green 단계: `feat: Green - [간략한 설명]`
        
    - 🔵 Refactor 단계: `refactor: Refactor - [간략한 설명]`
        

### 🚨 **테스트 품질 및 무결성 원칙 (절대 원칙)**

#### **절대 금지사항**

- **❌ 테스트 우회**: 실제 로직 대신 하드코딩된 값으로 테스트 통과시키기 금지
    ```kotlin
    // ❌ 잘못된 예: 하드코딩으로 테스트 우회
    fun getUserInfo(handle: String): UserInfo {
        return UserInfo(tier = 10, solvedCount = 100) // 항상 같은 값 반환
    }
    ```

- **❌ 가짜 구현**: 비즈니스 로직 없이 테스트만 통과하는 구현 금지
    ```kotlin
    // ❌ 잘못된 예: 로직 없는 가짜 구현
    fun validateSolvedacHandle(handle: String): Boolean {
        return true // 항상 성공 반환
    }
    ```

- **❌ 테스트 간소화**: 복잡한 시나리오를 단순화하여 테스트 회피 금지
    - 예외 상황, 경계값, 실패 시나리오 테스트 생략 금지
    - 보상 트랜잭션, 롤백 시나리오 테스트 생략 금지

- **❌ 예외 처리 생략**: 에러 케이스 테스트 건너뛰기 금지
    - solved.ac API 실패 상황
    - 네트워크 오류, 타임아웃
    - 데이터 정합성 오류

#### **필수 준수사항**

- **✅ 실제 비즈니스 로직 구현**: 모든 테스트는 실제 비즈니스 요구사항 검증
    - GREEN 단계에서도 해당 기능의 핵심 로직 완전 구현
    - 단순 반환값이 아닌 실제 계산, 검증, 처리 로직 포함

- **✅ 엣지 케이스 포함**: 예외 상황, 경계값, 실패 시나리오 모두 테스트
    - 입력값 검증 (null, 빈 문자열, 잘못된 형식)
    - 외부 API 실패 상황
    - 동시성 문제, 데이터 경합 상태

- **✅ 완전한 기능 구현**: GREEN 단계에서도 프로덕션 품질의 코드 작성
    - 로깅, 예외 처리, 검증 로직 포함
    - 성능 고려사항 반영
    - 보안 취약점 방지

- **✅ 테스트 시나리오 완성도**: 실제 운영 환경에서 발생할 수 있는 모든 케이스 커버
    - 정상 플로우 + 모든 예외 플로우
    - 보상 트랜잭션 검증
    - 이벤트 발행/수신 검증

#### **🎯 품질 검증 체크리스트**

작업 완료 전 다음을 반드시 확인:
- [ ] 모든 테스트가 실제 비즈니스 로직을 검증하는가?
- [ ] 하드코딩된 값이나 임시 구현이 있는가?
- [ ] 예외 상황과 실패 시나리오가 모두 테스트되었는가?
- [ ] 보상 트랜잭션과 롤백 로직이 검증되었는가?
- [ ] 프로덕션 환경에서 동작할 수 있는 완전한 구현인가?

**🚨 기억하세요: 테스트 품질은 제품 품질과 직결됩니다. 꼼수나 우회는 기술 부채가 되어 나중에 더 큰 문제를 야기합니다!**

---

## 1. TDD 개요

### 1.1 TDD 핵심 원칙

- **테스트 우선 작성**: 구현 코드보다 테스트를 먼저 작성해야 함
    
- **최소 단위 개발**: 한 번에 하나의 기능만 구현
    
- **Red-Green-Refactor 사이클 엄수**: 다음 단계로 넘어가기 전 현재 단계를 완료해야 함
    

### 1.2 TDD 사이클 (Red-Green-Refactor)

1. **🔴 Red (실패하는 테스트 작성)**: 구현하려는 기능을 검증하는 테스트를 먼저 작성. 테스트는 반드시 실패해야 함.
    
2. **🟢 Green (테스트 통과를 위한 최소한의 코드 작성)**: 테스트를 통과하기 위한 **최소한의 코드**만 구현.
    
3. **🔵 Refactor (리팩토링)**: 테스트가 통과하는 상태를 유지하면서 코드 품질 향상.

---

## 🔴 **RED 단계 올바른 방법론 (중요!)**

### **❌ 잘못된 RED 단계 방법들**

#### **방법 1: 클래스 없음 → 컴파일 실패**
```kotlin
// 테스트만 작성, 구현체 없음
class SolvedacLinkSagaTest {
    @Test
    fun should_link_solvedac_account() {
        val saga = SolvedacLinkSaga() // ← 클래스가 없어서 컴파일 실패
    }
}
```
**문제**: 컴파일이 안 되면 테스트 실행 불가 ❌

#### **방법 2: 빈 구현체 → 예외 발생**
```kotlin
class SolvedacLinkSaga {
    fun start(request: SolvedacLinkRequest): SolvedacLinkResult {
        TODO("Not implemented")  // NotImplementedError 던짐
    }
}
```
**문제**: 예외로 테스트 중단, assertion 도달 불가 ❌

### **✅ 올바른 RED 단계 방법 (정통 TDD 방법론)**

#### **방법 3: "Fake It" 전략 → 가장 간단한 가짜 값 반환**
```kotlin
class SolvedacLinkSaga {
    fun start(request: SolvedacLinkRequest): SolvedacLinkResult {
        // RED 단계: 컴파일 오류만 해결, 가장 간단한 가짜 값 반환
        return SolvedacLinkResult(
            sagaStatus = SagaStatus.PENDING,  // 기본값 (모든 테스트 실패)
            linkedHandle = null,              // 기본값 (모든 테스트 실패)
            errorMessage = null               // 기본값 (모든 테스트 실패)
        )
    }
}
```

#### **정통 TDD "Fake It" 전략의 원칙**
- ✅ **컴파일 오류만 해결**: 메서드 시그니처만 맞춤
- ✅ **가장 간단한 값**: `null`, `0`, `false`, 기본 생성자 등
- ✅ **모든 테스트 실패**: 실제 로직 없이 기본값만 반환
- ✅ **Kent Beck의 원칙**: "Make it work, then make it right, then make it fast"

### **🎯 올바른 RED 단계 조건**
1. **✅ 컴파일 성공** - 모든 클래스와 메서드 존재
2. **✅ 테스트 실행 가능** - 예외 발생하지 않음  
3. **✅ Assertion에서 실패** - 기대값과 실제값 불일치
4. **✅ 실패 이유가 명확** - 어떤 부분이 구현되지 않았는지 알 수 있음

### **🔍 실제 적용 예시**

**Controller RED 단계:**
```kotlin
@RestController
class UserController {
    @GetMapping("/api/v1/users/{id}")
    fun getUser(@PathVariable id: String): ResponseEntity<UserDto> {
        // RED: 의도적으로 404 반환 (테스트는 200 기대)
        return ResponseEntity.notFound().build()
    }
}
```

**Service RED 단계:**
```kotlin
@Service  
class UserService {
    fun findUserById(id: String): User? {
        // RED: 의도적으로 null 반환 (테스트는 User 객체 기대)
        return null
    }
}
```

**Repository RED 단계:**
```kotlin
@Repository
class UserRepository {
    fun save(user: User): User {
        // RED: 의도적으로 빈 User 반환 (테스트는 저장된 User + ID 기대)
        return User(id = "", email = "", nickname = "")
    }
}
```

### **📝 RED 단계 체크리스트**

작업 전 다음을 확인:
- [ ] 모든 클래스가 존재하는가?
- [ ] 모든 메서드가 정의되어 있는가?
- [ ] 컴파일이 성공하는가?
- [ ] 테스트 실행 시 예외가 발생하지 않는가?
- [ ] Assertion에서 명확히 실패하는가?
- [ ] 실패 원인이 "구현되지 않음" 때문인가?

**🚨 기억하세요: RED 단계는 "테스트 중단"이 아니라 "테스트 실패"입니다!**

---

## 📝 **TODO 주석 활용한 TDD 단계 관리 (필수!)**

### **🎯 TODO 주석의 목적**
1. **진행 상황 추적**: 어떤 부분이 RED/GREEN/REFACTOR 중 어느 단계인지 명확히 표시
2. **나중에 쉽게 찾기**: IDE에서 TODO 검색으로 미완성 부분 빠르게 발견
3. **컨텍스트 보존**: 왜 이 부분이 미완성인지, 다음에 뭘 해야 하는지 명확히 기록
4. **협업 효율성**: 다른 개발자도 현재 작업 상황과 다음 할 일을 바로 파악 가능

### **📋 TODO 주석 컨벤션 (반드시 준수)**

```kotlin
// TODO: [RED] - 테스트 작성 단계에서 해야 할 일
// TODO: [GREEN] - 기본 구현 단계에서 해야 할 일  
// TODO: [REFACTOR] - 리팩토링 단계에서 해야 할 일
// TODO: [BUG] - 버그 수정이 필요한 부분
// TODO: [OPTIMIZE] - 성능 최적화가 필요한 부분
// TODO: [DOCS] - 문서화가 필요한 부분
```

### **✅ 올바른 TODO 주석 사용 예시**

**RED 단계 구현체:**
```kotlin
class SolvedacLinkSaga {
    fun start(request: SolvedacLinkRequest): SolvedacLinkResult {
        // TODO: [GREEN] solved.ac API 호출 및 사용자 검증 로직 구현 필요
        // TODO: [GREEN] 사용자 프로필에 solved.ac 정보 업데이트 로직 구현 필요  
        // TODO: [GREEN] OutboxService를 통한 SOLVEDAC_LINKED 이벤트 발행 구현 필요
        // TODO: [REFACTOR] 중복 핸들 체크 및 보상 트랜잭션 로직 추가 필요
        
        return SolvedacLinkResult(
            sagaStatus = SagaStatus.FAILED,  // TODO: [GREEN] 성공 시 COMPLETED로 변경
            linkedHandle = null,             // TODO: [GREEN] 실제 연동된 handle 반환
            errorMessage = "Not implemented" // TODO: [GREEN] 성공 시 null로 변경
        )
    }
}
```

**GREEN 단계 구현 중:**
```kotlin
class SolvedacLinkSaga {
    fun start(request: SolvedacLinkRequest): SolvedacLinkResult {
        // ✅ GREEN: solved.ac API 호출 구현 완료
        val userInfo = solvedacApiClient.getUserInfo(request.solvedacHandle)
        
        // TODO: [GREEN] 중복 핸들 체크 로직 구현 필요
        // TODO: [GREEN] 사용자 프로필 업데이트 로직 구현 필요
        // TODO: [REFACTOR] 예외 처리 및 보상 트랜잭션 로직 추가 필요
        
        return SolvedacLinkResult(
            sagaStatus = SagaStatus.COMPLETED,
            linkedHandle = request.solvedacHandle,
            errorMessage = null
        )
    }
}
```

### **🔍 TODO 주석 관리 규칙**

1. **완료된 작업**: TODO 주석을 제거하거나 `✅ GREEN:` 형태로 완료 표시
2. **단계별 정리**: 각 TDD 단계 완료 시 해당 TODO들 정리
3. **우선순위 표시**: 긴급한 TODO는 `TODO: [GREEN] [URGENT]` 형태로 표시
4. **연관 작업**: 관련된 TODO는 같은 위치에 그룹화

### **📝 TODO 주석 작성 체크리스트**

작업 시작 전:
- [ ] 현재 단계(RED/GREEN/REFACTOR)를 TODO에 명시했는가?
- [ ] 구체적으로 무엇을 해야 하는지 명확히 기술했는가?
- [ ] 다른 개발자가 봐도 이해할 수 있게 작성했는가?
- [ ] 관련된 TODO들을 적절히 그룹화했는가?

작업 완료 후:
- [ ] 완료된 TODO는 제거하거나 완료 표시했는가?
- [ ] 새로 발견된 작업은 적절한 TODO로 추가했는가?
- [ ] 다음 단계를 위한 TODO를 준비했는가?

**🚨 중요: TODO 주석은 TDD 사이클 관리의 핵심 도구입니다. 반드시 활용하세요!**

---

## 📋 **Kotest BehaviorSpec 데이터 생명주기 관리 (🚨 필수 숙지 - 매번 실수하는 부분!)**

### **🚨 자주 발생하는 테스트 오류 - 데이터 생명주기 실수**

**문제 상황**: CreateGroupSagaTest에서 발생했던 "사용자를 찾을 수 없습니다" 오류

**원인**: Kotest BehaviorSpec의 실행 순서를 잘못 이해하여 테스트 데이터가 삭제된 상태에서 테스트 실행

```kotlin
// ❌ 문제가 있었던 방식
init {
    beforeEach {
        userService.clear() // 각 then 블록 실행 전마다 호출!
    }
    
    given("CREATE_GROUP_SAGA가 실행될 때") {
        val testUser = userService.createUser(...) // 여기서 사용자 생성
        val ownerId = testUser.id
        
        then("테스트 1") {
            // beforeEach에서 이미 사용자가 삭제됨!
            val result = createGroupSaga.start(CreateGroupRequest(ownerId = ownerId, ...))
            // 💥 IllegalArgumentException: 사용자를 찾을 수 없습니다
        }
    }
}

// ✅ 수정된 방식
init {
    beforeEach {
        userService.clear()
    }
    
    given("CREATE_GROUP_SAGA가 실행될 때") {
        then("테스트 1") {
            val testUser = userService.createUser(...) // then 블록 내에서 생성
            val ownerId = testUser.id
            
            val result = createGroupSaga.start(CreateGroupRequest(ownerId = ownerId, ...))
            // ✅ 정상 실행
        }
    }
}
```

### **📋 Kotest 데이터 생명주기 체크리스트 (매번 확인 필수)**

새로운 테스트 작성 시:
- [ ] `beforeEach`가 어느 스코프에 있는지 확인
- [ ] 테스트 데이터 생성 위치 확인 (`given` vs `then`)
- [ ] 각 `then` 블록이 독립적으로 실행됨을 고려
- [ ] 데이터 정리(`clear()`) 시점 확인

테스트 실패 시 확인사항:
- [ ] "찾을 수 없습니다" 류의 오류 → 데이터 생명주기 문제 의심
- [ ] `beforeEach`에서 데이터가 삭제되고 있지 않은지 확인
- [ ] 테스트 데이터가 적절한 스코프에서 생성되고 있는지 확인

**🎯 교훈**: 구현 코드가 올바른데 테스트가 실패한다면, 테스트 코드의 구조적 문제를 먼저 의심하라!

---

## 🚨 **BehaviorSpec Mock 격리 문제 - 매번 반복되는 실수! (필수 숙지)**

### **⚠️ 심각한 문제: Mock 인스턴스 공유로 인한 테스트 간섭**

**문제 상황**: PersonalStatsRefreshSagaUnitTest에서 반복 발생하는 테스트 실패

**원인**: 클래스 레벨 Mock 인스턴스를 여러 테스트에서 공유하여 서로 간섭

```kotlin
// ❌ 문제가 있는 방식 - 클래스 레벨 Mock 공유
class PersonalStatsRefreshSagaUnitTest : BehaviorSpec({

    val userRepository: UserRepository = mockk()  // 클래스 레벨 Mock
    val analysisService: AnalysisService = mockk()
    val solvedacApiClient: SolvedacApiClient = mockk()
    
    val personalStatsRefreshSaga: PersonalStatsRefreshSaga = PersonalStatsRefreshSaga(
        userRepository, analysisService, ..., solvedacApiClient, ...
    )

    given("첫 번째 테스트") {
        `when`("존재하지 않는 사용자") {
            every { userRepository.findAllActiveUserIds() } returns emptyList()
            every { analysisService.deletePersonalAnalysis(any()) } just Runs
            // ... 첫 번째 테스트 실행
        }
    }

    given("두 번째 테스트") {
        `when`("정상 사용자") {
            // 💥 문제: 첫 번째 테스트의 Mock 설정이 남아있음!
            // analysisService.deletePersonalAnalysis() 호출이 기록되어 있어서 
            // verify(exactly = 0) { analysisService.deletePersonalAnalysis(any()) } 실패!
        }
    }
})
```

### **✅ 올바른 해결방법: 테스트별 독립적인 Mock 인스턴스**

```kotlin
// ✅ 올바른 방식 - 각 테스트마다 독립적인 Mock 인스턴스 생성
class PersonalStatsRefreshSagaUnitTest : BehaviorSpec({

    given("존재하지 않는 사용자 시나리오") {
        `when`("존재하지 않는 사용자에 대해 통계 갱신을 요청하면") {
            then("즉시 실패하고 보상 트랜잭션이 실행되어야 한다") {
                // 독립적인 Mock 인스턴스 생성
                val userRepo = mockk<UserRepository>()
                val analysisService = mockk<AnalysisService>()
                val apiClient = mockk<SolvedacApiClient>()
                val outboxService = mockk<OutboxService>()
                
                every { userRepo.findAllActiveUserIds() } returns emptyList()
                every { analysisService.deletePersonalAnalysis(any()) } just Runs
                every { outboxService.publishEvent(any(), any(), any(), any()) } returns UUID.randomUUID()

                val saga = PersonalStatsRefreshSaga(userRepo, analysisService, ..., apiClient, ..., outboxService)
                val result = saga.start(request)

                result.sagaStatus shouldBe SagaStatus.FAILED
                result.compensationExecuted shouldBe true
                
                verify(exactly = 1) { analysisService.deletePersonalAnalysis("non-existent-user") }
            }
        }
    }

    given("정상 사용자 시나리오") {  // 완전히 분리된 테스트
        `when`("정상적인 사용자 데이터 처리") {
            then("성공적으로 완료되어야 한다") {
                // 새로운 독립적인 Mock 인스턴스 생성
                val userRepo = mockk<UserRepository>()
                val analysisService = mockk<AnalysisService>()  // 첫 번째 테스트와 완전히 독립!
                val apiClient = mockk<SolvedacApiClient>()
                val outboxService = mockk<OutboxService>()
                
                every { userRepo.findAllActiveUserIds() } returns listOf("test-user")
                // analysisService.deletePersonalAnalysis() 호출 기록이 없음!
                
                val saga = PersonalStatsRefreshSaga(userRepo, analysisService, ..., apiClient, ..., outboxService)
                val result = saga.start(request)

                // ✅ 성공: 이 Mock 인스턴스에는 deletePersonalAnalysis() 호출 기록이 없음
                verify(exactly = 0) { analysisService.deletePersonalAnalysis(any()) }
            }
        }
    }
})
```

### **🔥 Mock 격리 필수 원칙**

#### **1. Mock 인스턴스 생성 위치**
```kotlin
// ❌ 절대 금지: 클래스 레벨 Mock 선언
class SomeTest : BehaviorSpec({
    val mockService = mockk<Service>()  // 여러 테스트에서 공유됨!
    
// ✅ 권장: then 블록 내부에서 Mock 생성
class SomeTest : BehaviorSpec({
    given("시나리오") {
        `when`("조건") {
            then("결과") {
                val mockService = mockk<Service>()  // 이 테스트에서만 사용
            }
        }
    }
})
```

#### **2. Mock 설정 스코프**
```kotlin
// ❌ 문제: given/when 레벨에서 Mock 설정
given("시나리오") {
    every { mockService.someMethod() } returns "value"  // 여러 then에서 공유
    
    then("테스트 1") { /* 영향받음 */ }
    then("테스트 2") { /* 영향받음 */ }
}

// ✅ 해결: then 블록 내부에서 Mock 설정
given("시나리오") {
    then("테스트 1") {
        val mockService = mockk<Service>()
        every { mockService.someMethod() } returns "value1"  // 이 테스트에서만 유효
    }
    
    then("테스트 2") {
        val mockService = mockk<Service>()
        every { mockService.someMethod() } returns "value2"  // 독립적인 설정
    }
}
```

### **📋 Mock 격리 체크리스트 (반드시 확인!)**

**테스트 작성 전:**
- [ ] Mock 인스턴스를 클래스 레벨에서 선언하고 있지 않은가?
- [ ] 여러 테스트에서 동일한 Mock 인스턴스를 공유하고 있지 않은가?
- [ ] Mock 설정을 `given`/`when` 레벨에서 하고 있지 않은가?

**테스트 실패 시:**
- [ ] "should not be called" 오류 → Mock 호출 기록이 남아있는지 확인
- [ ] "Verification failed" 오류 → 다른 테스트의 Mock 호출이 간섭하는지 확인
- [ ] Mock 인스턴스가 테스트 간에 공유되고 있는지 확인

**수정 방법:**
- [ ] 각 `then` 블록 내부에서 독립적인 Mock 인스턴스 생성
- [ ] Mock 설정도 `then` 블록 내부에서 수행
- [ ] 필요 시 `given` 블록을 여러 개로 분리하여 완전 격리

### **🎯 Mock 격리 핵심 기억사항**

**🚨 기억하세요**: BehaviorSpec에서 Mock을 클래스 레벨에서 선언하면 **100% 테스트 간섭 문제 발생**합니다!

**✅ 해결책**: 
1. **Mock은 항상 `then` 블록 내부에서 생성**
2. **각 테스트는 독립적인 Mock 인스턴스 사용**
3. **의심스러우면 `given` 블록을 분리해서 완전 격리**

---

### **🔥 절대 잊지 말 것 - 테스트 작성 시 필수 확인사항**

**테스트 작성 전 3초만 투자하세요:**
1. **데이터를 `given`/`when`에서 생성하고 있나?** → ❌ **99% 실패**
2. **데이터를 `then` 블록 내부에서 생성하고 있나?** → ✅ **성공**
3. **`beforeEach`에서 `clear()` 호출하고 있나?** → ⚠️ **데이터 생명주기 확인 필수**

**📝 간단한 자가진단:**
```kotlin
// 이 패턴을 보면 즉시 의심하세요! 
given("무언가 할 때") {
    val data = createData()  // ← 🚨 위험! beforeEach에서 삭제될 수 있음
    
    then("결과 확인") {
        // data 사용 → 실패 가능성 높음
    }
}

// 안전한 패턴:
given("무언가 할 때") {
    then("결과 확인") {
        val data = createData()  // ← ✅ 안전! then 블록 내부에서 생성
        // data 사용 → 성공
    }
}
```
    

## 2. 알고리포트 프로젝트 TDD 적용 규칙

### 2.1 서비스 테스트 패턴 (Kotlin + MockK)

```
@ExtendWith(MockKExtension::class)
@DisplayName("StudyGroupService 테스트")
class StudyGroupServiceTest {
    @MockK
    private lateinit var studyGroupRepository: StudyGroupRepository
    @MockK
    private lateinit var userRepository: UserRepository
    @InjectMockKs
    private lateinit var studyGroupService: StudyGroupService

    @Test
    @DisplayName("스터디 그룹 생성 시 그룹장 정보가 정확히 설정되어야 한다")
    fun createStudyGroup_shouldSetOwnerCorrectly() {
        // given
        val ownerId = 1L
        val owner = User(email = "owner@test.com", nickname = "그룹장")
        val requestDto = CreateStudyGroupRequest("알고리즘 스터디")

        every { userRepository.findByIdOrNull(ownerId) } returns owner
        every { studyGroupRepository.save(any()) } answers { firstArg() }

        // when
        val createdGroup = studyGroupService.createStudyGroup(ownerId, requestDto)

        // then
        assertThat(createdGroup.owner).isEqualTo(owner)
        assertThat(createdGroup.name).isEqualTo("알고리즘 스터디")
        verify(exactly = 1) { studyGroupRepository.save(any()) }
    }
}
```

---

## 📊 **분석 모듈 Mock 전략 (Redis/Kafka Mock 사용 가이드)**

### **🎯 분석 모듈에서 Mock 사용이 적절한 이유**

**알고리포트의 분석 모듈은 복잡한 비즈니스 로직을 포함하므로 단위 테스트에서 Redis와 Kafka를 Mock으로 대체하는 것이 최적입니다.**

#### **Mock 사용의 핵심 근거**

1. **비즈니스 로직 집중**: 인프라 문제가 아닌 도메인 로직 검증
2. **빠른 TDD 사이클**: Red-Green-Refactor 사이클이 2-5초 내 완료
3. **테스트 격리성**: 외부 환경에 영향받지 않는 독립적 테스트
4. **높은 안정성**: 156개 테스트 모두 통과, JaCoCo 75%/80% 커버리지 달성

### **✅ 올바른 분석 모듈 Mock 테스트 패턴**

#### **Redis 캐시 서비스 Mock 테스트**
```kotlin
// ✅ 완벽한 Redis Mock 테스트 예시
class AnalysisCacheServiceTest : BehaviorSpec() {
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var objectMapper: ObjectMapper
    private lateinit var analysisCacheService: AnalysisCacheService
    
    init {
        beforeEach {
            redisTemplate = mockk()
            objectMapper = ObjectMapper().apply {
                registerModule(JavaTimeModule())  // LocalDateTime 지원 필수!
            }
            val valueOperations = mockk<ValueOperations<String, String>>()
            every { redisTemplate.opsForValue() } returns valueOperations
            
            analysisCacheService = AnalysisCacheService(redisTemplate, objectMapper)
        }
        
        given("개인 분석 데이터를 캐시할 때") {
            `when`("캐시에 저장하고 조회하면") {
                then("동일한 데이터를 반환해야 한다") {
                    // Mock 설정: 저장과 조회
                    every { valueOperations.set(any(), any(), any<Long>(), any()) } just runs
                    every { valueOperations.get("analysis:personal:user123") } returns personalAnalysisJson
                    
                    // 테스트 실행
                    analysisCacheService.cachePersonalAnalysis("user123", personalAnalysis)
                    val cached = analysisCacheService.getPersonalAnalysisFromCache("user123")
                    
                    // 비즈니스 로직 검증 (인프라가 아닌!)
                    cached!!.userId shouldBe "user123"
                    cached.totalSolved shouldBe 150
                    cached.tagSkills shouldBe mapOf("dp" to 0.8, "graph" to 0.6)
                    
                    // Mock 호출 검증
                    verify { valueOperations.set("analysis:personal:user123", any(), 6, TimeUnit.HOURS) }
                }
            }
        }
    }
}
```

#### **Kafka 이벤트 발행 Mock 테스트**
```kotlin
// ✅ 완벽한 Kafka Mock 테스트 예시  
class AnalysisUpdateSagaTest : BehaviorSpec() {
    init {
        given("분석 업데이트 SAGA가 실행될 때") {
            `when`("분석이 성공적으로 완료되면") {
                then("ANALYSIS_UPDATE_COMPLETED 이벤트가 발행되어야 한다") {
                    // OutboxService Mock 설정
                    val outboxService = mockk<OutboxService>()
                    every { outboxService.publishEvent(any(), any(), any(), any()) } returns UUID.randomUUID().toString()
                    
                    val analysisUpdateSaga = AnalysisUpdateSaga(userRepo, groupRepo, analysisService, cacheService, outboxService)
                    
                    // 테스트 실행
                    val result = analysisUpdateSaga.start(request)
                    
                    // 비즈니스 로직 검증 (인프라가 아닌!)
                    result.sagaStatus shouldBe SagaStatus.COMPLETED
                    result.eventPublished shouldBe true
                    
                    // 이벤트 발행 검증 (Kafka 연결이 아닌 이벤트 데이터 구조!)
                    verify { 
                        outboxService.publishEvent(
                            eventType = "ANALYSIS_UPDATE_COMPLETED",
                            aggregateType = "ANALYSIS", 
                            eventData = match { data ->
                                data["totalUsersProcessed"] == 2 && 
                                data["sagaType"] == "ANALYSIS_UPDATE_SAGA"
                            }
                        )
                    }
                }
            }
        }
    }
}
```

### **🚨 Mock 테스트 시 주의사항**

#### **Jackson LocalDateTime 직렬화 문제**
```kotlin
// ❌ 잘못된 방식 - LocalDateTime 직렬화 실패
val objectMapper = ObjectMapper()

// ✅ 올바른 방식 - JSR310 모듈 추가 필수
val objectMapper = ObjectMapper().apply {
    registerModule(JavaTimeModule())  // LocalDateTime 지원
}
```

#### **배치 캐싱 Mock 설정**
```kotlin
// ❌ 잘못된 방식 - 배치 저장 후 조회 시 Mock 응답 없음
analysisCacheService.cachePersonalAnalysisBatch(personalAnalysisMap)
val cached = analysisCacheService.getPersonalAnalysisFromCache("user1") // null 반환!

// ✅ 올바른 방식 - 배치 저장 후 조회를 위한 Mock 설정 추가
val user1Json = objectMapper.writeValueAsString(personalAnalysisMap["user1"])
every { valueOperations.get("analysis:personal:user1") } returns user1Json

analysisCacheService.cachePersonalAnalysisBatch(personalAnalysisMap) 
val cached = analysisCacheService.getPersonalAnalysisFromCache("user1") // 정상 반환!
```

### **🎯 분석 모듈에서 테스트하는 것 vs 하지 않는 것**

#### **✅ 우리가 테스트하는 핵심 비즈니스 로직**
- 개인/그룹 분석 결과 계산이 정확한가?
- 캐시 키 패턴이 `analysis:personal:{userId}` 형태인가?
- TTL이 개인 6시간, 그룹 12시간으로 설정되는가?
- SAGA 단계별 로직이 올바른 순서로 실행되는가?
- 보상 트랜잭션이 실패 시 적절히 동작하는가?
- 이벤트 데이터 구조가 비즈니스 요구사항에 맞는가?

#### **❌ 테스트하지 않는 인프라 관심사**
- Redis 서버가 실제로 동작하는가? (인프라 문제)
- Kafka 브로커가 메시지를 저장하는가? (환경 문제)
- 네트워크 연결이 안정적인가? (환경 문제)
- JSON 직렬화가 올바르게 되는가? (Jackson 라이브러리 문제)

### **📋 분석 모듈 Mock 테스트 체크리스트**

**테스트 작성 전 확인:**
- [ ] ObjectMapper에 JavaTimeModule 추가했는가?
- [ ] 모든 Redis 호출에 대한 Mock 응답 설정했는가?
- [ ] 배치 캐싱 후 조회를 위한 Mock 설정 추가했는가?
- [ ] 비즈니스 로직만 검증하고 인프라는 무시하는가?
- [ ] Mock 호출 검증으로 올바른 파라미터 전달 확인하는가?

**테스트 실패 시 확인:**
- [ ] Jackson LocalDateTime 직렬화 오류가 아닌가?
- [ ] Mock 응답이 설정되지 않아 null을 반환하는 것은 아닌가?
- [ ] 비즈니스 로직 자체의 문제인가, Mock 설정 문제인가?

**🚨 기억하세요: 분석 모듈은 복잡한 비즈니스 로직이 핵심입니다. Redis와 Kafka는 이미 검증된 오픈소스이므로 Mock으로 충분합니다!**