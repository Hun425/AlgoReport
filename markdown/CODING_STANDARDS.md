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
        

### 2.3 테스트 구조 (Given-When-Then)

```
@Test
@DisplayName("상세한 테스트 시나리오 설명")
fun testMethodName() {
    // given - 테스트 준비 (객체 생성, Mocking 설정)

    // when - 테스트 대상 함수 실행

    // then - 결과 검증 (Assertion), 상호작용 검증 (Verification)
}
```

## 3. 비즈니스 로직 구현 규칙

### 3.1 불변성(Immutability) 지향

- `val`을 우선적으로 사용하고, 변경이 꼭 필요한 경우에만 `var` 사용
    
- 엔티티의 상태 변경은 명확한 의도를 가진 메서드를 통해 수행
    

### 3.2 Null 안전성

- Kotlin의 Nullable 타입(`?`)을 적극 활용하여 NPE 방지
    
- `?.`(Safe Call)와 `?:`(Elvis Operator)를 적절히 사용
    

## 4. 커밋 메시지 규칙

### 4.1 TDD 사이클별 커밋 메시지

```
🔴 Red:     test: Red - [설명]
🟢 Green:   feat: Green - [설명]
🔵 Refactor: refactor: Refactor - [설명]
```

### 4.2 일반 커밋 메시지

```
feat: 새로운 기능 추가
fix: 버그 수정
docs: 문서 수정
style: 코드 포맷팅
refactor: 코드 리팩토링
test: 테스트 코드
chore: 빌드 설정 등
```