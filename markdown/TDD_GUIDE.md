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

### **✅ 올바른 RED 단계 방법**

#### **방법 3: 가짜 구현체 → Assertion 실패**
```kotlin
class SolvedacLinkSaga {
    fun start(request: SolvedacLinkRequest): SolvedacLinkResult {
        // 의도적으로 잘못된 값 반환 (테스트 실패하도록)
        return SolvedacLinkResult(
            sagaStatus = SagaStatus.FAILED,  // 테스트는 COMPLETED 기대
            linkedHandle = null,             // 테스트는 실제 handle 기대
            errorMessage = "Not implemented"
        )
    }
}
```

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