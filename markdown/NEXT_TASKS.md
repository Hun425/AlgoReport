# 다음 할 일 및 우선순위 (SAGA 패턴 기반 TDD 마스터 플랜)

## 📊 작업 현황 개요

- **프로젝트명**: 알고리포트 (Algo-Report)
- **핵심 컨셉**: `solved.ac` 사용자/그룹의 문제 해결 이력을 분석하여 학습 패턴 시각화, 강점/약점 분석, 맞춤 문제 추천 및 스터디 자동 관리를 제공하는 플랫폼
- **아키텍처**: 모듈형 모놀리스 + SAGA 패턴 + TDD
- **총 등록 작업**: 45개 (마이페이지 SAGA 추가)
- **완료된 작업**: 11개 (Phase 0, 1, 2, 3, 4 완료)
- **완료 대기 작업**: 34개
- **진행중 작업**: 0개 (모든 핵심 SAGA 완료)
- **마지막 업데이트**: 2025-08-04

---

## 🎯 **SAGA 우선순위 및 복잡도 매트릭스**

| 순서 | SAGA 이름 | 복잡도 | 우선순위 | TDD 적용 난이도 | 예상 소요일 | 상태 |
|-----|----------|-------|--------|-------------|-----------|------|
| 1 | `INITIAL_DATA_SYNC_SAGA` | Very High | 🔥 Critical | High | 3-4일 | ✅ **완료** |
| 2 | `SUBMISSION_SYNC_SAGA` | Medium | 🔥 Critical | Medium | 1-2일 | ✅ **완료** |
| 3 | `USER_REGISTRATION_SAGA` | Medium | 🔥 Critical | Low | 1일 | ✅ **완료** |
| 4 | `SOLVEDAC_LINK_SAGA` | High | 🔥 Critical | Medium | 2일 | ✅ **완료** |
| 5 | `CREATE_GROUP_SAGA` | Medium | 🔥 Critical | Medium | 1-2일 | ✅ **완료** |
| 6 | `JOIN_GROUP_SAGA` | High | 🔥 Critical | High | 2-3일 | ✅ **완료** |
| 7 | `USER_PROFILE_UPDATE_SAGA` | Medium | 🔥 Critical | Medium | 1일 | ⏳ 대기 |
| 8 | `ANALYSIS_UPDATE_SAGA` | Medium | 🟡 Important | Medium | 2일 | ✅ **완료** |
| 9 | `PERSONAL_STATS_REFRESH_SAGA` | Medium | 🟡 Important | Medium | 1-2일 | 🚀 **진행중** |
    

---

## Phase 0: 프로젝트 기반 구축 🔥 **Critical**

**목표**: 모든 개발을 시작하기 위한 기반을 완벽하게 구축합니다.

### **📋 Phase 0 세부 작업 계획**

#### **0.1 프로젝트 초기 설정** 🔥 **최우선**

##### **Task 0-1-1: Kotlin+Spring Boot 프로젝트 생성**
- **담당자**: 채기훈
- **예상 소요시간**: 1시간
- **현재 상태**: **대기 ⏳ (가장 먼저 시작할 작업)**
- **TDD 적용**: X (프로젝트 설정)
- **완료 기준**:
  - [ ] Spring Initializr로 프로젝트 생성 (`Kotlin`, `Gradle`, `Spring Boot 3.2.x`, `Java 17`)
  - [ ] `build.gradle.kts`에 필수 의존성 추가
    - `web`, `jpa`, `kafka`, `redis`, `spring-security`
    - `org.springframework.boot:spring-boot-starter-oauth2-client`
    - `org.springframework.boot:spring-boot-starter-data-elasticsearch`
  - [ ] `./gradlew build` 명령어로 초기 빌드 성공 확인
  - [ ] `.gitignore` 파일에 `.idea/`, `build/` 등 불필요한 파일/디렉토리 추가

##### **Task 0-1-2: 기본 의존성 설정**
- **예상 소요시간**: 30분
- **TDD 적용**: X
- **완료 기준**:
  - [ ] MockK, Kotest 테스트 의존성 추가
  - [ ] Spring Boot Test 설정 확인

##### **Task 0-1-3: 모듈 구조 생성**
- **예상 소요시간**: 1시간
- **TDD 적용**: X
- **완료 기준**:
  - [ ] `com.algoreport.module.user` 패키지 생성
  - [ ] `com.algoreport.module.studygroup` 패키지 생성
  - [ ] `com.algoreport.module.analysis` 패키지 생성
  - [ ] `com.algoreport.module.notification` 패키지 생성
  - [ ] `com.algoreport.config` 패키지 생성

##### **Task 0-1-4: 개발/테스트 프로필 설정**
- **예상 소요시간**: 30분
- **TDD 적용**: X
- **완료 기준**:
  - [ ] `application-dev.yml` 생성
  - [ ] `application-test.yml` 생성
  - [ ] H2 테스트 데이터베이스 설정

#### **0.2 Docker 인프라 구성**

##### **Task 0-2-1: docker-compose.yml 작성**
- **예상 소요시간**: 1시간
- **TDD 적용**: X
- **완료 기준**:
  - [ ] PostgreSQL 컨테이너 설정
  - [ ] Redis 컨테이너 설정
  - [ ] Kafka + Zookeeper 컨테이너 설정
  - [ ] Elasticsearch + Kibana 컨테이너 설정

##### **Task 0-2-2: 데이터베이스 초기 스키마 설정**
- **예상 소요시간**: 30분
- **TDD 적용**: X
- **완료 기준**:
  - [ ] PostgreSQL 초기 데이터베이스 생성
  - [ ] JPA DDL 설정 확인

##### **Task 0-2-3: Kafka 토픽 초기 설정**
- **예상 소요시간**: 30분
- **TDD 적용**: X
- **완료 기준**:
  - [ ] `new-submission` 토픽 생성
  - [ ] `study-group-alert` 토픽 생성
  - [ ] Kafka 연결 테스트

#### **0.3 공통 인프라 구현** (TDD 적용)

##### **Task 0-3-1: 전역 예외 처리 구현**
- **예상 소요시간**: 2시간
- **TDD 적용**: ✅ Red-Green-Refactor
- **완료 기준**:
  - [ ] **[RED]** CustomException 클래스 테스트 작성
  - [ ] **[GREEN]** CustomException + Error enum 구현
  - [ ] **[REFACTOR]** GlobalExceptionHandler 구현
  - [ ] 각 단계마다 즉시 커밋

##### **Task 0-3-2: OAuth2 + JWT 보안 설정 기본 구조**
- **예상 소요시간**: 2시간
- **TDD 적용**: ✅ Red-Green-Refactor
- **완료 기준**:
  - [ ] **[RED]** SecurityConfig 테스트 작성
  - [ ] **[GREEN]** 기본 OAuth2 설정 구현
  - [ ] **[REFACTOR]** JWT 토큰 처리 로직 개선
  - [ ] 각 단계마다 즉시 커밋

##### **Task 0-3-3: Outbox Pattern 기본 구현**
- **예상 소요시간**: 3시간
- **TDD 적용**: ✅ Red-Green-Refactor
- **완료 기준**:
  - [ ] **[RED]** OutboxEvent 엔티티 테스트 작성
  - [ ] **[GREEN]** 기본 Outbox 구조 구현
  - [ ] **[REFACTOR]** 이벤트 발행 인프라 완성
  - [ ] 각 단계마다 즉시 커밋

**Phase 0 예상 총 소요시간**: 2-3일
        

---

## Phase 1: 핵심 데이터 파이프라인 구축 🔥 **Critical**

**목표**: SAGA 패턴 기반으로 solved.ac 데이터 수집부터 분석까지 핵심 파이프라인을 완성합니다.

### **📋 Phase 1 SAGA 기반 세부 작업 계획**

#### **1.1 INITIAL_DATA_SYNC_SAGA 구현** 🔥 **최우선** (가장 복잡한 SAGA)

##### **Task 1-1-1: [RED] solved.ac API 클라이언트 테스트 작성**
- **예상 소요시간**: 2시간
- **TDD 적용**: ✅ Red 단계
- **완료 기준**:
  - [ ] **[RED]** SolvedacApiClient 인터페이스 테스트 작성
  - [ ] **[RED]** 사용자 정보 조회 API 테스트 작성  
  - [ ] **[RED]** 제출 이력 조회 API 테스트 작성
  - [ ] 테스트 실패 확인 후 즉시 커밋: `test: Red - solved.ac API 클라이언트 기본 구조`

##### **Task 1-1-2: [GREEN] solved.ac API 클라이언트 기본 구현**
- **예상 소요시간**: 2시간
- **TDD 적용**: ✅ Green 단계
- **완료 기준**:
  - [ ] **[GREEN]** SolvedacApiClient 기본 구현 (최소한의 코드)
  - [ ] **[GREEN]** RestTemplate 기반 API 호출 로직
  - [ ] 모든 테스트 통과 확인 후 즉시 커밋: `feat: Green - solved.ac API 클라이언트 기본 구현`

##### **Task 1-1-3: [REFACTOR] API 클라이언트 구조 개선**
- **예상 소요시간**: 1시간
- **TDD 적용**: ✅ Refactor 단계
- **완료 기준**:
  - [ ] **[REFACTOR]** 코드 구조 개선 (가독성, 확장성)
  - [ ] **[REFACTOR]** 에러 처리 로직 추가
  - [ ] 테스트 통과 유지하며 즉시 커밋: `refactor: Refactor - API 클라이언트 구조 개선`

##### **Task 1-1-4~9: INITIAL_DATA_SYNC_SAGA 완전 구현** ✅ **완료** (2025-07-27)
- **소요시간**: 1일 (기존 예상 3-4일에서 단축)
- **TDD 적용**: ✅ Red-Green-Refactor 사이클 완료 (9개 Task 모두)
- **완료 내용**:
  - **대용량 배치 수집**: DataSyncBatchService, Kotlin Coroutines 병렬 처리
  - **레이트 리밋 처리**: RateLimitHandler, 지수 백오프 재시도 로직
  - **SAGA 오케스트레이터**: InitialDataSyncSaga, 보상 트랜잭션, 체크포인트 재시작
  - **성능 최적화**: SagaPerformanceOptimizer, 성능 분석 및 배치 크기 추천

#### **1.2 SUBMISSION_SYNC_SAGA 구현** 🚀 **다음 작업**

##### **Task 1-2-1: [RED] 실시간 제출 동기화 테스트 작성**
- **예상 소요시간**: 2시간
- **TDD 적용**: ✅ Red 단계
- **완료 기준**:
  - [ ] **[RED]** 실시간 제출 동기화 SAGA 테스트 작성
  - [ ] **[RED]** 5분마다 스케줄링 로직 테스트
  - [ ] **[RED]** 증분 업데이트 로직 테스트
  - [ ] 테스트 실패 확인 후 즉시 커밋: `test: Red - 실시간 제출 동기화 기본 구조`

##### **Task 1-2-2: [GREEN] 5분마다 스케줄링 구현**
- **예상 소요시간**: 1시간
- **TDD 적용**: ✅ Green 단계
- **완료 기준**:
  - [ ] **[GREEN]** @Scheduled 기반 구현
  - [ ] **[GREEN]** 마지막 동기화 시점 추적
  - [ ] **[GREEN]** 증분 데이터 수집 로직
  - [ ] 모든 테스트 통과 확인 후 즉시 커밋: `feat: Green - 5분마다 스케줄링 구현`

##### **Task 1-2-3: [REFACTOR] 성능 최적화** ✅ **완료** (2025-07-28)
- **예상 소요시간**: 1시간 (실제 소요: 1시간)
- **TDD 적용**: ✅ Refactor 단계 ✅ **완료**
- **완료 기준**:
  - [x] **[REFACTOR]** 배치 처리 성능 개선 ✅ **완료** (Kotlin Coroutines 병렬 처리)
  - [x] **[REFACTOR]** 메모리 사용량 최적화 ✅ **완료** (배치 중복 체크, 결과 집계 분리)
  - [x] **[REFACTOR]** 중복 데이터 방지 로직 ✅ **완료** (Set 기반 중복 체크)
  - [x] 테스트 통과 유지하며 즉시 커밋: `refactor: Refactor - SUBMISSION_SYNC_SAGA 성능 최적화` ✅ **완료**

#### **1.2 SUBMISSION_SYNC_SAGA 구현**

##### **Task 1-2-1: [RED] 실시간 제출 동기화 테스트**
- **예상 소요시간**: 2시간
- **TDD 적용**: ✅ Red-Green-Refactor
- **완료 기준**:
  - [ ] **[RED]** 실시간 제출 동기화 SAGA 테스트 작성
  - [ ] **[GREEN]** 기본 동기화 로직 구현
  - [ ] **[REFACTOR]** 코드 품질 개선
  - [ ] 각 단계마다 즉시 커밋

##### **Task 1-2-2: [GREEN] 5분마다 스케줄링 구현**
- **예상 소요시간**: 1시간
- **TDD 적용**: ✅ Red-Green-Refactor
- **완료 기준**:
  - [ ] **[RED]** 스케줄링 로직 테스트
  - [ ] **[GREEN]** @Scheduled 기반 구현
  - [ ] **[REFACTOR]** 스케줄러 최적화
  - [ ] 각 단계마다 즉시 커밋

##### **Task 1-2-3: [REFACTOR] 성능 최적화**
- **예상 소요시간**: 1시간
- **TDD 적용**: ✅ Refactor
- **완료 기준**:
  - [ ] 배치 처리 성능 개선
  - [ ] 메모리 사용량 최적화
  - [ ] 즉시 커밋: `refactor: Refactor - SUBMISSION_SYNC_SAGA 성능 최적화`

#### **1.3 데이터 저장소 설정**

##### **Task 1-3-1: JPA Entity 설계 및 구현**
- **예상 소요시간**: 3시간
- **TDD 적용**: ✅ Red-Green-Refactor
- **완료 기준**:
  - [ ] **[RED]** User, Submission, Problem 엔티티 테스트
  - [ ] **[GREEN]** JPA 엔티티 기본 구현
  - [ ] **[REFACTOR]** 연관관계 최적화
  - [ ] 각 단계마다 즉시 커밋

##### **Task 1-3-2: Elasticsearch 인덱스 설정**
- **예상 소요시간**: 2시간
- **TDD 적용**: X (인프라 설정)
- **완료 기준**:
  - [ ] submissions-{YYYY.MM} 인덱스 설정
  - [ ] problem-metadata 인덱스 설정
  - [ ] 인덱스 매핑 및 설정 최적화

##### **Task 1-3-3: Redis 캐시 구조 설정**
- **예상 소요시간**: 1시간
- **TDD 적용**: X (인프라 설정)
- **완료 기준**:
  - [ ] 캐시 키 구조 설계
  - [ ] TTL 설정 및 캐시 정책 수립
  - [ ] Redis 연결 테스트

**Phase 1 예상 총 소요시간**: 5-7일
        

### **Phase 2: 사용자 및 스터디 그룹 관리 구현 (Medium Priority)**

**목표**: 사용자가 가입하고, `solved.ac` 계정을 연동하며, 스터디 그룹을 만들어 활동할 수 있는 기반을 마련합니다.

#### **Task 2-1: Google OAuth2 로그인 구현**

- **담당자**: 채기훈
    
- **예상 소요시간**: 4시간
    
- **현재 상태**: 대기 ⏳
    
- **의존성**: Task 0-1 완료
    
- **완료 기준**:
    
    - [ ] `SecurityConfig` 및 `CustomOAuth2UserService` 등 OAuth2 관련 설정 완료
        
    - [ ] Google 로그인 성공 시, 우리 서비스의 `USERS` 테이블에 사용자 정보가 저장되고 JWT 토큰이 발급되는 흐름 완성
        

#### **Task 2-2: `solved.ac` 계정 연동 API 구현**

- **담당자**: 채기훈
    
- **예상 소요시간**: 2시간
    
- **현재 상태**: 대기 ⏳
    
- **의존성**: Task 2-1 완료
    
- **완료 기준**:
    
    - [ ] 로그인한 사용자가 자신의 `solved.ac` 핸들을 입력하여 계정을 연동하는 API 구현
        
    - [ ] `solved.ac` API를 호출하여 핸들의 유효성을 검증하는 로직 포함
        

#### **Task 2-3: 스터디 그룹 관리 API TDD 구현**

- **담당자**: 채기훈
    
- **예상 소요시간**: 5시간
    
- **현재 상태**: 대기 ⏳
    
- **의존성**: Task 2-2 완료
    
- **TDD 사이클 계획**:
    
    - [ ] **스터디 그룹 생성/조회 API**: Red -> Green -> Refactor
        
    - [ ] **스터디 그룹 참여/탈퇴 API**: Red -> Green -> Refactor
        

#### **Task 2-4: 스터디 그룹 자동 관리 규칙 API 구현 (신규)**

- **담당자**: 채기훈
    
- **예상 소요시간**: 3시간
    
- **현재 상태**: 대기 ⏳
    
- **의존성**: Task 2-3 완료
    
- **완료 기준**:
    
    - [ ] 그룹장이 스터디의 자동 관리 규칙(예: '주 3문제 이상 풀지 않으면 알림', '2주간 활동 없으면 경고')을 설정하는 API 구현
        
    - [ ] 설정된 규칙은 `STUDY_GROUP_RULES` 테이블에 저장
        

### **Phase 3: 핵심 분석 및 추천/알림 기능 구현 (Medium Priority)**

**목표**: 수집된 데이터를 바탕으로 사용자에게 유의미한 분석 리포트, 추천, 자동화된 알림을 제공합니다.

#### **Task 3-1: 개인 학습 대시보드 API 구현**

- **담당자**: 채기훈
    
- **예상 소요시간**: 5시간
    
- **현재 상태**: 대기 ⏳
    
- **의존성**: Task 1-3, Task 2-2 완료
    
- **완료 기준**:
    
    - [ ] Elasticsearch의 데이터를 집계하여 개인의 문제 해결 잔디밭(히트맵), 알고리즘 태그별 숙련도(레이더 차트용 데이터)를 제공하는 API 구현
        
    - [ ] 자주 조회되는 사용자 프로필, 티어 정보 등은 Redis에 캐싱하여 응답 속도 개선
        

#### **Task 3-2: 맞춤 문제 추천 API 구현**

- **담당자**: 채기훈
    
- **예상 소요시간**: 4시간
    
- **현재 상태**: 대기 ⏳
    
- **의존성**: Task 3-1 완료
    
- **완료 기준**:
    
    - [ ] 사용자의 태그별 숙련도를 분석하여 가장 취약한 알고리즘 태그를 식별하는 로직 구현
        
    - [ ] 해당 태그에 속하면서, 사용자가 아직 풀지 않았고, 사용자의 티어에 맞는 적절한 난이도의 문제를 추천하는 API 구현
        

#### **Task 3-3: 스터디 그룹 대시보드 API 구현**

- **담당자**: 채기훈
    
- **예상 소요시간**: 4시간
    
- **현재 상태**: 대기 ⏳
    
- **의존성**: Task 2-3, Task 3-1 완료
    
- **완료 기준**:
    
    - [ ] 그룹원 전체의 주간/월간 문제 해결 통계 제공 API 구현
        
    - [ ] 그룹 전체의 강점/약점 태그를 분석하여 보여주는 API 구현
        

#### **Task 3-4: 스터디 그룹 상태 분석 및 알림 서비스 구현 (신규)**

- **담당자**: 채기훈
    
- **예상 소요시간**: 5시간
    
- **현재 상태**: 대기 ⏳
    
- **의존성**: Task 1-3, Task 2-4 완료
    
- **완료 기준**:
    
    - [ ] `@Scheduled`를 통해 주기적으로 각 스터디 그룹의 상태를 분석하는 서비스 구현
        
    - [ ] `STUDY_GROUP_RULES`에 설정된 규칙과 그룹원들의 활동 데이터를 비교하여 위반 사례를 감지
        
    - [ ] 감지된 이벤트(예: '홍길동, 1주일간 문제 풀이 없음')를 `study-group-alert` Kafka 토픽으로 발행
        
    - [ ] 해당 토픽을 구독하여 관련자(그룹장, 해당 그룹원)에게 알림을 보내는 로직 구현 (초기에는 로그 출력)
        

### **Phase 4: React 프론트엔드 개발 (Low Priority)**

**목표**: 사용자가 분석 데이터를 시각적으로 확인하고 상호작용할 수 있는 웹 애플리케이션을 개발합니다.

#### **Task 4-1: React + Next.js 프로젝트 초기 설정 및 API 연동**

- **담당자**: 채기훈
    
- **예상 소요시간**: 4시간
    
- **현재 상태**: 대기 ⏳
    
- **의존성**: Phase 2, 3의 API 구현 완료
    
- **완료 기준**:
    
    - [ ] Next.js 프로젝트 생성 및 TypeScript 설정
        
    - [ ] Axios 또는 Fetch API를 사용하여 백엔드 REST API 연동 모듈 구현
        
    - [ ] Tailwind CSS 또는 Styled Components 설정

#### **Task 4-2: 핵심 화면 UI 개발 (React Components)**

- **담당자**: 채기훈
    
- **예상 소요시간**: 10시간
    
- **현재 상태**: 대기 ⏳
    
- **의존성**: Task 4-1 완료
    
- **완료 기준**:
    
    - [ ] 개인 학습 대시보드 화면 구현 (Chart.js 또는 Recharts 사용)
        
    - [ ] 스터디 그룹 대시보드 화면 구현
        
    - [ ] 맞춤 문제 추천 목록 화면 구현
        
    - [ ] 반응형 디자인 적용

#### **Task 4-3: 스터디 그룹 관리 설정 화면 구현**

- **담당자**: 채기훈
    
- **예상 소요시간**: 4시간
    
- **현재 상태**: 대기 ⏳
    
- **의존성**: Task 4-1, Task 2-4 API 완료
    
- **완료 기준**:
    
    - [ ] 그룹장이 스터디 그룹의 자동 관리 규칙을 설정하고 조회/수정할 수 있는 UI 구현
        
    - [ ] 폼 유효성 검사 및 에러 처리

#### **Task 4-4: 추후 모바일 앱 확장 준비**

- **담당자**: 채기훈
    
- **예상 소요시간**: 2시간
    
- **현재 상태**: 대기 ⏳
    
- **완료 기준**:
    
    - [ ] API 클라이언트 코드를 공통 모듈로 분리
        
    - [ ] 비즈니스 로직을 React Native/Flutter와 공유 가능하도록 구조화
        

## 📋 백로그 (Backlog)

---

## Phase 6: Saga 리팩토링 및 분산 추적 시스템 구축 (신규)

**목표**: 불필요하게 복잡한 Saga 패턴을 단순 이벤트 기반 아키텍처로 전환하고, Elastic APM을 도입하여 시스템의 복잡성을 낮추고 관측 가능성(Observability)을 극대화한다.

### **Task 6-0: Elastic APM을 이용한 분산 추적 시스템 구축**
- **우선순위**: 🔥 **Critical** (Phase 6의 모든 작업에 대한 전제 조건)
- **예상 소요시간**: 2시간
- **완료 기준**:
  - [ ] `docker-compose.yml`에 APM 서버 추가 및 실행
  - [ ] `build.gradle.kts`에 APM Agent 의존성 추가
  - [ ] Kibana APM 대시보드에서 애플리케이션 트랜잭션 데이터 확인

### **Task 6-1: `USER_PROFILE_UPDATE_SAGA` 리팩토링**
- **TDD 사이클 계획:**
    - **[RED] 테스트 재정의:**
        1.  `UserProfileUpdateSagaTest`를 삭제하고, `UserService` 통합 테스트를 새로 작성하여 `USER_PROFILE_UPDATED` 이벤트 발행을 검증합니다.
        2.  각 구독 모듈의 이벤트 핸들러 단위 테스트를 작성합니다.
    - **[GREEN] Saga 로직 제거:**
        1.  `UserProfileUpdateSaga` 클래스를 삭제하고, `UserService`에서 직접 이벤트를 발행하도록 수정합니다.
    - **[REFACTOR] 구독자 로직 강화:**
        1.  각 이벤트 핸들러의 멱등성 및 재시도 로직을 강화합니다.

### **Task 6-2: `DISCUSSION_CREATE_SAGA` 리팩토링**
- **TDD 사이클 계획:**
    - **[RED] 테스트 재정의:**
        1.  `DiscussionCreateSagaTest`를 삭제하고, `SocialService` 통합 테스트를 작성하여 `DISCUSSION_CREATED` 이벤트 발행을 검증합니다.
    - **[GREEN] Saga 로직 제거:**
        1.  `DiscussionCreateSaga`를 삭제하고, `SocialService`에서 직접 이벤트를 발행하도록 수정합니다.
    - **[REFACTOR] 구독자 로직 개선:**
        1.  `NotificationService`의 이벤트 핸들러가 독립적으로 동작하도록 수정합니다.

### **Task 6-3: `PERSONAL_STATS_REFRESH_SAGA` 리팩토링**
- **TDD 사이클 계획:**
    - **[RED] 단계별 테스트 재정의:**
        1.  `PersonalStatsRefreshSagaTest`를 삭제합니다.
        2.  각 서비스(Analysis, Cache, Notification)가 특정 이벤트를 수신하고 다음 이벤트를 발행하는지 검증하는 단위/통합 테스트를 각각 작성합니다.
    - **[GREEN] Saga를 이벤트 체인으로 분리:**
        1.  `PersonalStatsRefreshSaga`를 삭제하고, 각 서비스가 이벤트 체인으로 동작하도록 구현을 변경합니다.
    - **[REFACTOR] 각 단계의 독립성 강화:**
        1.  각 이벤트 처리 로직의 예외 처리와 로깅을 강화하여 추적 용이성을 높입니다.

## Phase 6: Saga 리팩토링 및 아키텍처 경량화 (신규)

**목표**: 불필요하게 복잡한 Saga 패턴을 단순 이벤트 기반 아키텍처로 전환하여 시스템의 복잡성을 낮추고, 모듈 간 결합도를 줄이며, 유지보수성을 향상시킨다.

### **Task 6-1: `USER_PROFILE_UPDATE_SAGA` 리팩토링**

- **TDD 사이클 계획:**
    - **[RED] 테스트 재정의:**
        1.  기존 `UserProfileUpdateSagaTest`를 삭제합니다.
        2.  `UserService`에 대한 통합 테스트를 새로 작성합니다. 이 테스트는 `updateProfile` 메서드 호출 시, User 모듈의 DB는 변경되고 `USER_PROFILE_UPDATED` 이벤트가 Outbox에 정확히 저장되는지만을 검증합니다. (이 테스트는 처음에는 실패합니다.)
        3.  각 구독 모듈(Analysis, StudyGroup)의 이벤트 핸들러에 대한 단위 테스트를 작성하여, 이벤트 수신 시 각자 자신의 데이터를 올바르게 동기화하는지 검증합니다.

    - **[GREEN] Saga 로직 제거 및 단순 이벤트 발행:**
        1.  `UserProfileUpdateSaga` 클래스를 삭제합니다.
        2.  `UserService`의 `updateProfile` 메서드에서 Saga를 호출하는 로직을 제거합니다.
        3.  대신, 프로필 업데이트 트랜잭션이 성공한 후 `OutboxService`를 통해 `USER_PROFILE_UPDATED` 이벤트를 발행하도록 수정합니다.
        4.  새로 작성한 통합 테스트를 통과시킵니다.

    - **[REFACTOR] 구독자 로직 강화:**
        1.  각 구독 모듈의 이벤트 핸들러가 멱등성(Idempotency)을 가지도록 코드를 개선합니다. (같은 이벤트를 여러 번 수신해도 문제가 없도록)
        2.  각 핸들러의 예외 처리 및 재시도 로직을 강화합니다.
        3.  관련 없는 Saga 상태 추적 로직을 모두 제거합니다.

### **Task 6-2: `DISCUSSION_CREATE_SAGA` 리팩토링**

- **TDD 사이클 계획:**
    - **[RED] 테스트 재정의:**
        1.  `DiscussionCreateSagaTest`를 삭제합니다.
        2.  `SocialService`에 대한 통합 테스트를 작성하여, 토론 생성 시 `DISCUSSION_CREATED` 이벤트가 Outbox에 저장되는 것을 검증합니다.
        3.  `NotificationService`의 이벤트 핸들러 단위 테스트를 작성하여, 이벤트 수신 시 알림 생성 로직이 동작하는지 검증합니다.

    - **[GREEN] Saga 로직 제거:**
        1.  `DiscussionCreateSaga` 클래스를 삭제합니다.
        2.  `SocialService`의 `createDiscussion` 메서드에서 Saga 로직을 제거하고, `OutboxService`를 통한 이벤트 발행 로직으로 대체합니다.
        3.  새로운 테스트를 통과시킵니다.

    - **[REFACTOR] 구독자 로직 개선:**
        1.  `NotificationService`의 이벤트 핸들러가 알림 발송 실패 시, 토론 생성 트랜잭션에 영향을 주지 않고 독립적으로 재시도하도록 로직을 수정합니다.
        2.  관련 코드의 가독성을 높이고 주석을 정리합니다.

### **Task 6-3: `PERSONAL_STATS_REFRESH_SAGA` 리팩토링**

- **TDD 사이클 계획:**
    - **[RED] 단계별 테스트 재정의:**
        1.  `PersonalStatsRefreshSagaTest`를 삭제합니다.
        2.  **1단계 테스트:** `AnalysisService`가 개인 통계 분석 후 `PERSONAL_STATS_UPDATED` 이벤트를 발행하는지 검증하는 테스트를 작성합니다.
        3.  **2단계 테스트:** `CacheService`의 이벤트 핸들러가 위 이벤트를 수신하여 캐시를 업데이트하고 `PERSONAL_CACHE_REFRESHED` 이벤트를 발행하는지 검증하는 테스트를 작성합니다.
        4.  **3단계 테스트:** `NotificationService`의 이벤트 핸들러가 캐시 갱신 이벤트를 수신하여 알림을 생성하는지 검증합니다.

    - **[GREEN] Saga를 이벤트 체인으로 분리:**
        1.  `PersonalStatsRefreshSaga` 클래스를 삭제합니다.
        2.  각 서비스(Analysis, Cache, Notification)가 이전 단계의 이벤트를 구독하고, 자신의 로직을 처리한 뒤, 다음 단계의 이벤트를 발행하도록 **이벤트 체인(Chain of Events)** 구조로 구현합니다.
        3.  새로 작성한 단계별 테스트를 모두 통과시킵니다.

    - **[REFACTOR] 각 단계의 독립성 강화:**
        1.  각 이벤트 처리 로직이 실패하더라도 다른 단계에 영향을 주지 않도록 예외 처리를 강화합니다.
        2.  각 단계별로 명확한 로그를 남겨 전체 흐름을 추적하기 쉽도록 개선합니다.

### **Task 6-4: 코루틴 `runBlocking` 제거 및 안전한 스코프 적용 (신규)**
- **우선순위**: **높음**
- **대상 파일**: `SubmissionSyncSaga.kt`, `AnalysisUpdateSaga.kt`
- **목표**: `@Scheduled` 메서드 내에서 스레드를 차단하는 `runBlocking`을 제거하고, 애플리케이션 스코프의 `CoroutineScope`를 사용하여 안전한 비동기 작업을 실행하도록 변경합니다.
- **완료 기준**:
  - [ ] `runBlocking` 호출이 프로젝트에서 모두 제거됨.
  - [ ] 별도의 `CoroutineScope`를 주입받아 `launch` 빌더를 사용하는 방식으로 리팩토링 완료.
  - [ ] 관련 단위/통합 테스트가 모두 통과하여 기능의 정상 동작을 보장.

### **Task 6-5: 블로킹 I/O 호출 격리 규칙 전면 적용 (신규)**
- **우선순위**: **높음**
- **대상 파일**: `DataSyncBatchServiceImpl.kt` 등 `suspend` 함수 내에서 I/O가 발생하는 모든 곳.
- **목표**: `suspend` 함수 내에서 실행되는 모든 블로킹 I/O(JPA, RestTemplate 등) 호출을 `withContext(Dispatchers.IO)`로 감싸, 코루틴 스레드가 차단되는 것을 방지하고 시스템 전체의 성능을 보장합니다.
- **완료 기준**:
  - [ ] 코드 전체를 검토하여 `withContext(Dispatchers.IO)`가 누락된 블로킹 호출을 모두 찾아 수정.
  - [ ] 관련 코딩 컨벤션을 `CODING_STANDARDS.md`에 명시하고 팀에 공유.
  - [ ] 코드 리뷰 시 해당 규칙을 최우선으로 점검하는 문화 정착.

### **Task 6-6: Kafka Consumer DLQ(Dead Letter Queue) 도입 (신규)**
- **우선순위**: **중간**
- **목표**: 이벤트 처리 실패 시, 해당 메시지를 별도의 DLQ 토픽으로 보내 재처리나 분석이 가능하도록 하여 시스템 안정성을 높입니다.
- **완료 기준**:
  - [ ] Spring Kafka의 `DeadLetterPublishingRecoverer`를 사용하도록 Kafka Consumer 설정 수정.
  - [ ] `*-dlq` 토픽이 자동으로 생성되고, 처리 실패한 메시지가 해당 토픽으로 전송되는지 통합 테스트로 검증.
  - [ ] DLQ에 쌓인 메시지를 모니터링하고 재처리하는 절차를 `DEBEZIUM_RUNBOOK.md` 또는 별도 운영 문서에 기록.

### 아이디어 단계

- **[아이디어] 업적(Achievement) 시스템**: "DP 마스터", "그래프 탐험가" 등 특정 조건 달성 시 배지를 부여하는 게임화 기능
    
- **[아이디어] 유사 문제 추천**: 특정 문제를 풀었을 때, 비슷한 알고리즘을 사용하는 다른 문제를 추천하는 기능
    
- **[아이디어] 라이벌 기능**: 특정 사용자를 라이벌로 등록하여 문제 해결 현황을 비교하는 기능
    
- **[아이디어] 주간/월간 리포트 자동 생성**: 그룹의 학습 현황을 요약한 리포트를 매주/매월 자동으로 생성하여 이메일이나 디스코드로 발송하는 기능
    

### 개선사항

- **[개선사항]** 문제 추천 알고리즘 고도화 (단순 태그 기반 -> 협업 필터링 등)
    
- **[개선사항]** Elasticsearch 쿼리 튜닝을 통한 대시보드 로딩 속도 최적화
    
- **[개선사항]** 스터디 그룹 알림 커스터마이징 기능 (알림 문구, 발송 시간 등)
    

### 기술 부채

- **[기술부채]** `solved.ac` API 호출 실패 시 재시도 로직 미포함. 추후 `Resilience4j` 등 도입 고려.
    
- **[기술부채]** Kafka 메시지 포맷으로 JSON 사용. 향후 성능 개선을 위해 Avro 또는 Protobuf 도입 검토.