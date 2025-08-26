# 🔧 종합 리팩토링 계획서 (2024-08-26)

## 📋 **개요**

기존 중간 보고서에서 확인된 문제점들에 대해 전체 코드베이스를 종합적으로 재검토한 결과, **9개 모듈에서 총 15개의 인메모리 구현체**가 발견되었습니다. 이는 프로덕션 환경에서 심각한 데이터 유실 위험을 초래하는 치명적 문제입니다.

### 🎯 **리팩토링 목표**
1. **데이터 영속성 확보**: 모든 인메모리 구현체를 JPA Entity/Repository로 전환
2. **시스템 안정성 향상**: SAGA 복구, 분산 환경 대응 가능
3. **코드 품질 개선**: 블로킹 호출 제거, 하드코딩 값 외부화
4. **테스트 신뢰성 향상**: 실제 DB 연동 통합 테스트, MockK 활용 단위 테스트

---

## 🚨 **발견된 문제점 현황**

### **1. 치명적 영속성 부재 (CRITICAL)**

#### **Collector 모듈 (데이터 동기화 핵심)**
- ❌ `DataSyncCheckpointRepositoryImpl`:20-21 → `ConcurrentHashMap` 사용
- ❌ `SubmissionSyncServiceImpl`:21-22 → `ConcurrentHashMap` 사용  
- ❌ `SubmissionRepositoryImpl`:20 → `ConcurrentHashMap` 사용
- ❌ `SagaPerformanceOptimizer`:22 → `ConcurrentHashMap` 사용

#### **StudyGroup 모듈**
- ❌ `StudyGroupService`:16-18 → `ConcurrentHashMap` 3개 사용

#### **Analysis 모듈** 
- ❌ `ElasticsearchService`:27-28 → `ConcurrentHashMap` 사용
- ❌ `AnalysisService`:14-15 → `ConcurrentHashMap` 사용

#### **Notification 모듈**
- ❌ `EmailNotificationService`:14 → `ConcurrentHashMap` 사용

### **2. 코루틴 블로킹 호출 (HIGH)**
- ❌ `RateLimitHandlerImpl`:120 → `Thread.sleep()` 사용

### **3. 테스트 패턴 문제 (MEDIUM)**
- ❌ **10개+ 테스트파일**에서 `beforeEach { service.clear() }` 패턴
- ❌ MockK 활용 부족 (중간 실패 시나리오 테스트 미완성)

---

## 🚀 **5단계 리팩토링 계획**

### **Phase 1: Collector 모듈 영속화** 🔥 **CRITICAL**
> **우선순위**: 1순위 | **예상 소요**: 2-3일 | **영향도**: 프로덕션 정상화

#### **1.1 DataSyncCheckpoint 영속화**
```kotlin
// AS-IS: 인메모리
private val checkpoints = ConcurrentHashMap<UUID, DataSyncCheckpoint>()

// TO-BE: JPA Entity
@Entity
@Table(name = "data_sync_checkpoints")
data class DataSyncCheckpoint(
    @Id val syncJobId: UUID,
    val userId: UUID,
    val checkpointAt: LocalDateTime,
    val lastProcessedSubmissionId: Long?,
    val status: SyncStatus,
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

@Repository
interface DataSyncCheckpointRepository : JpaRepository<DataSyncCheckpoint, UUID> {
    fun findLatestByUserId(userId: UUID): DataSyncCheckpoint?
}
```

#### **1.2 Submission 영속화**
```kotlin
// AS-IS: 인메모리
private val submissions = ConcurrentHashMap<Long, Submission>()

// TO-BE: JPA Entity
@Entity
@Table(name = "submissions")
data class Submission(
    @Id val submissionId: Long,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
    val problemId: String,
    val result: String,
    val language: String,
    val submittedAt: LocalDateTime,
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
```

#### **1.3 SubmissionSyncService 리팩토링**
```kotlin
// AS-IS: 인메모리
private val userHandles = ConcurrentHashMap<UUID, String>()
private val lastSyncTimes = ConcurrentHashMap<UUID, LocalDateTime>()

// TO-BE: JPA Repository 활용
@Service
class SubmissionSyncService(
    private val userRepository: UserRepository,
    private val dataSyncCheckpointRepository: DataSyncCheckpointRepository
) {
    fun getActiveUserIds(): List<UUID> = 
        userRepository.findAllBySolvedacHandleIsNotNull().map { it.id }
        
    fun getUserHandle(userId: UUID): String = 
        userRepository.findById(userId).orElseThrow().solvedacHandle!!
        
    fun getLastSyncTime(userId: UUID): LocalDateTime = 
        dataSyncCheckpointRepository.findLatestByUserId(userId)?.checkpointAt 
            ?: LocalDateTime.now().minusHours(1)
}
```

### **Phase 2: StudyGroup 모듈 영속화** 🚨 **HIGH**
> **우선순위**: 2순위 | **예상 소요**: 1-2일 | **영향도**: 확장성 확보

#### **2.1 StudyGroup Entity 생성**
```kotlin
@Entity
@Table(name = "study_groups")
data class StudyGroup(
    @Id val id: UUID,
    @Column(unique = true, nullable = false)
    val name: String,
    val description: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    val owner: User,
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(name = "study_group_members")
data class StudyGroupMember(
    @Id val id: UUID,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    val group: StudyGroup,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
    @Column(updatable = false)
    val joinedAt: LocalDateTime = LocalDateTime.now()
)
```

### **Phase 3: Analysis/Notification 모듈 영속화** ⚠️ **MEDIUM**
> **우선순위**: 3순위 | **예상 소요**: 1-2일 | **영향도**: 완전성 확보

#### **3.1 실제 Elasticsearch 연동**
```kotlin
// AS-IS: 인메모리 시뮬레이션
private val personalStatsIndex = ConcurrentHashMap<String, MutableMap<String, Any>>()

// TO-BE: 실제 Elasticsearch Client
@Service
class ElasticsearchService(
    private val elasticsearchClient: ElasticsearchClient
) {
    fun indexPersonalAnalysis(analysis: PersonalAnalysis) {
        val request = IndexRequest("personal-stats-${getCurrentMonth()}")
            .id(analysis.userId)
            .source(analysis.toMap())
        elasticsearchClient.index(request)
    }
}
```

#### **3.2 EmailNotification 영속화**
```kotlin
@Entity
@Table(name = "email_notifications")
data class EmailNotification(
    @Id val id: UUID,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
    @Enumerated(EnumType.STRING)
    val emailType: EmailType,
    val sentAt: LocalDateTime,
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class EmailType { WELCOME, DAILY_SUMMARY, NOTIFICATION }
```

### **Phase 4: 기술 부채 해결** 📝 **LOW**
> **우선순위**: 4순위 | **예상 소요**: 0.5-1일 | **영향도**: 성능 최적화

#### **4.1 코루틴 블로킹 호출 수정**
```kotlin
// AS-IS: 블로킹 호출
Thread.sleep(delayMs)

// TO-BE: 논블로킹 호출
import kotlinx.coroutines.delay
delay(delayMs)
```

#### **4.2 하드코딩 값 외부화**
```yaml
# application.yml
algoreport:
  retry:
    max-attempts: 3
    base-delay-ms: 1000
    max-delay-ms: 60000
  batch:
    size: 100
    concurrency: 10
  collector:
    rate-limit-delay-ms: 60000
```

### **Phase 5: 테스트 코드 리팩토링** 📝 **LOW**
> **우선순위**: 5순위 | **예상 소요**: 1-2일 | **영향도**: 안정성 확보

#### **5.1 통합 테스트 → @SpringBootTest + @Transactional**
```kotlin
// AS-IS: beforeEach로 인메모리 초기화
beforeEach { service.clear() }

// TO-BE: 실제 DB 연동 통합 테스트
@SpringBootTest
@ActiveProfiles("test") 
@Transactional // 자동 롤백으로 테스트 격리
class ServiceIntegrationTest(
    private val repository: Repository // DB 상태 직접 검증
)
```

#### **5.2 단위 테스트 → MockK 활용**
```kotlin
// AS-IS: 주석 처리된 중간 실패 테스트
// given("중간 단계가 실패할 때") { /* 주석 */ }

// TO-BE: MockK로 의존성 Mocking
@MockkBean
private lateinit var analysisProfileService: AnalysisProfileService

given("분석 프로필 생성이 실패할 때") {
    every { analysisProfileService.createProfile(any()) } throws Exception()
    
    val result = userRegistrationSaga.start(request)
    
    then("전체 트랜잭션이 롤백되어야 한다") {
        result.sagaStatus shouldBe SagaStatus.FAILED
        userRepository.findByEmail(request.email) shouldBe null
    }
}
```

---

## 📊 **작업 우선순위 및 일정**

| Phase | 모듈 | 우선순위 | 예상 시간 | 상태 | 담당자 | 진행률 |
|-------|------|----------|----------|------|--------|---------|
| **1** | **Collector** | 🔥 CRITICAL | **2-3일** | ✅ **90% 완료** | 개발자 | **9/10 작업** |
| **2** | **StudyGroup** | 🚨 HIGH | **1-2일** | ⏸️ 대기 | 개발자 | 0% |
| **3** | **Analysis/Notification** | ⚠️ MEDIUM | **1-2일** | ⏸️ 대기 | 개발자 | 0% |
| **4** | **기술 부채** | 📝 LOW | **0.5-1일** | ⏸️ 대기 | 개발자 | 0% |
| **5** | **테스트 리팩토링** | 📝 LOW | **1-2일** | ⏸️ 대기 | 개발자 | 0% |

### **🎯 즉시 시작 권장**: Phase 1 (Collector 모듈)
- **DataSyncCheckpointRepository** 영속화만으로도 SAGA 복구 기능 복원
- 프로덕션 환경 데이터 유실 위험 즉시 해결

---

## ✅ **이미 완료된 작업** (2024-08-26 이전)

### **✅ User 모듈** 
- **완료**: `User` Entity, `UserRepository`, `UserService` JPA 전환
- **완료**: `UserRegistrationSagaTest` 통합 테스트 리팩토링

### **✅ Analysis 모듈** 
- **완료**: `AnalysisProfile` Entity, `AnalysisProfileRepository`, `AnalysisProfileService` JPA 전환

### **✅ Notification 모듈** 
- **완료**: `NotificationSettings` Entity, `NotificationSettingsRepository`, `NotificationSettingsService` JPA 전환

---

## 📝 **리팩토링 진행 현황 추적**

### **Phase 1: Collector 모듈** (진행률: 90%) ✅ **거의 완료**
- [x] `DataSyncCheckpoint` Entity 생성
- [x] `DataSyncCheckpointRepository` JPA 전환
- [x] `DataSyncCheckpointRepositoryImpl` 삭제
- [x] `Submission` Entity 생성
- [x] `SubmissionRepository` JPA 전환  
- [x] `SubmissionRepositoryImpl` 삭제
- [x] `SubmissionSyncService` 리팩토링
- [x] `SubmissionSyncServiceImpl` 인메모리 로직 제거
- [x] `UserRepository`에 `findAllBySolvedacHandleIsNotNull()` 메서드 추가
- [x] 관련 테스트 코드 MockK로 수정
- [ ] `SagaPerformanceOptimizer` 영속화 (Redis 또는 DB) - **잔여 작업**

### **Phase 2: StudyGroup 모듈** (진행률: 0%)
- [ ] `StudyGroup` Entity 생성
- [ ] `StudyGroupMember` Entity 생성
- [ ] `StudyGroupRepository` JPA 인터페이스 생성
- [ ] `StudyGroupMemberRepository` JPA 인터페이스 생성
- [ ] `StudyGroupService` 인메모리 로직 제거 및 JPA 전환
- [ ] 관련 테스트 코드 수정

### **Phase 3: Analysis/Notification** (진행률: 0%)
- [ ] 실제 Elasticsearch Client 설정
- [ ] `ElasticsearchService` 인메모리 로직 제거
- [ ] `AnalysisService` 인메모리 로직 제거
- [ ] `EmailNotification` Entity 생성
- [ ] `EmailNotificationService` 영속화
- [ ] 관련 테스트 코드 수정

### **Phase 4: 기술 부채** (진행률: 0%)
- [ ] `RateLimitHandlerImpl` `Thread.sleep()` → `delay()` 변경
- [ ] 하드코딩 값들 `application.yml`로 외부화
- [ ] `@ConfigurationProperties` 클래스 생성

### **Phase 5: 테스트 리팩토링** (진행률: 0%)
- [ ] 인메모리 테스트들을 `@SpringBootTest` + `@Transactional`로 전환
- [ ] MockK를 활용한 중간 실패 시나리오 테스트 완성
- [ ] `beforeEach { service.clear() }` 패턴 제거

---

## 📋 **체크리스트 및 검증 기준**

### **각 Phase 완료 기준**
- [ ] 모든 `ConcurrentHashMap` 사용 제거 확인
- [ ] JPA Entity/Repository 정상 동작 확인
- [ ] 기존 테스트 모두 통과
- [ ] 신규 통합 테스트 추가
- [ ] 애플리케이션 재시작 후 데이터 유지 확인

### **전체 리팩토링 완료 기준**
- [ ] `grep -r "ConcurrentHashMap" src/` 결과 0건
- [ ] `grep -r "Thread.sleep" src/` 결과 0건 (테스트 제외)
- [ ] `grep -r "clear()" src/test/` 결과 0건 (정당한 용도 제외)
- [ ] 전체 테스트 스위트 100% 통과
- [ ] JaCoCo 커버리지 75% Branch, 80% Line 유지

---

---

## 🎉 **Phase 1 작업 완료 보고 (2024-08-26)**

### **✅ 완료된 작업 (총 9개)**
1. **DataSyncCheckpoint JPA Entity 생성** - `BatchModels.kt`
2. **DataSyncCheckpointRepository JPA 전환** - `DataSyncBatchService.kt` 
3. **DataSyncCheckpointRepositoryImpl 삭제** - 인메모리 구현체 제거
4. **Submission JPA Entity 생성** - `SubmissionModels.kt` 신규 생성
5. **SubmissionRepository JPA 전환** - `SubmissionRepository.kt`
6. **SubmissionRepositoryImpl 삭제** - 인메모리 구현체 제거
7. **SubmissionSyncService 리팩토링** - `SubmissionSyncServiceImpl.kt`
8. **UserRepository 메서드 추가** - `findAllBySolvedacHandleIsNotNull()` 
9. **테스트 코드 MockK 전환** - `SubmissionSyncServiceTest.kt`

### **🎯 달성된 목표**
- ✅ **SAGA 복구 기능 복원**: DataSyncCheckpoint 영속화로 애플리케이션 재시작 후 복구 가능
- ✅ **데이터 유실 위험 해결**: 핵심 동기화 데이터가 DB에 안전하게 저장
- ✅ **분산 환경 대응**: 여러 인스턴스에서 동일한 DB 상태 공유 가능
- ✅ **테스트 품질 향상**: MockK를 활용한 단위 테스트로 전환

### **⏳ 잔여 작업 (1개)**
- [ ] `SagaPerformanceOptimizer` 영속화 (Redis 또는 DB 선택 필요)

---

## 🚨 **Phase 1 후속 작업: UUID/String 타입 통일 (2024-08-26 오후)**

### **🔍 발견된 추가 문제**
Phase 1 JPA 전환 후 **컴파일 단계에서 대량의 UUID/String 타입 불일치** 발생:
- 메인 코드: User, StudyGroup 등의 ID가 UUID로 변경됨
- 테스트 코드: 여전히 String 기반으로 Mock 데이터 생성
- 일부 서비스: 혼재된 타입 정의 (AnalysisService 등)

### **✅ 해결 완료된 타입 불일치**
1. **SolvedacLinkSaga + Test**: 모든 userId String → UUID 변경
2. **CreateGroupSaga + Test**: ownerId String → UUID 변경  
3. **JoinGroupSaga + Test**: userId String → UUID 변경
4. **StudyGroupService**: 모든 멤버 관리 메서드 UUID 기반으로 통일
5. **CustomOAuth2UserServiceTest**: UserRegistrationResult.userId 타입 맞춤
6. **Error enum**: 누락된 오류 코드 추가 (DUPLICATE_EMAIL, INVALID_OAUTH_CODE 등)

### **⚠️ 임시방편으로 처리된 부분**
- `AnalysisUpdateSagaTest.kt`: `user.id.toString()` 변환으로 임시 해결
- 이유: AnalysisService가 여전히 String 기반 인메모리 구현

### **🎯 올바른 방향성 합의**
**사용자 지적사항**: 
> "임시방편으로 `.toString()` 붙이는 것보다는 AnalysisService 자체를 UUID 기반으로 변경하는게 올바른 접근"

**합의된 방향성**:
1. ✅ **근본적 해결**: 서비스 계층을 UUID 기반으로 통일
2. ❌ **임시방편 금지**: `.toString()` 변환은 기술부채 증가
3. 🎯 **일관성 원칙**: 전체 시스템의 ID 타입 통일성 우선

### **🚀 다음 작업 계획**
1. **AnalysisService** UUID 기반으로 완전 변경
2. **PersonalStatsRefreshSagaTest** 등 남은 테스트 파일들 UUID 통일  
3. **Analysis 모듈 전체** 타입 일관성 확보

### **📊 현재 상태**
- ✅ 핵심 SAGA 클래스들: UUID 기반 완료
- ✅ StudyGroup 관련: 완전히 통일됨  
- ⏳ Analysis 모듈: 부분적 임시처리 상태
- ⏳ 테스트 컴파일: 약 80% 해결

---

📅 **최종 업데이트**: 2024-08-26 UUID 타입 통일 진행중  
👤 **작성자**: 개발팀  
🔄 **다음 업데이트 예정**: AnalysisService UUID 기반 변경 완료 시