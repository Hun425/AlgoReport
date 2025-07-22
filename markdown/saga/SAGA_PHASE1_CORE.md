# Phase 1 핵심 Saga 설계

이 문서는 **알고리포트 Phase 1에서 구현해야 하는 6개 핵심 Saga**의 상세 설계를 다룹니다. 이들은 플랫폼의 기본 기능을 위해 반드시 필요한 분산 트랜잭션들입니다.

---

## 🎯 **Phase 1 Saga 개요**

| 순서 | Saga 이름 | 복잡도 | 트리거 | 관련 모듈 | 구현 우선순위 |
|-----|----------|-------|--------|----------|-------------|
| 1 | `INITIAL_DATA_SYNC_SAGA` | Very High | solved.ac 연동 | User, Analysis, StudyGroup, Notification | 🔥 Critical |
| 2 | `USER_REGISTRATION_SAGA` | Medium | OAuth2 로그인 | User, Analysis, Notification | 🔥 Critical |
| 3 | `SOLVEDAC_LINK_SAGA` | High | 사용자 요청 | User, Analysis, StudyGroup, Notification | 🔥 Critical |
| 4 | `CREATE_GROUP_SAGA` | Medium | 사용자 요청 | StudyGroup, User, Analysis, Notification | 🔥 Critical |
| 5 | `JOIN_GROUP_SAGA` | High | 사용자 요청 | StudyGroup, User, Analysis, Notification | 🔥 Critical |
| 6 | `SUBMISSION_SYNC_SAGA` | Medium | 스케줄러 | Analysis, StudyGroup, Notification | 🟡 Important |
| 7 | `ANALYSIS_UPDATE_SAGA` | Medium | 스케줄러 | Analysis, StudyGroup, Notification | 🟡 Important |

---

## 📋 **상세 Saga 설계**

### **1. INITIAL_DATA_SYNC_SAGA**

**목표**: solved.ac 계정 연동 시 과거 데이터 대량 수집 및 초기 분석 환경 구축

#### **비즈니스 요구사항**
- solved.ac 연동 시 **과거 6개월간** 모든 제출 이력 수집
- **대용량 데이터 처리**를 위한 배치 작업 관리
- **점진적 데이터 수집**으로 API 레이트 리밋 준수
- 수집 진행 상황 실시간 알림
- **실패 시 부분 복구** 가능한 체크포인트 시스템

#### **Saga 흐름도**

```mermaid
sequenceDiagram
    participant Client
    participant U as User Module
    participant API as solved.ac API
    participant A as Analysis Module
    participant SG as StudyGroup Module
    participant N as Notification Module
    participant K as Kafka

    Note over Client,K: 🔄 INITIAL_DATA_SYNC_SAGA (가장 복잡한 데이터 수집 Saga)
    
    Client->>U: POST /users/me/link-solvedac {handle, syncPeriod}
    
    rect rgb(255, 240, 240)
        Note over U: Step 1: 연동 준비 및 수집 계획 수립
        U->>API: solved.ac 사용자 정보 조회
        API-->>U: {handle, tier, solvedCount, ...}
        U->>U: 수집 대상 문제 수 예상 (최근 6개월)
        U->>U: 배치 작업 계획 수립 (100개씩 나누어 수집)
        U->>U: SyncJob 생성 및 체크포인트 초기화
        U->>U: Outbox에 이벤트 저장
        U-->>K: DATA_SYNC_INITIATED 발행
    end
    
    rect rgb(240, 255, 240)
        Note over A: Step 2: 배치 데이터 수집 시작
        K->>A: DATA_SYNC_INITIATED 수신
        A->>A: 수집 작업 스케줄 생성
        loop 배치별 데이터 수집 (100개씩)
            A->>API: 제출 이력 조회 (pageSize=100)
            API-->>A: submissions[] (배치)
            A->>A: Submissions 검증 및 저장
            A->>A: 체크포인트 업데이트
            A->>A: 진행률 계산 및 저장
            
            alt API 레이트 리밋 도달
                A->>A: 1분 대기 후 재시도
            else 일시적 오류
                A->>A: 지수 백오프로 재시도 (최대 3회)
            end
        end
        A->>A: Outbox에 이벤트 저장
        A-->>K: HISTORICAL_DATA_COLLECTED 발행
    end
    
    rect rgb(240, 240, 255)
        Note over A: Step 3: 초기 분석 실행
        K->>A: HISTORICAL_DATA_COLLECTED 수신 (자체 처리)
        A->>A: 수집된 데이터 기반 초기 분석 실행
        A->>A: 태그별 숙련도 계산
        A->>A: 취약점/강점 분석
        A->>A: 문제 해결 패턴 분석
        A->>A: 추천 시스템 초기 데이터 구축
        A->>A: Outbox에 이벤트 저장
        A-->>K: INITIAL_ANALYSIS_COMPLETED 발행
    end
    
    rect rgb(255, 240, 255)
        Note over SG: Step 4: 그룹 프로필 동기화
        K->>SG: INITIAL_ANALYSIS_COMPLETED 수신
        SG->>SG: 사용자가 속한 그룹들 조회
        loop 각 그룹별로
            SG->>SG: 멤버 프로필 업데이트
            SG->>SG: 그룹 통계 재계산
        end
        SG->>SG: Outbox에 이벤트 저장
        SG-->>K: GROUP_PROFILES_SYNCHRONIZED 발행
    end
    
    rect rgb(255, 255, 240)
        Note over N: Step 5: 동기화 완료 알림
        K->>N: GROUP_PROFILES_SYNCHRONIZED 수신
        N->>N: 동기화 완료 알림 생성
        N->>N: 초기 분석 결과 요약 알림
        N->>N: 추천 문제 목록 알림
        N->>N: Outbox에 이벤트 저장
        N-->>K: SYNC_COMPLETION_NOTIFICATIONS_SENT 발행
    end
    
    U-->>Client: 동기화 시작 응답 (비동기 처리 중)
```

#### **이벤트 명세**

##### `DATA_SYNC_INITIATED`
```json
{
  "eventType": "DATA_SYNC_INITIATED",
  "aggregateId": "sync-job-{uuid}",
  "sagaId": "{saga-uuid}",
  "data": {
    "userId": "{uuid}",
    "solvedacHandle": "algosolver",
    "syncPeriodMonths": 6,
    "estimatedSubmissions": 450,
    "batchSize": 100,
    "syncJobId": "{uuid}",
    "priority": "HIGH"
  }
}
```

##### `HISTORICAL_DATA_COLLECTED`
```json
{
  "eventType": "HISTORICAL_DATA_COLLECTED",
  "aggregateId": "sync-job-{uuid}",
  "sagaId": "{saga-uuid}",
  "data": {
    "userId": "{uuid}",
    "syncJobId": "{uuid}",
    "collectedSubmissions": 387,
    "collectedProblems": 245,
    "syncDurationMinutes": 23,
    "dataQualityScore": 0.98,
    "collectionStats": {
      "totalBatches": 4,
      "successfulBatches": 4,
      "failedBatches": 0,
      "retryCount": 2
    }
  }
}
```

##### `INITIAL_ANALYSIS_COMPLETED`
```json
{
  "eventType": "INITIAL_ANALYSIS_COMPLETED",
  "aggregateId": "analysis-{uuid}",
  "sagaId": "{saga-uuid}",
  "data": {
    "userId": "{uuid}",
    "analysisId": "{uuid}",
    "currentTier": "gold3",
    "strongTags": ["implementation", "math", "string"],
    "weakTags": ["dp", "graph", "tree"],
    "solvedProblems": 387,
    "averageDifficulty": "silver2",
    "streakDays": 45,
    "recommendedNextTier": "gold2"
  }
}
```

#### **복잡한 보상 트랜잭션**

```mermaid
sequenceDiagram
    participant U as User Module
    participant A as Analysis Module
    participant SG as StudyGroup Module
    participant K as Kafka

    Note over U,K: 💥 Step 2 실패 시나리오 (대용량 데이터 수집 실패)
    
    rect rgb(255, 200, 200)
        Note over A: 데이터 수집 중 심각한 오류 발생
        A->>A: collectHistoricalData() [API 서버 다운]
        A->>A: 체크포인트 확인 (70% 완료됨)
        A->>A: Outbox에 실패 이벤트 저장
        A-->>K: HISTORICAL_DATA_COLLECTION_FAILED 발행
    end
    
    rect rgb(255, 200, 200)
        Note over U: 보상 1: 부분 수집 데이터 정리
        K->>U: HISTORICAL_DATA_COLLECTION_FAILED 수신
        U->>U: 진행 중인 SyncJob 상태 확인
        alt 70% 이상 완료된 경우
            U->>U: 부분 데이터 보존 (나중에 재시작 가능)
            U->>U: SyncJob 상태를 PARTIALLY_COMPLETED로 변경
        else 30% 미만인 경우
            U->>U: 수집된 데이터 전체 삭제
            U->>U: SyncJob 상태를 FAILED로 변경
        end
        U->>U: Outbox에 보상 이벤트 저장
        U-->>K: DATA_SYNC_COMPENSATED 발행
    end
    
    rect rgb(255, 200, 200)
        Note over A: 보상 2: 분석 데이터 정리
        K->>A: DATA_SYNC_COMPENSATED 수신
        A->>A: 생성된 부분 분석 결과 정리
        A->>A: 추천 캐시 무효화
        A->>A: Outbox에 보상 이벤트 저장
        A-->>K: ANALYSIS_DATA_REVERTED 발행
    end
    
    rect rgb(255, 200, 200)
        Note over SG: 보상 3: 그룹 프로필 복원
        K->>SG: ANALYSIS_DATA_REVERTED 수신
        SG->>SG: 업데이트된 멤버 프로필 이전 상태로 복원
        SG->>SG: 그룹 통계 재계산
        SG->>SG: Outbox에 보상 이벤트 저장
        SG-->>K: GROUP_PROFILES_REVERTED 발행
    end
```

#### **체크포인트 기반 복구 시스템**

```kotlin
data class DataSyncCheckpoint(
    val syncJobId: UUID,
    val userId: UUID,
    val currentBatch: Int,
    val totalBatches: Int,
    val lastProcessedSubmissionId: Long,
    val collectedCount: Int,
    val failedAttempts: Int,
    val checkpointAt: LocalDateTime,
    val canResume: Boolean
)

@Service
class DataSyncRecoveryService {
    
    fun resumeFromCheckpoint(syncJobId: UUID): Boolean {
        val checkpoint = checkpointRepository.findBySyncJobId(syncJobId)
            ?: return false
            
        if (!checkpoint.canResume || checkpoint.failedAttempts > 3) {
            return false
        }
        
        // 체크포인트부터 재시작
        dataCollectionService.resumeCollection(
            syncJobId = checkpoint.syncJobId,
            startFromBatch = checkpoint.currentBatch,
            lastProcessedId = checkpoint.lastProcessedSubmissionId
        )
        
        return true
    }
    
    fun createCheckpoint(syncJob: DataSyncJob) {
        val checkpoint = DataSyncCheckpoint(
            syncJobId = syncJob.id,
            userId = syncJob.userId,
            currentBatch = syncJob.currentBatch,
            totalBatches = syncJob.totalBatches,
            lastProcessedSubmissionId = syncJob.lastProcessedSubmissionId,
            collectedCount = syncJob.collectedCount,
            failedAttempts = syncJob.failedAttempts,
            checkpointAt = LocalDateTime.now(),
            canResume = syncJob.failedAttempts < 3
        )
        
        checkpointRepository.save(checkpoint)
    }
}
```

---

### **2. USER_REGISTRATION_SAGA**

**목표**: Google OAuth2를 통한 신규 사용자 등록과 초기 프로필 설정

#### **비즈니스 요구사항**
- Google OAuth2로 인증된 사용자만 가입 가능
- 가입 즉시 분석 프로필과 알림 설정 초기화
- 가입 완료 시 환영 이메일 발송
- 모든 단계가 성공해야 가입 완료로 처리

#### **Saga 흐름도**

```mermaid
sequenceDiagram
    participant Client
    participant Google as Google OAuth2
    participant U as User Module
    participant A as Analysis Module
    participant N as Notification Module
    participant K as Kafka

    Note over Client,K: 🔄 USER_REGISTRATION_SAGA
    
    Client->>Google: OAuth2 로그인 시작
    Google-->>Client: authorization_code
    Client->>U: POST /auth/register {authCode}
    
    rect rgb(255, 240, 240)
        Note over U: Step 1: 사용자 계정 생성
        U->>Google: 사용자 정보 검증
        Google-->>U: {email, name, picture}
        U->>U: 중복 이메일 체크
        U->>U: User 엔티티 생성 및 저장
        U->>U: JWT 토큰 생성
        U->>U: Outbox에 이벤트 저장
        U-->>K: USER_REGISTERED 발행
    end
    
    rect rgb(240, 255, 240)
        Note over A: Step 2: 분석 프로필 초기화
        K->>A: USER_REGISTERED 수신
        A->>A: UserAnalysisProfile 생성
        A->>A: 기본 선호도 설정
        A->>A: Outbox에 이벤트 저장
        A-->>K: ANALYSIS_PROFILE_CREATED 발행
    end
    
    rect rgb(240, 240, 255)
        Note over N: Step 3: 알림 설정 및 환영 메시지
        K->>N: USER_REGISTERED 수신
        N->>N: NotificationSettings 생성
        N->>N: 환영 이메일 발송
        N->>N: Outbox에 이벤트 저장
        N-->>K: WELCOME_NOTIFICATION_SENT 발행
    end
    
    U-->>Client: {token, userInfo} 응답
```

#### **이벤트 명세**

##### `USER_REGISTERED`
```json
{
  "eventType": "USER_REGISTERED",
  "aggregateId": "user-{uuid}",
  "sagaId": "{saga-uuid}",
  "data": {
    "userId": "{uuid}",
    "email": "user@gmail.com",
    "nickname": "알고마스터",
    "profileImageUrl": "https://lh3.googleusercontent.com/...",
    "provider": "GOOGLE"
  }
}
```

##### `ANALYSIS_PROFILE_CREATED`
```json
{
  "eventType": "ANALYSIS_PROFILE_CREATED", 
  "aggregateId": "analysis-profile-{uuid}",
  "sagaId": "{saga-uuid}",
  "data": {
    "userId": "{uuid}",
    "profileId": "{uuid}",
    "initializedAt": "2025-07-22T10:30:00Z"
  }
}
```

##### `WELCOME_NOTIFICATION_SENT`
```json
{
  "eventType": "WELCOME_NOTIFICATION_SENT",
  "aggregateId": "notification-{uuid}",
  "sagaId": "{saga-uuid}",
  "data": {
    "userId": "{uuid}",
    "notificationId": "{uuid}",
    "channel": "EMAIL",
    "sentAt": "2025-07-22T10:30:00Z"
  }
}
```

#### **보상 트랜잭션**

```mermaid
sequenceDiagram
    participant U as User Module
    participant A as Analysis Module  
    participant N as Notification Module
    participant K as Kafka

    Note over U,K: 💥 Step 2 실패 시나리오
    
    rect rgb(255, 200, 200)
        Note over A: Analysis Profile 생성 실패
        A->>A: createAnalysisProfile() [FAILED]
        A->>A: Outbox에 실패 이벤트 저장
        A-->>K: ANALYSIS_PROFILE_CREATION_FAILED 발행
    end
    
    rect rgb(255, 200, 200)
        Note over U: 보상: User 삭제
        K->>U: ANALYSIS_PROFILE_CREATION_FAILED 수신
        U->>U: 생성된 User 삭제
        U->>U: JWT 토큰 무효화
        U->>U: Outbox에 보상 이벤트 저장
        U-->>K: USER_REGISTRATION_CANCELLED 발행
    end
```

---

### **2. SOLVEDAC_LINK_SAGA**

**목표**: solved.ac 계정 연동과 모든 관련 서비스의 데이터 동기화

#### **비즈니스 요구사항**
- solved.ac 핸들 유효성 검증 필수
- 연동 즉시 제출 이력 수집 시작
- 참여 중인 스터디 그룹들에 프로필 업데이트
- 연동 완료 알림 발송

#### **Saga 흐름도**

```mermaid
sequenceDiagram
    participant Client
    participant U as User Module
    participant API as solved.ac API
    participant A as Analysis Module
    participant SG as StudyGroup Module
    participant N as Notification Module
    participant K as Kafka

    Note over Client,K: 🔄 SOLVEDAC_LINK_SAGA
    
    Client->>U: POST /users/me/link-solvedac {handle}
    
    rect rgb(255, 240, 240)
        Note over U: Step 1: 핸들 검증 및 계정 연동
        U->>API: solved.ac 사용자 정보 조회
        API-->>U: {handle, tier, solvedCount, ...}
        U->>U: 핸들 중복 체크
        U->>U: SolvedacUser 생성 및 저장
        U->>U: Outbox에 이벤트 저장
        U-->>K: SOLVEDAC_LINKED 발행
    end
    
    rect rgb(240, 255, 240)
        Note over A: Step 2: 제출 이력 수집 시작
        K->>A: SOLVEDAC_LINKED 수신
        A->>API: 제출 이력 조회 (최근 6개월)
        API-->>A: submissions[]
        A->>A: Submissions 일괄 저장
        A->>A: 초기 분석 실행
        A->>A: Outbox에 이벤트 저장
        A-->>K: SUBMISSION_SYNC_STARTED 발행
    end
    
    rect rgb(240, 240, 255)
        Note over SG: Step 3: 스터디 그룹 프로필 업데이트
        K->>SG: SOLVEDAC_LINKED 수신
        SG->>SG: 사용자가 속한 그룹 조회
        loop 각 그룹별로
            SG->>SG: GroupMemberProfile 업데이트
        end
        SG->>SG: Outbox에 이벤트 저장
        SG-->>K: MEMBER_PROFILES_UPDATED 발행
    end
    
    rect rgb(255, 255, 240)
        Note over N: Step 4: 연동 완료 알림
        K->>N: SOLVEDAC_LINKED 수신
        N->>N: 연동 성공 알림 생성
        N->>N: Outbox에 이벤트 저장
        N-->>K: LINK_COMPLETION_NOTIFICATION_SENT 발행
    end
    
    U-->>Client: 연동 완료 응답
```

#### **보상 트랜잭션 (복잡한 시나리오)**

```mermaid
sequenceDiagram
    participant U as User Module
    participant A as Analysis Module
    participant SG as StudyGroup Module
    participant K as Kafka

    Note over U,K: 💥 Step 2 실패 시나리오 (제출 이력 수집 실패)
    
    rect rgb(255, 200, 200)
        Note over A: 제출 이력 수집 실패
        A->>A: fetchSubmissions() [API 오류/타임아웃]
        A->>A: Outbox에 실패 이벤트 저장
        A-->>K: SUBMISSION_SYNC_FAILED 발행
    end
    
    rect rgb(255, 200, 200)
        Note over U: 보상 1: solved.ac 연동 해제
        K->>U: SUBMISSION_SYNC_FAILED 수신
        U->>U: SolvedacUser 삭제
        U->>U: Outbox에 보상 이벤트 저장
        U-->>K: SOLVEDAC_LINK_REVERTED 발행
    end
    
    rect rgb(255, 200, 200)
        Note over SG: 보상 2: 그룹 프로필 복원
        K->>SG: SOLVEDAC_LINK_REVERTED 수신  
        SG->>SG: 업데이트된 프로필 복원
        SG->>SG: Outbox에 보상 이벤트 저장
        SG-->>K: MEMBER_PROFILES_REVERTED 발행
    end
```

---

### **3. CREATE_GROUP_SAGA**

**목표**: 스터디 그룹 생성과 그룹장 설정, 초기 환경 구축

#### **Saga 흐름도**

```mermaid
sequenceDiagram
    participant Client
    participant SG as StudyGroup Module
    participant U as User Module
    participant A as Analysis Module
    participant N as Notification Module
    participant K as Kafka

    Note over Client,K: 🔄 CREATE_GROUP_SAGA
    
    Client->>SG: POST /studygroups {groupInfo}
    
    rect rgb(255, 240, 240)
        Note over SG: Step 1: 그룹 생성 및 그룹장 설정
        SG->>SG: 그룹장 권한 검증
        SG->>SG: StudyGroup 생성
        SG->>SG: 그룹장을 첫 번째 멤버로 추가
        SG->>SG: 초대 코드 생성 (비공개 그룹)
        SG->>SG: Outbox에 이벤트 저장
        SG-->>K: STUDY_GROUP_CREATED 발행
    end
    
    rect rgb(240, 255, 240)
        Note over U: Step 2: 사용자 프로필 업데이트
        K->>U: STUDY_GROUP_CREATED 수신
        U->>U: 소유 그룹 목록에 추가
        U->>U: Outbox에 이벤트 저장
        U-->>K: USER_GROUP_OWNERSHIP_UPDATED 발행
    end
    
    rect rgb(240, 240, 255)
        Note over A: Step 3: 그룹 분석 프로필 초기화
        K->>A: STUDY_GROUP_CREATED 수신
        A->>A: GroupAnalyticsProfile 생성
        A->>A: 그룹장 개인 분석과 연결
        A->>A: Outbox에 이벤트 저장
        A-->>K: GROUP_ANALYTICS_INITIALIZED 발행
    end
    
    rect rgb(255, 255, 240)
        Note over N: Step 4: 그룹 알림 설정
        K->>N: STUDY_GROUP_CREATED 수신
        N->>N: 그룹 알림 설정 생성
        N->>N: 그룹 생성 확인 알림 발송
        N->>N: Outbox에 이벤트 저장
        N-->>K: GROUP_NOTIFICATION_SETUP_COMPLETED 발행
    end
    
    SG-->>Client: 그룹 생성 완료 응답
```

---

### **4. JOIN_GROUP_SAGA**

**목표**: 사용자의 스터디 그룹 참여와 모든 관련 데이터 동기화

이는 가장 복잡한 Saga 중 하나로, 여러 검증 단계와 보상 로직이 필요합니다.

#### **Saga 흐름도**

```mermaid
sequenceDiagram
    participant Client
    participant SG as StudyGroup Module
    participant U as User Module
    participant A as Analysis Module
    participant N as Notification Module
    participant K as Kafka

    Note over Client,K: 🔄 JOIN_GROUP_SAGA (가장 복잡한 Saga)
    
    Client->>SG: POST /studygroups/{id}/join
    
    rect rgb(255, 240, 240)
        Note over SG: Step 1: 그룹 정원 체크 & 임시 예약
        SG->>SG: 그룹 존재 여부 확인
        SG->>SG: 최대 인원 체크
        SG->>SG: 임시 멤버 예약 생성 (5분 TTL)
        SG->>SG: Outbox에 이벤트 저장
        SG-->>K: USER_VALIDATION_REQUESTED 발행
    end
    
    rect rgb(240, 255, 240)
        Note over U: Step 2: 사용자 자격 검증
        K->>U: USER_VALIDATION_REQUESTED 수신
        U->>U: 사용자 활성 상태 확인
        U->>U: solved.ac 연동 상태 확인 (선택적)
        U->>U: 이미 참여한 그룹인지 확인
        U->>U: Outbox에 이벤트 저장
        U-->>K: USER_VALIDATED 발행
    end
    
    rect rgb(240, 240, 255)
        Note over SG: Step 3: 정식 멤버 전환
        K->>SG: USER_VALIDATED 수신
        SG->>SG: 임시 예약을 정식 멤버로 전환
        SG->>SG: 멤버 수 증가
        SG->>SG: Outbox에 이벤트 저장
        SG-->>K: MEMBER_JOINED 발행
    end
    
    rect rgb(255, 240, 255)
        Note over A: Step 4: 분석 데이터 동기화
        K->>A: MEMBER_JOINED 수신
        A->>A: 사용자 분석 데이터를 그룹에 동기화
        A->>A: 그룹 통계 재계산
        A->>A: Outbox에 이벤트 저장
        A-->>K: MEMBER_ANALYSIS_SYNCED 발행
    end
    
    rect rgb(255, 255, 240)
        Note over N: Step 5: 환영 알림 발송
        K->>N: MEMBER_JOINED 수신
        N->>N: 그룹장에게 새 멤버 알림
        N->>N: 참여자에게 환영 알림
        N->>N: Outbox에 이벤트 저장
        N-->>K: WELCOME_NOTIFICATIONS_SENT 발행
    end
    
    SG-->>Client: 참여 완료 응답
```

#### **복잡한 보상 시나리오들**

##### **시나리오 1: 사용자 검증 실패**

```mermaid
sequenceDiagram
    participant SG as StudyGroup Module
    participant U as User Module
    participant K as Kafka

    rect rgb(255, 200, 200)
        Note over U: Step 2에서 검증 실패
        U->>U: validateUser() [비활성 사용자]
        U->>U: Outbox에 실패 이벤트 저장
        U-->>K: USER_VALIDATION_FAILED 발행
    end
    
    rect rgb(255, 200, 200)
        Note over SG: 보상: 임시 예약 삭제
        K->>SG: USER_VALIDATION_FAILED 수신
        SG->>SG: 임시 멤버 예약 삭제
        SG->>SG: Outbox에 보상 이벤트 저장
        SG-->>K: JOIN_GROUP_CANCELLED 발행
    end
```

##### **시나리오 2: 분석 동기화 실패**

```mermaid
sequenceDiagram
    participant SG as StudyGroup Module
    participant U as User Module
    participant A as Analysis Module
    participant K as Kafka

    rect rgb(255, 200, 200)
        Note over A: Step 4에서 분석 동기화 실패
        A->>A: syncMemberAnalysis() [DB 오류]
        A->>A: Outbox에 실패 이벤트 저장
        A-->>K: MEMBER_ANALYSIS_SYNC_FAILED 발행
    end
    
    rect rgb(255, 200, 200)
        Note over SG: 보상 1: 멤버 제거
        K->>SG: MEMBER_ANALYSIS_SYNC_FAILED 수신
        SG->>SG: 추가된 멤버 제거
        SG->>SG: 멤버 수 감소
        SG->>SG: Outbox에 보상 이벤트 저장
        SG-->>K: MEMBER_REMOVED_DUE_TO_SYNC_FAILURE 발행
    end
    
    rect rgb(255, 200, 200)
        Note over U: 보상 2: 사용자 프로필 복원
        K->>U: MEMBER_REMOVED_DUE_TO_SYNC_FAILURE 수신
        U->>U: 참여 그룹 목록에서 제거
        U->>U: Outbox에 보상 이벤트 저장
        U-->>K: USER_GROUP_MEMBERSHIP_REVERTED 발행
    end
```

---

### **5. SUBMISSION_SYNC_SAGA**

**목표**: solved.ac에서 새로운 제출 데이터를 수집하여 전체 시스템에 동기화

#### **Saga 흐름도**

```mermaid
sequenceDiagram
    participant Scheduler as Data Collector
    participant API as solved.ac API
    participant A as Analysis Module
    participant SG as StudyGroup Module
    participant N as Notification Module
    participant K as Kafka

    Note over Scheduler,K: 🔄 SUBMISSION_SYNC_SAGA (5분마다 실행)
    
    Scheduler->>A: triggerSubmissionSync()
    A->>API: 연동된 사용자들의 최신 제출 조회
    API-->>A: newSubmissions[]
    
    loop 각 새 제출별로
        rect rgb(255, 240, 240)
            Note over A: Step 1: 제출 데이터 저장 및 분석
            A->>A: Submission 엔티티 저장
            A->>A: 문제 메타데이터 업데이트
            A->>A: 사용자 분석 데이터 실시간 업데이트
            A->>A: Outbox에 이벤트 저장
            A-->>K: SUBMISSION_PROCESSED 발행
        end
        
        rect rgb(240, 255, 240)
            Note over SG: Step 2: 그룹 활동 업데이트
            K->>SG: SUBMISSION_PROCESSED 수신
            SG->>SG: 멤버 활동 기록 업데이트
            SG->>SG: 할당된 문제 완료 체크
            SG->>SG: 그룹 통계 업데이트
            SG->>SG: Outbox에 이벤트 저장
            SG-->>K: MEMBER_ACTIVITY_UPDATED 발행
        end
        
        rect rgb(240, 240, 255)
            Note over N: Step 3: 성취 알림 체크
            K->>N: MEMBER_ACTIVITY_UPDATED 수신
            N->>N: 연속 해결 기록 체크
            N->>N: 할당 문제 완료 알림
            alt 특별한 성취 달성
                N->>N: 성취 알림 생성
                N->>N: Outbox에 이벤트 저장
                N-->>K: ACHIEVEMENT_NOTIFICATION_SENT 발행
            end
        end
    end
```

---

### **6. ANALYSIS_UPDATE_SAGA**

**목표**: 정기적인 사용자/그룹 분석 결과 업데이트와 추천 갱신

#### **Saga 흐름도**

```mermaid  
sequenceDiagram
    participant Scheduler
    participant A as Analysis Module
    participant SG as StudyGroup Module
    participant N as Notification Module
    participant K as Kafka

    Note over Scheduler,K: 🔄 ANALYSIS_UPDATE_SAGA (일간 실행)
    
    Scheduler->>A: triggerDailyAnalysisUpdate()
    
    rect rgb(255, 240, 240)
        Note over A: Step 1: 분석 결과 생성
        A->>A: 어제 제출 데이터 집계
        A->>A: 태그별 숙련도 재계산
        A->>A: 취약점/강점 분석 업데이트
        A->>A: 개인 맞춤 추천 생성
        A->>A: Outbox에 이벤트 저장
        A-->>K: ANALYSIS_UPDATED 발행
    end
    
    rect rgb(240, 255, 240)
        Note over SG: Step 2: 그룹 분석 업데이트
        K->>SG: ANALYSIS_UPDATED 수신
        SG->>SG: 멤버 프로필 동기화
        SG->>SG: 그룹 전체 통계 재계산
        SG->>SG: 그룹 강점/약점 분석
        SG->>SG: Outbox에 이벤트 저장
        SG-->>K: GROUP_ANALYSIS_UPDATED 발행
    end
    
    rect rgb(240, 240, 255)
        Note over N: Step 3: 진전 상황 알림
        K->>N: ANALYSIS_UPDATED 수신
        N->>N: 주요 향상 사항 감지
        alt 눈에 띄는 발전 있음
            N->>N: 개인 진전 알림 생성
            N->>N: Outbox에 이벤트 저장
            N-->>K: PROGRESS_NOTIFICATION_SENT 발행
        end
    end
```

---

## 🎯 **구현 순서 및 테스트 전략**

### **1단계: 기본 인프라**
1. ✅ Outbox Pattern 기본 구현
2. ✅ Saga Coordinator 인터페이스
3. ✅ 기본 이벤트 발행/구독 구조

### **2단계: 데이터 파이프라인 구축 (NEXT_TASKS.md Phase 1 우선순위)**
1. ✅ `INITIAL_DATA_SYNC_SAGA` - **최우선 구현** (대용량 데이터 수집)
2. ✅ `SUBMISSION_SYNC_SAGA` - 실시간 데이터 동기화

### **3단계: 사용자 관리 기반 구축**
3. ✅ `USER_REGISTRATION_SAGA` - 가장 단순한 3단계 Saga
4. ✅ `SOLVEDAC_LINK_SAGA` - INITIAL_DATA_SYNC_SAGA와 연계

### **4단계: 그룹 관리 기능**
5. ✅ `CREATE_GROUP_SAGA` - 4단계 Saga로 복잡도 증가
6. ✅ `JOIN_GROUP_SAGA` - 가장 복잡한 5단계 보상 로직

### **5단계: 분석 및 최적화**
7. ✅ `ANALYSIS_UPDATE_SAGA` - 대용량 데이터 처리

### **테스트 전략**

```kotlin
// 각 Saga별 테스트 클래스 예시
@SpringBootTest
@TestPropertySource(properties = ["kafka.enabled=false"])
class UserRegistrationSagaTest {
    
    @Test
    fun `사용자 등록 Saga 성공 시나리오`() {
        // Given: OAuth2 인증 코드와 사용자 정보
        val authCode = "mock_auth_code"
        val expectedUserInfo = createMockUserInfo()
        
        // When: 회원가입 요청
        val result = userRegistrationSaga.start(authCode)
        
        // Then: 모든 단계 완료 확인
        assertThat(result.sagaStatus).isEqualTo(SagaStatus.COMPLETED)
        assertThat(userRepository.findByEmail(expectedUserInfo.email)).isNotNull()
        assertThat(analysisService.hasProfile(result.userId)).isTrue()
        assertThat(notificationService.hasSettings(result.userId)).isTrue()
    }
    
    @Test
    fun `분석 프로필 생성 실패 시 보상 트랜잭션 실행`() {
        // Given: 분석 서비스 장애 상황
        whenever(analysisService.createProfile(any())).thenThrow(RuntimeException("DB Error"))
        
        // When: 회원가입 시도
        val result = userRegistrationSaga.start("auth_code")
        
        // Then: Saga 실패 및 보상 실행 확인
        assertThat(result.sagaStatus).isEqualTo(SagaStatus.COMPENSATED)
        assertThat(userRepository.findByEmail(any())).isNull() // 사용자 삭제됨
    }
}
```

---

📝 **문서 버전**: v1.0  
📅 **최종 수정일**: 2025-07-22  
👤 **작성자**: 채기훈