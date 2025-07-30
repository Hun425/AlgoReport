# 알고리포트 (Algo-Report)

`solved.ac` 사용자 및 스터디 그룹의 문제 해결 이력을 분석하여 학습 패턴 시각화, 강점/약점 분석, 맞춤 문제 추천 및 스터디 자동 관리를 제공하는 플랫폼입니다.

## 🎯 핵심 기능

- **개인/그룹 학습 현황 분석**: 문제 해결 이력을 기반으로 잔디밭, 태그별 숙련도 등 학습 현황을 시각화
- **맞춤 문제 추천**: 사용자의 취약점을 분석하여 풀어볼 만한 문제를 추천
- **스터디 자동 관리**: 그룹장이 설정한 규칙에 따라 스터디원의 활동을 모니터링하고 자동으로 알림 발송

## 🏗️ 기술 스택

### **Backend**
- **Language**: Kotlin 2.2.0
- **JDK**: Java 21 LTS (장기 지원 + 안정성)
- **Framework**: Spring Boot 3.5.3
- **ORM**: Spring Data JPA + QueryDSL (타입 안전한 복잡 쿼리)
- **Architecture**: 모듈형 모놀리스 + SAGA 패턴
- **Database**: PostgreSQL (Production), H2 (Testing)
- **Cache**: Redis
- **Message Queue**: Apache Kafka + Debezium CDC
- **Search Engine**: Elasticsearch + Kibana
- **Authentication**: Google OAuth2 + JWT

### **Frontend**
- **Language**: TypeScript
- **Framework**: React + Next.js
- **Future Mobile**: React Native 또는 Flutter (추후 결정)

## 🚀 빠른 시작

### 1. 필수 요구사항

- Java 21 LTS+
- Docker & Docker Compose
- Node.js 18+ (프론트엔드 개발 시)

### 2. 인프라 실행

```bash
# Docker 인프라 실행 (PostgreSQL, Redis, Kafka, Elasticsearch)
docker-compose up -d

# 서비스 상태 확인
docker-compose ps
```

### 3. 애플리케이션 실행

```bash
# 프로젝트 빌드
./gradlew build

# 개발 모드로 실행
./gradlew bootRun --args='--spring.profiles.active=dev'

# 또는 프로덕션 모드로 실행
./gradlew bootRun --args='--spring.profiles.active=prod'
```

### 4. 서비스 접속

- **애플리케이션**: http://localhost:8080
- **H2 Console** (dev): http://localhost:8080/h2-console
- **Kibana**: http://localhost:5601
- **Kafka UI**: http://localhost:8080 (개발용)
- **Spring Actuator**: http://localhost:8080/actuator

## 🧪 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "com.algoreport.module.user.*"

# 테스트 리포트 확인
open build/reports/tests/test/index.html
```

## 🏛️ 프로젝트 구조

```
src/main/kotlin/com/algoreport/
├── AlgoReportApplication.kt          # 메인 애플리케이션
├── config/                           # 설정 및 공통 기능
│   ├── security/                     # OAuth2, JWT & Spring Security
│   └── exception/                    # 전역 예외 처리
└── module/                           # 도메인별 논리적 모듈
    ├── user/                         # 플랫폼 사용자 모듈
    ├── studygroup/                   # 스터디 그룹 모듈
    ├── analysis/                     # 분석 및 추천 모듈
    └── notification/                 # 알림 모듈
```

## 🎨 개발 환경

- **IDE**: IntelliJ IDEA 2025.x (Java 21 LTS 최적화)
- **Code Style**: Kotlin Official Style
- **Git Hook**: Pre-commit 테스트 실행
- **TDD**: Red-Green-Refactor 사이클 엄격 적용

## 📚 문서

- **[아키텍처 설계](markdown/architect/Architecture.md)**: 전체 시스템 아키텍처
- **[API 명세](markdown/architect/API.md)**: REST API 문서
- **[SAGA 패턴](markdown/saga/)**: 분산 트랜잭션 설계
- **[TDD 가이드](markdown/TDD_GUIDE.md)**: 테스트 주도 개발 방법론
- **[진행 상황](markdown/PHASE_TRACKER.md)**: Phase별 개발 현황

## 🤝 기여하기

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/amazing-feature`)
3. **TDD 사이클 준수** (Red-Green-Refactor 각 단계마다 커밋)
4. Commit your Changes (`git commit -m 'feat: Add some AmazingFeature'`)
5. Push to the Branch (`git push origin feature/amazing-feature`)
6. Open a Pull Request

## 📝 커밋 메시지 규칙

- `feat`: 새로운 기능 추가
- `fix`: 버그 수정
- `docs`: 문서 수정
- `refactor`: 코드 리팩토링
- `test`: 테스트 코드 (Red-Green-Refactor 표시 필수)
- `chore`: 빌드 설정 등

## 📄 라이선스

MIT License - 자세한 내용은 [LICENSE](LICENSE) 파일을 참고하세요.

## 👤 개발자

**채기훈** - 알고리포트 개발자

---

📝 **최종 업데이트**: 2025-07-22  
🚀 **현재 버전**: v0.0.1-SNAPSHOT
