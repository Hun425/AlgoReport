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