# API 명세서 - 알고리포트 (Algo-Report)

이 문서는 **알고리포트** 플랫폼의 REST API 명세와 모듈 간 Kafka 이벤트 통신을 정의합니다.

## 📋 **API 개요**

- **Base URL**: `/api/v1`
- **Authentication**: JWT Bearer Token (Google OAuth2 기반)
- **Content-Type**: `application/json`
- **Response Format**: JSON

---

## 🔐 **인증 (Authentication)**

### **Google OAuth2 로그인**

#### `GET /oauth2/authorization/google`
Google 로그인 페이지로 리다이렉트합니다.

**Response**: HTTP 302 Redirect to Google OAuth2

---

## 👤 **사용자 모듈 (User Module)**

### **solved.ac 계정 연동**

#### `POST /api/v1/users/me/link-solvedac`
로그인한 사용자의 solved.ac 핸들을 연동합니다.

**Headers**:
```
Authorization: Bearer {jwt_token}
```

**Request Body**:
```json
{
  "solvedacHandle": "example_handle"
}
```

**Response (200 OK)**:
```json
{
  "userId": 1,
  "email": "user@example.com",
  "nickname": "알고마스터",
  "solvedacHandle": "example_handle",
  "linkedAt": "2025-07-22T10:30:00Z"
}
```

**Error Responses**:
- `404 NOT_FOUND`: solved.ac에서 해당 핸들을 찾을 수 없음
- `409 CONFLICT`: 이미 다른 사용자가 연동한 핸들

---

## 👥 **스터디 그룹 모듈 (Study Group Module)**

### **스터디 그룹 생성**

#### `POST /api/v1/studygroups`

**Headers**:
```
Authorization: Bearer {jwt_token}
```

**Request Body**:
```json
{
  "name": "코테 정복반",
  "description": "매일 1문제씩 풀어요",
  "maxMembers": 10,
  "isPublic": true
}
```

**Response (201 Created)**:
```json
{
  "studyGroupId": 1,
  "name": "코테 정복반",
  "description": "매일 1문제씩 풀어요",
  "maxMembers": 10,
  "isPublic": true,
  "owner": {
    "userId": 1,
    "nickname": "그룹장"
  },
  "memberCount": 1,
  "createdAt": "2025-07-22T10:30:00Z"
}
```

### **스터디 그룹 상세 조회**

#### `GET /api/v1/studygroups/{id}`

**Response (200 OK)**:
```json
{
  "studyGroupId": 1,
  "name": "코테 정복반",
  "description": "매일 1문제씩 풀어요",
  "maxMembers": 10,
  "isPublic": true,
  "owner": {
    "userId": 1,
    "nickname": "그룹장",
    "solvedacHandle": "group_leader"
  },
  "members": [
    {
      "userId": 1,
      "nickname": "그룹장",
      "solvedacHandle": "group_leader",
      "joinedAt": "2025-07-22T10:30:00Z"
    }
  ],
  "rules": [
    {
      "ruleId": 1,
      "ruleType": "WEEKLY_PROBLEM_COUNT",
      "threshold": 3,
      "description": "주 3문제 이상 풀기"
    }
  ],
  "createdAt": "2025-07-22T10:30:00Z"
}
```

### **스터디 그룹 참여**

#### `POST /api/v1/studygroups/{id}/join`

**Headers**:
```
Authorization: Bearer {jwt_token}
```

**Response (200 OK)**:
```json
{
  "message": "스터디 그룹에 성공적으로 참여했습니다.",
  "joinedAt": "2025-07-22T11:00:00Z"
}
```

**Error Responses**:
- `404 NOT_FOUND`: 스터디 그룹을 찾을 수 없음
- `409 CONFLICT`: 이미 참여한 스터디 그룹
- `400 BAD_REQUEST`: 최대 인원 초과

### **스터디 그룹 규칙 설정** (그룹장만)

#### `POST /api/v1/studygroups/{id}/rules`

**Headers**:
```
Authorization: Bearer {jwt_token}
```

**Request Body**:
```json
{
  "rules": [
    {
      "ruleType": "WEEKLY_PROBLEM_COUNT",
      "threshold": 3,
      "description": "주 3문제 이상 풀기"
    },
    {
      "ruleType": "CONSECUTIVE_INACTIVE_DAYS",
      "threshold": 7,
      "description": "7일 연속 비활성 시 경고"
    }
  ]
}
```

**Response (200 OK)**:
```json
{
  "message": "스터디 그룹 규칙이 설정되었습니다.",
  "rules": [
    {
      "ruleId": 1,
      "ruleType": "WEEKLY_PROBLEM_COUNT",
      "threshold": 3,
      "description": "주 3문제 이상 풀기",
      "createdAt": "2025-07-22T11:30:00Z"
    }
  ]
}
```

### **자동 문제 할당** (Phase 2.5 - 향후 구현)

#### `POST /api/v1/studygroups/{id}/auto-assign`

**Headers**:
```
Authorization: Bearer {jwt_token}
```

**Request Body**:
```json
{
  "weeklyTargetCount": 5,
  "targetTags": ["dp", "graph", "greedy"],
  "difficultyRange": {
    "min": "silver3",
    "max": "gold2"
  }
}
```

**Response (200 OK)**:
```json
{
  "message": "자동 문제 할당이 설정되었습니다.",
  "nextAssignmentDate": "2025-07-28T09:00:00Z"
}
```

---

## 📊 **분석 모듈 (Analysis Module)**

### **개인 학습 대시보드**

#### `GET /api/v1/analysis/users/{handle}`

**Query Parameters**:
- `period`: `week` | `month` | `year` (default: `month`)

**Response (200 OK)**:
```json
{
  "handle": "example_handle",
  "tier": "gold3",
  "solvedCount": 458,
  "period": "month",
  "submissionHeatmap": [
    {
      "date": "2025-07-01",
      "solvedCount": 3,
      "intensity": "high"
    }
  ],
  "tagProficiency": [
    {
      "tag": "dp",
      "solvedCount": 45,
      "accuracy": 78.5,
      "averageTries": 2.3,
      "proficiencyLevel": "intermediate"
    }
  ],
  "weeklyStats": {
    "totalSolved": 12,
    "averageDifficulty": "silver1",
    "streakDays": 5
  }
}
```

### **스터디 그룹 대시보드**

#### `GET /api/v1/analysis/studygroups/{id}`

**Response (200 OK)**:
```json
{
  "studyGroupId": 1,
  "name": "코테 정복반",
  "period": "week",
  "totalMembers": 5,
  "activeMembers": 4,
  "groupStats": {
    "totalSolved": 67,
    "averagePerMember": 13.4,
    "groupStrengths": ["dp", "implementation"],
    "groupWeaknesses": ["graph", "string"]
  },
  "memberActivities": [
    {
      "userId": 1,
      "nickname": "그룹장",
      "solvedThisWeek": 8,
      "ruleViolations": 0,
      "lastActive": "2025-07-22T09:30:00Z"
    }
  ],
  "weeklyProgress": [
    {
      "week": "2025-W29",
      "totalSolved": 67,
      "participationRate": 80.0
    }
  ]
}
```

### **맞춤 문제 추천**

#### `GET /api/v1/recommendations/users/{handle}`

**Query Parameters**:
- `count`: 추천할 문제 수 (default: 10)
- `difficulty`: `bronze` | `silver` | `gold` | `platinum` (optional)

**Response (200 OK)**:
```json
{
  "handle": "example_handle",
  "recommendations": [
    {
      "problemId": 1463,
      "title": "1로 만들기 2",
      "tier": "silver1",
      "tags": ["dp"],
      "reason": "DP 태그 숙련도 향상을 위해 추천",
      "solvedCount": 12543,
      "averageTries": 3.2
    }
  ],
  "targetWeaknesses": ["dp", "graph"],
  "generatedAt": "2025-07-22T12:00:00Z"
}
```

---

## 🔔 **알림 모듈 (Notification Module)**

### **알림 설정 조회**

#### `GET /api/v1/notifications/me/settings`

**Headers**:
```
Authorization: Bearer {jwt_token}
```

**Response (200 OK)**:
```json
{
  "userId": 1,
  "emailNotifications": true,
  "pushNotifications": true,
  "studyGroupAlerts": true,
  "recommendationAlerts": false
}
```

---

## 🔄 **Kafka 이벤트 통신**

### **토픽별 이벤트 정의**

#### **`new-submission` 토픽**
- **발행자**: 데이터 수집기 (Collector)
- **구독자**: 분석 서비스 (Analysis Service)

**이벤트 예시**:
```json
{
  "eventType": "NEW_SUBMISSION",
  "solvedacHandle": "example_handle",
  "problemId": 1000,
  "submissionId": 12345678,
  "result": "AC",
  "submittedAt": "2025-07-22T14:30:00Z",
  "language": "Python3",
  "codeLength": 245
}
```

#### **`study-group-alert` 토픽**
- **발행자**: 스터디 그룹 모니터링 서비스
- **구독자**: 알림 서비스

**이벤트 예시**:
```json
{
  "eventType": "RULE_VIOLATION_DETECTED",
  "studyGroupId": 1,
  "userId": 5,
  "ruleType": "WEEKLY_PROBLEM_COUNT",
  "violation": {
    "expected": 3,
    "actual": 1,
    "period": "2025-W29"
  },
  "detectedAt": "2025-07-22T15:00:00Z"
}
```

#### **`problem-assigned` 토픽** (Phase 2.5)
- **발행자**: 자동 문제 할당 서비스
- **구독자**: 알림 서비스, 분석 서비스

**이벤트 예시**:
```json
{
  "eventType": "PROBLEMS_ASSIGNED",
  "studyGroupId": 1,
  "userId": 3,
  "assignedProblems": [
    {
      "problemId": 1463,
      "title": "1로 만들기 2",
      "tier": "silver1",
      "dueDate": "2025-07-29T23:59:59Z"
    }
  ],
  "assignedAt": "2025-07-22T09:00:00Z"
}
```

---

## ❌ **공통 에러 응답**

### **에러 응답 형식**
```json
{
  "error": {
    "code": "E40401",
    "message": "해당 사용자를 찾을 수 없습니다.",
    "timestamp": "2025-07-22T16:00:00Z"
  }
}
```

### **주요 에러 코드**
- `E40401`: USER_NOT_FOUND
- `E40402`: STUDY_GROUP_NOT_FOUND  
- `E40403`: SOLVEDAC_USER_NOT_FOUND
- `E40901`: ALREADY_JOINED_STUDY
- `E40902`: MAX_MEMBERS_EXCEEDED
- `E40301`: INSUFFICIENT_PERMISSION

---

📝 **문서 버전**: v1.0  
📅 **최종 수정일**: 2025-07-22  
👤 **작성자**: 채기훈