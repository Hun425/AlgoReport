# Phase 2 확장 Saga 설계

이 문서는 **알고리포트 Phase 2에서 구현할 7개 확장 Saga**의 상세 설계를 다룹니다. 이들은 플랫폼의 고급 기능과 사용자 경험 향상을 위한 분산 트랜잭션들입니다.

---

## 🎯 **Phase 2 Saga 개요**

| 순서 | Saga 이름 | 복잡도 | 트리거 | 관련 모듈 | 구현 우선순위 |
|-----|----------|-------|--------|----------|-------------|
| 10 | `LEAVE_GROUP_SAGA` | High | 사용자 요청 | StudyGroup, User, Analysis, Notification | 🟡 Important |
| 11 | `GROUP_RULE_UPDATE_SAGA` | Medium | 그룹장 요청 | StudyGroup, Analysis, Notification | 🟡 Important |
| 12 | `PROBLEM_ASSIGNMENT_SAGA` | High | 스케줄러/그룹장 | StudyGroup, Analysis, Notification | 🟡 Important |
| 13 | `RULE_VIOLATION_SAGA` | High | 스케줄러 | Analysis, StudyGroup, Notification | 🟡 Important |
| 14 | `USER_ACHIEVEMENT_SAGA` | Medium | 조건 달성 시 | Analysis, User, Notification | 🔵 Normal |
| 15 | `RECOMMENDATION_GENERATION_SAGA` | Medium | 스케줄러 | Analysis, StudyGroup, Notification | 🔵 Normal |
| 16 | `GROUP_ACHIEVEMENT_SAGA` | Medium | 스케줄러 | StudyGroup, Analysis, Notification | 🔵 Normal |

---

## 📋 **상세 Saga 설계**

### **14. USER_ACHIEVEMENT_SAGA**

**목표**: 개인 성취/배지 획득과 관련 모듈 동기화 및 알림

#### **비즈니스 요구사항**
- 개인 성취 조건 달성 시 자동 배지 부여
- 성취 이력 관리 및 프로필 업데이트
- 성취 알림 및 공유 기능
- 스터디 그룹 내 성취 공지

#### **성취 타입**

```kotlin
enum class PersonalAchievementType {
    PROBLEM_MILESTONE,           // "100문제, 500문제, 1000문제 해결"
    TIER_PROMOTION,             // "티어 승급 (Bronze → Silver 등)"
    TAG_MASTERY,               // "특정 태그 90% 이상 숙련도"
    STREAK_ACHIEVEMENT,        // "연속 해결 기록 (7일, 30일, 100일)"
    SPEED_SOLVING,             // "빠른 문제 해결 (1시간 내 10문제 등)"
    DIFFICULTY_CHALLENGE,      // "본인 티어 +2 이상 문제 해결"
    CONSISTENCY,               // "매일 꾸준히 문제 해결"
    FIRST_BLOOD               // "새로 출제된 문제 최초 해결"
}
```

#### **Saga 흐름도**

```mermaid
sequenceDiagram
    participant A as Analysis Module
    participant U as User Module
    participant SG as StudyGroup Module
    participant N as Notification Module
    participant K as Kafka

    Note over A,K: 🔄 USER_ACHIEVEMENT_SAGA (조건 달성 시 자동 트리거)
    
    A->>A: 제출 데이터 분석 중 성취 조건 감지
    
    rect rgb(255, 240, 240)
        Note over A: Step 1: 성취 검증 및 배지 생성
        A->>A: 성취 조건 재검증 (중복 방지)
        A->>A: PersonalAchievement 엔티티 생성
        A->>A: 배지 메타데이터 저장
        A->>A: 성취 통계 업데이트
        A->>A: Outbox에 이벤트 저장
        A-->>K: USER_ACHIEVEMENT_UNLOCKED 발행
    end
    
    rect rgb(240, 255, 240)
        Note over U: Step 2: 사용자 프로필 배지 동기화
        K->>U: USER_ACHIEVEMENT_UNLOCKED 수신
        U->>U: 사용자 프로필에 새 배지 추가
        U->>U: 배지 카운트 및 레벨 업데이트
        U->>U: 성취 이력 저장
        U->>U: Outbox에 이벤트 저장
        U-->>K: USER_PROFILE_ACHIEVEMENT_SYNCED 발행
    end
    
    rect rgb(240, 240, 255)
        Note over SG: Step 3: 스터디 그룹 성취 공지
        K->>SG: USER_ACHIEVEMENT_UNLOCKED 수신
        SG->>SG: 사용자가 속한 그룹들 조회
        loop 각 그룹별로
            SG->>SG: 그룹 성취 피드에 추가
            SG->>SG: 그룹 성취 통계 업데이트
        end
        SG->>SG: Outbox에 이벤트 저장
        SG-->>K: GROUP_ACHIEVEMENT_FEED_UPDATED 발행
    end
    
    rect rgb(255, 255, 240)
        Note over N: Step 4: 성취 축하 알림
        K->>N: USER_ACHIEVEMENT_UNLOCKED 수신
        N->>N: 성취 축하 알림 생성
        N->>N: 중요 성취의 경우 그룹원들에게도 알림
        alt 마일스톤 성취 (100문제, 티어승급 등)
            N->>N: 특별 축하 알림 및 이벤트 생성
            N->>N: SNS 공유 기능 제공
        end
        N->>N: Outbox에 이벤트 저장
        N-->>K: ACHIEVEMENT_CELEBRATION_SENT 발행
    end
```

#### **이벤트 명세**

##### `USER_ACHIEVEMENT_UNLOCKED`
```json
{
  "eventType": "USER_ACHIEVEMENT_UNLOCKED",
  "aggregateId": "achievement-{uuid}",
  "sagaId": "{saga-uuid}",
  "data": {
    "userId": "{uuid}",
    "achievementId": "{uuid}",
    "achievementType": "PROBLEM_MILESTONE",
    "title": "문제 해결 마스터",
    "description": "500문제 해결 달성",
    "badgeImageUrl": "https://badges.algoreport.com/500problems.png",
    "rarity": "RARE",
    "points": 100,
    "unlockedAt": "2025-07-22T15:30:00Z",
    "triggerData": {
      "problemCount": 500,
      "milestoneType": "PROBLEM_COUNT"
    }
  }
}
```

#### **보상 트랜잭션**

```mermaid
sequenceDiagram
    participant A as Analysis Module
    participant U as User Module
    participant K as Kafka

    Note over A,K: 💥 Step 2 실패 시나리오 (프로필 동기화 실패)
    
    rect rgb(255, 200, 200)
        Note over U: 사용자 프로필 배지 동기화 실패
        U->>U: addAchievementToBadge() [DB 오류]
        U->>U: Outbox에 실패 이벤트 저장
        U-->>K: USER_PROFILE_ACHIEVEMENT_SYNC_FAILED 발행
    end
    
    rect rgb(255, 200, 200)
        Note over A: 보상: 성취 데이터 롤백
        K->>A: USER_PROFILE_ACHIEVEMENT_SYNC_FAILED 수신
        A->>A: 생성된 PersonalAchievement 삭제
        A->>A: 성취 통계 원복
        A->>A: Outbox에 보상 이벤트 저장
        A-->>K: USER_ACHIEVEMENT_REVERTED 발행
    end
```

---

### **10. LEAVE_GROUP_SAGA**

**목표**: 사용자의 스터디 그룹 탈퇴와 모든 관련 데이터 정리

#### **비즈니스 요구사항**
- 그룹장 탈퇴 시 소유권 이전 또는 그룹 해체
- 할당된 문제 정리 및 보상
- 분석 데이터에서 그룹 관련 정보 제거
- 탈퇴 알림 발송

#### **Saga 흐름도**

```mermaid
sequenceDiagram
    participant Client
    participant SG as StudyGroup Module
    participant U as User Module
    participant A as Analysis Module
    participant N as Notification Module
    participant K as Kafka

    Note over Client,K: 🔄 LEAVE_GROUP_SAGA (복잡한 정리 작업)
    
    Client->>SG: DELETE /studygroups/{id}/members/me
    
    rect rgb(255, 240, 240)
        Note over SG: Step 1: 탈퇴 타입 결정 및 처리
        SG->>SG: 사용자 역할 확인 (OWNER/MEMBER)
        alt 그룹장인 경우
            SG->>SG: 다른 관리자에게 소유권 이전
            alt 관리자가 없는 경우
                SG->>SG: 그룹 해체 준비
            end
        else 일반 멤버인 경우
            SG->>SG: 단순 멤버 제거
        end
        SG->>SG: Outbox에 이벤트 저장
        SG-->>K: MEMBER_LEAVE_INITIATED 발행
    end
    
    rect rgb(240, 255, 240)
        Note over U: Step 2: 사용자 프로필 업데이트
        K->>U: MEMBER_LEAVE_INITIATED 수신
        U->>U: 참여 그룹 목록에서 제거
        U->>U: 그룹 관련 권한 정리
        U->>U: Outbox에 이벤트 저장
        U-->>K: USER_GROUP_MEMBERSHIP_UPDATED 발행
    end
    
    rect rgb(240, 240, 255)
        Note over SG: Step 3: 할당 문제 정리
        K->>SG: USER_GROUP_MEMBERSHIP_UPDATED 수신
        SG->>SG: 탈퇴자의 미완료 문제들 조회
        loop 각 할당 문제별로
            SG->>SG: 문제 할당 취소 또는 재할당
        end
        SG->>SG: 그룹 통계 재계산
        SG->>SG: Outbox에 이벤트 저장
        SG-->>K: ASSIGNMENTS_CLEANED_UP 발행
    end
    
    rect rgb(255, 240, 255)
        Note over A: Step 4: 분석 데이터 정리
        K->>A: ASSIGNMENTS_CLEANED_UP 수신
        A->>A: 그룹 분석에서 해당 사용자 데이터 제거
        A->>A: 그룹 추천 캐시 무효화
        A->>A: 개인 분석에서 그룹 관련 컨텍스트 정리
        A->>A: Outbox에 이벤트 저장
        A-->>K: ANALYSIS_DATA_CLEANED 발행
    end
    
    rect rgb(255, 255, 240)
        Note over N: Step 5: 탈퇴 알림 발송
        K->>N: MEMBER_LEAVE_INITIATED 수신
        N->>N: 그룹장에게 탈퇴 알림
        N->>N: 탈퇴자에게 확인 알림
        alt 그룹 해체된 경우
            N->>N: 모든 멤버에게 해체 알림
        end
        N->>N: Outbox에 이벤트 저장
        N-->>K: LEAVE_NOTIFICATIONS_SENT 발행
    end
    
    SG-->>Client: 탈퇴 완료 응답
```

#### **복잡한 보상 시나리오: 그룹장 탈퇴 실패**

```mermaid
sequenceDiagram
    participant SG as StudyGroup Module
    participant U as User Module
    participant N as Notification Module
    participant K as Kafka

    rect rgb(255, 200, 200)
        Note over SG: Step 1에서 소유권 이전 실패
        SG->>SG: transferOwnership() [새 소유자 없음]
        SG->>SG: Outbox에 실패 이벤트 저장
        SG-->>K: GROUP_OWNERSHIP_TRANSFER_FAILED 발행
    end
    
    rect rgb(255, 200, 200)
        Note over N: 대안 처리: 유예 기간 제공
        K->>N: GROUP_OWNERSHIP_TRANSFER_FAILED 수신
        N->>N: 그룹장에게 유예 기간 알림 생성
        N->>N: 다른 멤버들에게 소유권 요청 알림
        N->>N: Outbox에 이벤트 저장
        N-->>K: OWNERSHIP_GRACE_PERIOD_STARTED 발행
    end
    
    rect rgb(255, 200, 200)
        Note over SG: 최종 처리: 7일 후 강제 해체
        K->>SG: OWNERSHIP_GRACE_PERIOD_STARTED 수신
        SG->>SG: 7일 후 해체 스케줄 등록
        SG->>SG: 그룹 상태를 PENDING_DISSOLUTION로 변경
        SG->>SG: Outbox에 이벤트 저장
        SG-->>K: GROUP_DISSOLUTION_SCHEDULED 발행
    end
```

---

### **8. GROUP_RULE_UPDATE_SAGA**

**목표**: 스터디 그룹 규칙 변경과 모든 관련 시스템 업데이트

#### **Saga 흐름도**

```mermaid
sequenceDiagram
    participant Client
    participant SG as StudyGroup Module
    participant A as Analysis Module
    participant N as Notification Module
    participant K as Kafka

    Note over Client,K: 🔄 GROUP_RULE_UPDATE_SAGA
    
    Client->>SG: PUT /studygroups/{id}/rules {newRules}
    
    rect rgb(255, 240, 240)
        Note over SG: Step 1: 규칙 변경 검증 및 적용
        SG->>SG: 그룹장 권한 확인
        SG->>SG: 새 규칙 유효성 검증
        SG->>SG: 기존 규칙과 비교 분석
        SG->>SG: GROUP_RULES 테이블 업데이트
        SG->>SG: Outbox에 이벤트 저장
        SG-->>K: GROUP_RULES_UPDATED 발행
    end
    
    rect rgb(240, 255, 240)
        Note over A: Step 2: 분석 엔진 규칙 동기화
        K->>A: GROUP_RULES_UPDATED 수신
        A->>A: 그룹 분석 알고리즘 파라미터 업데이트
        A->>A: 위반 감지 로직 재설정
        A->>A: 기존 분석 결과 재평가
        A->>A: Outbox에 이벤트 저장
        A-->>K: ANALYSIS_RULES_SYNCHRONIZED 발행
    end
    
    rect rgb(240, 240, 255)
        Note over N: Step 3: 멤버들에게 규칙 변경 알림
        K->>N: GROUP_RULES_UPDATED 수신
        N->>N: 모든 그룹 멤버에게 변경사항 알림
        N->>N: 중요한 변경의 경우 이메일 발송
        N->>N: Outbox에 이벤트 저장
        N-->>K: RULE_CHANGE_NOTIFICATIONS_SENT 발행
    end
    
    SG-->>Client: 규칙 업데이트 완료 응답
```

#### **이벤트 명세**

##### `GROUP_RULES_UPDATED`
```json
{
  "eventType": "GROUP_RULES_UPDATED",
  "aggregateId": "study-group-{uuid}",
  "sagaId": "{saga-uuid}",
  "data": {
    "groupId": "{uuid}",
    "groupName": "알고리즘 마스터즈",
    "updatedBy": "{user-uuid}",
    "updatedByNickname": "그룹장",
    "changes": [
      {
        "ruleType": "MINIMUM_PROBLEMS_PER_WEEK",
        "oldValue": 2,
        "newValue": 3,
        "changeReason": "난이도 상향 조정"
      }
    ],
    "newRuleSet": {
      "minimumProblemsPerWeek": 3,
      "allowedDifficultyRange": ["bronze1", "gold5"],
      "deadlinePenalty": {
        "type": "WARNING",
        "escalationDays": 3
      }
    }
  }
}
```

---

### **9. PROBLEM_ASSIGNMENT_SAGA**

**목표**: 스터디 그룹 내 문제 자동/수동 할당과 관련 데이터 동기화

#### **Saga 흐름도**

```mermaid
sequenceDiagram
    participant Trigger as Scheduler/Admin
    participant SG as StudyGroup Module
    participant A as Analysis Module
    participant N as Notification Module
    participant K as Kafka

    Note over Trigger,K: 🔄 PROBLEM_ASSIGNMENT_SAGA
    
    alt 자동 할당 (스케줄러)
        Trigger->>SG: triggerAutoAssignment()
    else 수동 할당 (그룹장)
        Trigger->>SG: POST /studygroups/{id}/assignments
    end
    
    rect rgb(255, 240, 240)
        Note over SG: Step 1: 할당 대상 및 문제 결정
        SG->>SG: 그룹 규칙 및 멤버 현황 조회
        alt 자동 할당인 경우
            SG->>SG: 각 멤버의 취약점 기반 문제 후보 생성
        else 수동 할당인 경우
            SG->>SG: 그룹장이 지정한 문제/멤버 검증
        end
        SG->>SG: ASSIGNED_PROBLEMS 테이블에 할당 기록
        SG->>SG: Outbox에 이벤트 저장
        SG-->>K: PROBLEMS_ASSIGNED 발행
    end
    
    rect rgb(240, 255, 240)
        Note over A: Step 2: 분석 데이터 업데이트
        K->>A: PROBLEMS_ASSIGNED 수신
        A->>A: 각 사용자의 추천 캐시 무효화
        A->>A: 개인 학습 계획에 할당 문제 반영
        A->>A: 그룹 전체 난이도 분포 재계산
        A->>A: Outbox에 이벤트 저장
        A-->>K: ASSIGNMENT_ANALYSIS_UPDATED 발행
    end
    
    rect rgb(240, 240, 255)
        Note over N: Step 3: 할당 알림 발송
        K->>N: PROBLEMS_ASSIGNED 수신
        loop 각 할당받은 멤버별로
            N->>N: 개인별 맞춤 할당 알림 생성
            N->>N: 마감일 기반 리마인더 스케줄 등록
        end
        N->>N: 그룹장에게 할당 완료 요약 알림
        N->>N: Outbox에 이벤트 저장
        N-->>K: ASSIGNMENT_NOTIFICATIONS_SENT 발행
    end
```

#### **자동 할당 로직**

```kotlin
data class AssignmentCriteria(
    val memberWeaknessMap: Map<String, List<String>>, // userId -> 취약 태그들
    val groupDifficultyRange: Pair<String, String>,   // 그룹 난이도 범위
    val weeklyQuota: Int,                             // 주간 할당 문제 수
    val avoidRecentlySolved: Boolean = true           // 최근 풀은 문제 제외
)

@Service
class AutoAssignmentService {
    
    fun generateAssignments(groupId: UUID): List<ProblemAssignment> {
        val group = studyGroupRepository.findById(groupId)
        val activeMembers = getActiveMembers(groupId)
        val groupRules = getGroupRules(groupId)
        
        return activeMembers.flatMap { member ->
            val weaknessTags = analysisService.getMemberWeaknesses(member.userId)
            val availableProblems = problemRepository.findProblemsForWeaknesses(
                tags = weaknessTags,
                tierRange = groupRules.difficultyRange,
                excludeSolved = member.solvedProblems,
                limit = groupRules.weeklyQuota
            )
            
            availableProblems.map { problem ->
                ProblemAssignment(
                    groupId = groupId,
                    userId = member.userId,
                    problemId = problem.id,
                    assignmentType = AssignmentType.AUTO,
                    dueDate = calculateDueDate(groupRules),
                    reasoning = "취약 태그: ${weaknessTags.joinToString()}"
                )
            }
        }
    }
}
```

---

### **10. RULE_VIOLATION_SAGA**

**목표**: 그룹 규칙 위반 감지 및 자동 처리

#### **Saga 흐름도**

```mermaid
sequenceDiagram
    participant Scheduler as Violation Detector
    participant A as Analysis Module
    participant SG as StudyGroup Module
    participant N as Notification Module
    participant K as Kafka

    Note over Scheduler,K: 🔄 RULE_VIOLATION_SAGA (매일 실행)
    
    Scheduler->>A: triggerViolationCheck()
    
    rect rgb(255, 240, 240)
        Note over A: Step 1: 위반 사례 감지
        A->>A: 모든 활성 그룹의 규칙 조회
        A->>A: 각 그룹별 멤버 활동 분석
        loop 각 그룹별로
            A->>A: 규칙 대비 실제 활동 비교
            A->>A: 위반 정도 계산 (경고/주의/심각)
        end
        A->>A: 위반 사례들 정리
        A->>A: Outbox에 이벤트 저장
        A-->>K: VIOLATIONS_DETECTED 발행
    end
    
    rect rgb(240, 255, 240)
        Note over SG: Step 2: 위반 기록 저장 및 조치 결정
        K->>SG: VIOLATIONS_DETECTED 수신
        loop 각 위반 사례별로
            SG->>SG: 위반 기록을 테이블에 저장
            SG->>SG: 누적 위반 횟수 확인
            alt 경고 수준
                SG->>SG: 경고 카운트 증가
            else 심각한 위반
                SG->>SG: 멤버 자격 정지 또는 강제 탈퇴 준비
            end
        end
        SG->>SG: Outbox에 이벤트 저장
        SG-->>K: VIOLATION_ACTIONS_DETERMINED 발행
    end
    
    rect rgb(240, 240, 255)
        Note over N: Step 3: 위반 알림 및 조치 통보
        K->>N: VIOLATION_ACTIONS_DETERMINED 수신
        loop 각 위반자별로
            N->>N: 개인별 위반 내역 알림 생성
            alt 경고인 경우
                N->>N: 개선 방안 제시 알림
            else 조치인 경우  
                N->>N: 조치 내역 통보
            end
        end
        N->>N: 그룹장에게 위반 현황 요약 리포트
        N->>N: Outbox에 이벤트 저장
        N-->>K: VIOLATION_NOTIFICATIONS_SENT 발행
    end
```

#### **위반 감지 로직**

```kotlin
data class ViolationCase(
    val userId: String,
    val groupId: UUID,
    val violationType: ViolationType,
    val severity: ViolationSeverity,
    val details: ViolationDetails,
    val detectedAt: LocalDateTime
)

enum class ViolationType {
    INSUFFICIENT_WEEKLY_PROBLEMS,    // 주간 문제 수 부족
    MISSED_DEADLINE,                 // 마감일 미준수
    CONSECUTIVE_INACTIVITY,          // 연속 비활성
    ASSIGNMENT_IGNORE               // 할당 문제 무시
}

enum class ViolationSeverity {
    WARNING,    // 경고 (1-2회)
    CAUTION,    // 주의 (3-4회)  
    CRITICAL    // 심각 (5회 이상, 조치 필요)
}

@Service
class ViolationDetectionService {
    
    fun detectViolations(): List<ViolationCase> {
        val activeGroups = studyGroupRepository.findActiveGroups()
        
        return activeGroups.flatMap { group ->
            val rules = getGroupRules(group.id)
            val members = getActiveMembers(group.id)
            
            members.mapNotNull { member ->
                checkMemberViolations(member, rules, group)
            }
        }
    }
    
    private fun checkMemberViolations(
        member: GroupMember, 
        rules: GroupRules, 
        group: StudyGroup
    ): ViolationCase? {
        val weeklySubmissions = getWeeklySubmissions(member.userId)
        val requiredCount = rules.minimumProblemsPerWeek
        
        return when {
            weeklySubmissions < requiredCount -> {
                val previousViolations = getViolationHistory(member.userId, group.id)
                val severity = calculateSeverity(previousViolations.size)
                
                ViolationCase(
                    userId = member.userId,
                    groupId = group.id,
                    violationType = ViolationType.INSUFFICIENT_WEEKLY_PROBLEMS,
                    severity = severity,
                    details = ViolationDetails(
                        expected = requiredCount,
                        actual = weeklySubmissions,
                        deficit = requiredCount - weeklySubmissions
                    ),
                    detectedAt = LocalDateTime.now()
                )
            }
            else -> null
        }
    }
}
```

---

### **11. RECOMMENDATION_GENERATION_SAGA**

**목표**: 개인/그룹 맞춤 문제 추천 생성 및 배포

#### **Saga 흐름도**

```mermaid
sequenceDiagram
    participant Scheduler
    participant A as Analysis Module
    participant SG as StudyGroup Module
    participant N as Notification Module
    participant K as Kafka

    Note over Scheduler,K: 🔄 RECOMMENDATION_GENERATION_SAGA (매일 새벽 실행)
    
    Scheduler->>A: triggerRecommendationGeneration()
    
    rect rgb(255, 240, 240)
        Note over A: Step 1: 추천 알고리즘 실행
        A->>A: 모든 활성 사용자의 최신 분석 데이터 조회
        A->>A: 취약점 기반 추천 생성
        A->>A: 진전도 기반 추천 생성
        A->>A: 협업 필터링 기반 추천 생성
        A->>A: RECOMMENDATION_CACHE 테이블 업데이트
        A->>A: Outbox에 이벤트 저장
        A-->>K: RECOMMENDATIONS_GENERATED 발행
    end
    
    rect rgb(240, 255, 240)
        Note over SG: Step 2: 그룹별 추천 동기화
        K->>SG: RECOMMENDATIONS_GENERATED 수신
        A->>A: 각 그룹별 멤버 추천 집계
        A->>A: 그룹 전체 추천 문제 선별
        A->>A: 중복 제거 및 우선순위 조정
        A->>A: Outbox에 이벤트 저장
        A-->>K: GROUP_RECOMMENDATIONS_PREPARED 발행
    end
    
    rect rgb(240, 240, 255)
        Note over N: Step 3: 추천 알림 발송
        K->>N: GROUP_RECOMMENDATIONS_PREPARED 수신
        N->>N: 사용자별 추천 알림 설정 확인
        loop 알림 수신 동의 사용자별로
            N->>N: 개인별 맞춤 추천 알림 생성
            N->>N: 추천 이유 및 학습 경로 포함
        end
        N->>N: Outbox에 이벤트 저장
        N-->>K: RECOMMENDATION_NOTIFICATIONS_SENT 발행
    end
```

#### **추천 알고리즘**

```kotlin
interface RecommendationEngine {
    fun generateRecommendations(userId: String): List<ProblemRecommendation>
}

@Component
class WeaknessBasedRecommendationEngine : RecommendationEngine {
    
    override fun generateRecommendations(userId: String): List<ProblemRecommendation> {
        val userProfile = analysisService.getUserProfile(userId)
        val weakTags = userProfile.tagProficiency
            .filter { it.value < 0.6 } // 60% 미만 숙련도
            .keys.toList()
        
        return weakTags.flatMap { tag ->
            problemRepository.findSuitableProblems(
                tag = tag,
                userTier = userProfile.currentTier,
                excludeSolved = userProfile.solvedProblems,
                limit = 3
            ).map { problem ->
                ProblemRecommendation(
                    problemId = problem.id,
                    recommendationType = RecommendationType.WEAKNESS_IMPROVEMENT,
                    targetTag = tag,
                    difficulty = problem.tier,
                    reason = "취약한 ${tag} 알고리즘 보완을 위한 추천",
                    expectedImprovement = calculateImprovement(userProfile, problem)
                )
            }
        }.sortedByDescending { it.expectedImprovement }
         .take(10)
    }
}

@Component  
class CollaborativeFilteringEngine : RecommendationEngine {
    
    override fun generateRecommendations(userId: String): List<ProblemRecommendation> {
        val userProfile = analysisService.getUserProfile(userId)
        val similarUsers = findSimilarUsers(userProfile, limit = 50)
        
        val recommendedProblems = similarUsers.flatMap { similarUser ->
            getRecentSolvedProblems(similarUser.id, days = 30)
        }.groupBy { it.id }
         .mapValues { it.value.size } // 문제별 추천 빈도
         .filter { !userProfile.solvedProblems.contains(it.key) }
         .toList()
         .sortedByDescending { it.second }
         .take(10)
        
        return recommendedProblems.map { (problemId, frequency) ->
            val problem = problemRepository.findById(problemId)
            ProblemRecommendation(
                problemId = problemId,
                recommendationType = RecommendationType.COLLABORATIVE_FILTERING,
                reason = "비슷한 실력의 ${frequency}명이 최근 해결한 문제",
                confidence = calculateConfidence(frequency, similarUsers.size)
            )
        }
    }
}
```

---

### **12. GROUP_ACHIEVEMENT_SAGA**

**목표**: 그룹 목표 달성 감지 및 보상 처리

#### **Saga 흐름도**

```mermaid
sequenceDiagram
    participant Scheduler
    participant SG as StudyGroup Module
    participant A as Analysis Module
    participant N as Notification Module
    participant K as Kafka

    Note over Scheduler,K: 🔄 GROUP_ACHIEVEMENT_SAGA (매일 실행)
    
    Scheduler->>SG: triggerAchievementCheck()
    
    rect rgb(255, 240, 240)
        Note over SG: Step 1: 달성 목표 확인
        SG->>SG: 모든 활성 그룹의 목표 조회
        SG->>SG: 각 목표별 진척도 계산
        loop 각 그룹의 목표별로
            SG->>SG: 달성 조건 확인
            alt 목표 달성
                SG->>SG: GROUP_ACHIEVEMENTS에 달성 기록
                SG->>SG: 그룹 배지/레벨 업데이트
            end
        end
        SG->>SG: Outbox에 이벤트 저장
        SG-->>K: ACHIEVEMENTS_UNLOCKED 발행
    end
    
    rect rgb(240, 255, 240)
        Note over A: Step 2: 분석 데이터 업데이트
        K->>A: ACHIEVEMENTS_UNLOCKED 수신
        A->>A: 그룹 분석 결과에 달성 기록 반영
        A->>A: 멤버들의 개인 기록에도 연동
        A->>A: 성취 기반 새로운 목표 제안 생성
        A->>A: Outbox에 이벤트 저장
        A-->>K: ACHIEVEMENT_ANALYSIS_UPDATED 발행
    end
    
    rect rgb(240, 240, 255)
        Note over N: Step 3: 축하 알림 및 보상 안내
        K->>N: ACHIEVEMENTS_UNLOCKED 수신
        loop 각 달성된 목표별로
            N->>N: 모든 그룹 멤버에게 축하 알림
            N->>N: 달성 내역 및 기여도 상세 설명
            alt 특별 보상이 있는 경우
                N->>N: 보상 안내 및 수령 방법 알림
            end
        end
        N->>N: 그룹장에게 달성 요약 리포트
        N->>N: Outbox에 이벤트 저장
        N-->>K: ACHIEVEMENT_CELEBRATIONS_SENT 발행
    end
```

#### **달성 목표 타입**

```kotlin
enum class AchievementType {
    COLLECTIVE_SOLVING,          // "한 달간 그룹 전체 100문제 해결"
    CONSISTENCY,                 // "모든 멤버가 2주 연속 활동"  
    DIFFICULTY_PROGRESSION,      // "그룹 평균 티어 1단계 상승"
    TAG_MASTERY,                // "특정 알고리즘 태그 그룹 전체 숙달"
    PARTICIPATION,              // "신규 멤버 5명 이상 영입"
    RETENTION,                  // "6개월 이상 활동 지속"
    COLLABORATION               // "멤버간 코드 리뷰 50회 이상"
}

data class GroupAchievement(
    val id: UUID,
    val groupId: UUID,
    val achievementType: AchievementType,
    val title: String,
    val description: String,
    val criteria: AchievementCriteria,
    val progress: AchievementProgress,
    val rewards: List<Reward>,
    val isUnlocked: Boolean,
    val unlockedAt: LocalDateTime?
)

data class AchievementCriteria(
    val targetValue: Int,
    val timeframeDays: Int,
    val conditions: Map<String, Any>
)

@Service
class AchievementChecker {
    
    fun checkGroupAchievements(groupId: UUID): List<GroupAchievement> {
        val group = studyGroupRepository.findById(groupId)
        val activeGoals = getActiveAchievements(groupId)
        
        return activeGoals.mapNotNull { goal ->
            val currentProgress = calculateProgress(goal, group)
            
            if (currentProgress >= goal.criteria.targetValue) {
                unlockAchievement(goal, currentProgress)
            } else {
                updateProgress(goal, currentProgress)
                null
            }
        }
    }
    
    private fun calculateProgress(goal: GroupAchievement, group: StudyGroup): Int {
        return when (goal.achievementType) {
            AchievementType.COLLECTIVE_SOLVING -> {
                val timeframe = LocalDateTime.now().minusDays(goal.criteria.timeframeDays.toLong())
                submissionRepository.countGroupSubmissionsSince(group.id, timeframe)
            }
            AchievementType.CONSISTENCY -> {
                val activeDays = goal.criteria.timeframeDays
                val activeMembers = getConsistentlyActiveMembers(group.id, activeDays)
                activeMembers.size
            }
            // ... 다른 타입들
            else -> 0
        }
    }
}
```

---

## 🎯 **구현 순서 및 테스트 전략**

### **구현 우선순위**

#### **Phase 2A: 핵심 확장 (먼저 구현)**
1. ✅ `LEAVE_GROUP_SAGA` - 복잡하지만 필수적인 정리 작업
2. ✅ `GROUP_RULE_UPDATE_SAGA` - 그룹 관리의 핵심  
3. ✅ `PROBLEM_ASSIGNMENT_SAGA` - 자동화의 시작점

#### **Phase 2B: 인텔리전트 기능 (나중 구현)**
4. ✅ `RULE_VIOLATION_SAGA` - 자동 모니터링 
5. ✅ `RECOMMENDATION_GENERATION_SAGA` - AI/ML 기반 추천
6. ✅ `GROUP_ACHIEVEMENT_SAGA` - 게임화 요소

### **테스트 시나리오**

```kotlin
@SpringBootTest
class Phase2SagaIntegrationTest {
    
    @Test
    fun `그룹장 탈퇴 시 소유권 이전 Saga 통합 테스트`() {
        // Given: 그룹장과 일반 멤버가 있는 그룹
        val group = createTestGroup(ownerCount = 1, memberCount = 3)
        val owner = group.owner
        
        // When: 그룹장이 탈퇴 요청
        val sagaResult = leaveGroupSaga.start(
            LeaveGroupRequest(groupId = group.id, userId = owner.id)
        )
        
        // Then: 소유권이 이전되고 모든 데이터가 정리됨
        assertThat(sagaResult.status).isEqualTo(SagaStatus.COMPLETED)
        
        val updatedGroup = studyGroupRepository.findById(group.id)!!
        assertThat(updatedGroup.ownerId).isNotEqualTo(owner.id)
        assertThat(updatedGroup.members).doesNotContain(owner.id)
        
        // 분석 데이터에서도 정리 확인
        val analysisData = analysisService.getGroupAnalysis(group.id)
        assertThat(analysisData.memberProfiles).doesNotContainKey(owner.id)
    }
    
    @Test
    fun `규칙 위반 감지 및 자동 처리 Saga 테스트`() {
        // Given: 주 3문제 규칙이 있는 그룹과 위반자
        val group = createGroupWithRule(minimumProblemsPerWeek = 3)
        val violator = group.members.first()
        
        // 이번 주에 1문제만 해결 (규칙 위반)
        createSubmissions(violator.id, count = 1, withinDays = 7)
        
        // When: 위반 감지 Saga 실행
        val sagaResult = ruleViolationSaga.start()
        
        // Then: 위반이 감지되고 알림이 발송됨
        assertThat(sagaResult.detectedViolations).hasSize(1)
        assertThat(sagaResult.detectedViolations[0].userId).isEqualTo(violator.id)
        assertThat(sagaResult.detectedViolations[0].severity).isEqualTo(ViolationSeverity.WARNING)
        
        // 알림 발송 확인
        val notifications = notificationRepository.findByUserId(violator.id)
        assertThat(notifications).anyMatch { it.type == NotificationType.RULE_VIOLATION }
    }
}
```

---

## 📊 **모니터링 및 운영**

### **Phase 2 Saga 전용 메트릭**

```yaml
# 그룹 관리 Saga 관련
saga.leave_group.completion_rate           # 탈퇴 Saga 성공률
saga.leave_group.ownership_transfer_rate   # 소유권 이전 성공률
saga.rule_update.propagation_time          # 규칙 변경 전파 시간

# 자동화 Saga 관련
saga.problem_assignment.daily_count        # 일일 자동 할당 수
saga.violation_detection.accuracy          # 위반 감지 정확도
saga.recommendation.hit_rate               # 추천 적중률 (사용자가 실제로 푼 비율)

# 성취 Saga 관련
saga.achievement.unlock_frequency          # 목표 달성 빈도
saga.group_achievement.member_satisfaction # 달성 후 멤버 만족도
```

### **알림 및 대시보드**

```kotlin
@Component
class Phase2SagaMonitor {
    
    @EventListener
    fun handleComplexSagaFailure(event: SagaFailedEvent) {
        when (event.sagaType) {
            "LEAVE_GROUP_SAGA" -> {
                if (event.failedStep == "OWNERSHIP_TRANSFER") {
                    // 그룹 해체 위험 알림
                    alertingService.sendCriticalAlert(
                        "그룹 소유권 이전 실패로 그룹 해체 위험",
                        event.sagaId
                    )
                }
            }
            "RULE_VIOLATION_SAGA" -> {
                // 위반 감지 시스템 오류 알림
                alertingService.sendOperationalAlert(
                    "규칙 위반 감지 시스템 오류",
                    event
                )
            }
        }
    }
}
```

---

📝 **문서 버전**: v1.0  
📅 **최종 수정일**: 2025-07-22  
👤 **작성자**: 채기훈