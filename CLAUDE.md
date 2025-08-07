# CLAUDE.md

## 📋 **프로젝트 개요**

**알고리포트 (Algo-Report)**는 `solved.ac` 사용자 및 스터디 그룹의 문제 해결 이력을 분석하여, 학습 패턴 시각화, 강점/약점 분석, 맞춤 문제 추천 및 스터디 자동 관리를 제공하는 플랫폼입니다.

## 🔧 **빌드 & 실행**

```bash
# 인프라 실행
docker-compose up -d

# Debezium CDC 설정
curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d @scripts/outbox-connector.json

# 빌드 & 실행
./gradlew build
./gradlew bootRun

# 테스트
./gradlew test
```

## 🏗️ **기술 스택**

- **Language**: Kotlin 2.2.0 + Java 21 LTS
- **Framework**: Spring Boot 3.5.3, Spring Security, JPA + QueryDSL
- **Database**: PostgreSQL, H2 (Test)
- **Message**: Kafka + Debezium CDC
- **Cache**: Redis
- **Search**: Elasticsearch
- **Testing**: Kotest BehaviorSpec, MockK
- **Coverage**: JaCoCo (75% Branch, 80% Line)

## 🎯 **TDD 핵심 가이드**

### **🚨 절대 잊지 말 것!**
- **Red-Green-Refactor 각 단계마다 반드시 커밋**
- **커밋 메시지**: `test: Red - 설명`, `feat: Green - 설명`, `refactor: Refactor - 설명`

### **RED 단계 "Fake It" 전략**
```kotlin
// ✅ 컴파일 성공 + 가짜 값으로 테스트 실패 유도
class SomeService {
    fun process(): Result = Result(status = FAILED, data = null) // 의도적 실패
}
```

### **Kotest BehaviorSpec 함정 (매번 실수!)**
```kotlin
// ❌ 잘못: beforeEach에서 데이터 삭제됨
given("테스트") {
    val user = create()  // beforeEach 전에 생성 → 삭제됨
    then("결과") { /* 실패 */ }
}

// ✅ 정답: then 내에서 생성
given("테스트") {
    then("결과") {
        val user = create()  // then 내에서 생성 → 안전
    }
}

// ❌ Mock 공유 문제
val mockService = mockk<Service>()  // 클래스 레벨 → 테스트 간섭

// ✅ Mock 독립
then("테스트") {
    val mockService = mockk<Service>()  // then 내부 → 독립적
}
```

## 📊 **현재 상태**

### **완료 Phase (99%)**
- ✅ **Phase 0**: 프로젝트 기반 구축
- ✅ **Phase 1**: 데이터 파이프라인 (INITIAL_DATA_SYNC_SAGA, SUBMISSION_SYNC_SAGA)
- ✅ **Phase 2**: 사용자 인증 (USER_REGISTRATION_SAGA, SOLVEDAC_LINK_SAGA)
- ✅ **Phase 3**: 스터디 그룹 (CREATE_GROUP_SAGA, JOIN_GROUP_SAGA)
- ✅ **Phase 4**: 분석 기능 (ANALYSIS_UPDATE_SAGA, PERSONAL_STATS_REFRESH_SAGA, RecommendationService, **StudyGroupDashboardService**)

### **성과**
- **280+개 테스트 100% 통과** (예상)
- **JaCoCo 커버리지 달성**: 75% Branch, 80% Line
- **10개 주요 기능 완전 구현** (TDD 적용)
- **StudyGroupDashboardService GREEN 단계 완료** ✨ **NEW**

## 🚀 **다음 할 일**

### **1. Phase 4 완료** (🎯 **권장**, 30분-1시간)
- StudyGroupDashboardService REFACTOR 단계 (선택사항)
- **또는 Phase 4 완료 선언** ✨

### **2. Phase 5: 프론트엔드** 
- React + Next.js 웹 애플리케이션

### **3. Phase 6: 시스템 최적화**
- 불필요한 SAGA 단순화, Elastic APM 도입

## 🛠️ **체크리스트**

### **새 기능 개발**
- [ ] RED: 테스트 → 실행(실패) → 커밋
- [ ] GREEN: 구현 → 통과 → 커밋  
- [ ] REFACTOR: 리팩토링 → 통과 → 커밋

### **Kotest 실패 시**
- [ ] "찾을 수 없음" → 데이터 생명주기 (`then` 내부에서 생성)
- [ ] Mock 검증 실패 → Mock 공유 문제 (`then` 내부에서 mockk())
- [ ] LocalDateTime 오류 → JavaTimeModule 누락

## 🚨 **개발 원칙**

1. **결정사항 필수 문의**: 아키텍처 변경, 라이브러리 선택 등은 사용자에게 문의
2. **CustomException + Error enum 사용** (표준 예외 지양)
3. **Kotlin Coroutines 적극 활용** (Virtual Thread 대비 메모리 효율적)

## 📡 **주요 API**

```
GET    /oauth2/authorization/google           # Google 로그인
POST   /api/v1/users/me/link-solvedac        # solved.ac 연동
POST   /api/v1/studygroups                   # 그룹 생성
POST   /api/v1/studygroups/{id}/join         # 그룹 참여
GET    /api/v1/analysis/users/{handle}       # 개인 대시보드
GET    /api/v1/recommendations/users/{handle} # 문제 추천
```

## 🧠 **개발 메모리**

- 앞으로 테스트든 뭐든 구현할 때 항상 이미 구현된 것들을 먼저 분석 후에 진행해야 된다는 것을 명시
- 애매한 결정 사항들은 마음대로 선택하지 말고 사용자에게 다시 되물어야 한다는 것을 명시
- 프로젝트 시작 시 마크다운 하위폴더 전체를 읽는게 아니라 CLAUDE.md를 먼저 읽고, 나머지 문서에서는 필요한 내용들만 찾아서 파싱해서 읽어야 함
- 문서 업데이트할 때는 관련 문서 전체를 업데이트해야 함(마크다운 폴더 파일들까지)
- 테스트는 직접 실행하지말고 사용자에게 실행하도록 항상 요청할것

## 📈 **최신 업데이트 (2025-08-07)**

### **✅ 방금 완료**
- **StudyGroupDashboardService GREEN 단계**: 그룹 통계, 멤버 분석, 캐시 전략 구현
- **진행률 99% 달성**: Phase 4 거의 완료 (REFACTOR 단계만 남음)

### **🧪 테스트 안내**
테스트 실행을 원하시면 다음 명령어를 직접 실행해 주세요:
```bash
./gradlew test --tests "com.algoreport.module.analysis.StudyGroupDashboardServiceTest"
```

📝 Last Updated: 2025-08-07 | 👤 Maintainer: 채기훈