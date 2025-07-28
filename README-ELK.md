# 알고리포트 ELK Stack 가이드

## 📋 개요

**Phase 1**: Spring Boot 애플리케이션 로그 관리 전용 ELK Stack  
**Phase 2**: 향후 solved.ac 비즈니스 데이터 분석으로 확장 가능

## 🚀 빠른 시작

### 1. ELK Stack 시작
```bash
# Linux/Mac
chmod +x scripts/start-elk.sh
./scripts/start-elk.sh

# Windows (Git Bash 또는 WSL 사용)
bash scripts/start-elk.sh

# 또는 직접 Docker Compose 실행
docker-compose -f docker-compose.elk.yml up -d
```

### 2. 접속 확인
- **Kibana 대시보드**: http://localhost:5601
- **Elasticsearch API**: http://localhost:9200
- **Logstash 모니터링**: http://localhost:9600

### 3. Spring Boot 로그 설정

`logback-spring.xml`에 다음 설정 추가:

```xml
<configuration>
    <!-- 파일 출력 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %5level %5pid --- [%15thread] %-40logger{39} : %msg%n</pattern>
        </encoder>
    </appender>

    <!-- TCP 출력 (Logstash 직접 전송) -->
    <appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
        <destination>localhost:5000</destination>
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp/>
                <logLevel/>
                <loggerName/>
                <message/>
                <mdc/>
                <arguments/>
                <stackTrace/>
            </providers>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="FILE"/>
        <appender-ref ref="LOGSTASH"/>
    </root>
</configuration>
```

필요한 의존성:
```kotlin
// build.gradle.kts
implementation("net.logstash.logback:logstash-logback-encoder:7.4")
```

## 📊 Kibana 사용법

### 1. 인덱스 패턴 생성
1. Kibana 접속 → **Stack Management** → **Index Patterns**
2. **Create index pattern** 클릭
3. `algoreport-logs-*` 입력 → **Next step**
4. Time field: `@timestamp` 선택 → **Create index pattern**
5. 에러 로그용: `algoreport-errors-*`도 동일하게 생성

### 2. 로그 검색 (Discover)
```
# 에러 로그만 보기
level:ERROR

# 특정 클래스 로그
class:*Controller*

# 시간 범위 + 에러
level:ERROR AND @timestamp:[now-1h TO now]

# 특정 예외
exception_class:*SQLException*

# HTTP 요청 로그
http_method:POST AND request_uri:*/api/v1/users*
```

### 3. 대시보드 생성 권장 구성

#### **📊 애플리케이션 모니터링 대시보드**
- **로그 레벨 분포**: Pie chart (level 필드)
- **시간별 로그 량**: Histogram (@timestamp)
- **에러 발생 추이**: Line chart (level:ERROR)
- **응답 시간 분석**: 컨트롤러 로그 기반
- **활성 스레드 모니터링**: thread 필드 분석

#### **🚨 에러 추적 대시보드**
- **예외 타입별 분류**: Terms aggregation (exception_class)
- **에러 발생 패턴**: Heat map (시간대별)
- **영향받은 API**: request_uri 기반 분석
- **심각도별 분류**: severity 필드 활용

## 🔧 고급 설정

### 로그 알림 설정 (선택사항)

크리티컬 에러 발생 시 파일 알림:
```bash
# alerts 디렉토리 모니터링
tail -f logs/alerts/critical-errors-$(date +%Y-%m-%d).log
```

### 성능 최적화

#### Elasticsearch 메모리 조정:
```yaml
# docker-compose.elk.yml에서 수정
environment:
  - "ES_JAVA_OPTS=-Xms4g -Xmx8g"  # 더 많은 메모리 할당
```

#### 인덱스 라이프사이클 관리:
```bash
# 30일 이후 로그 자동 삭제
curl -X PUT "localhost:9200/_ilm/policy/algoreport-logs-policy" \
  -H "Content-Type: application/json" \
  -d '{
    "policy": {
      "phases": {
        "delete": {
          "min_age": "30d",
          "actions": {
            "delete": {}
          }
        }
      }
    }
  }'
```

## 🔮 Phase 2 확장 계획

### 비즈니스 데이터 분석 확장

1. **docker-compose.elk.yml** 주석 해제:
   - Kafka + Zookeeper
   - Debezium CDC
   - Schema Registry

2. **새로운 파이프라인 추가**:
   - solved.ac 데이터 수집
   - SAGA 이벤트 처리
   - 사용자 행동 분석

3. **확장된 인덱스**:
   - `submissions-{YYYY.MM}`
   - `saga-events-{YYYY.MM}`
   - `user-activities-{YYYY.MM}`

## 🛠️ 문제 해결

### 자주 발생하는 문제

#### 1. Elasticsearch 시작 안됨
```bash
# 시스템 설정 확인
sudo sysctl -w vm.max_map_count=262144

# 디스크 공간 확인
df -h

# 로그 확인
docker-compose -f docker-compose.elk.yml logs elasticsearch
```

#### 2. Logstash가 로그를 읽지 못함
```bash
# 로그 파일 권한 확인
ls -la logs/

# Logstash 설정 검증
docker exec algoreport-logstash logstash --config.test_and_exit
```

#### 3. Kibana 접속 안됨
```bash
# 서비스 상태 확인
docker-compose -f docker-compose.elk.yml ps

# Kibana 로그 확인
docker-compose -f docker-compose.elk.yml logs kibana
```

### 성능 모니터링

```bash
# Elasticsearch 클러스터 상태
curl http://localhost:9200/_cluster/health?pretty

# 인덱스 크기 확인
curl http://localhost:9200/_cat/indices?v

# Logstash 파이프라인 상태
curl http://localhost:9600/_node/stats/pipelines?pretty
```

## 📝 유지보수

### 정기 작업

1. **로그 정리** (주간):
   ```bash
   # 30일 이상 된 로그 파일 삭제
   find logs/ -name "*.log" -mtime +30 -delete
   ```

2. **인덱스 정리** (월간):
   ```bash
   # 오래된 인덱스 삭제
   curl -X DELETE "localhost:9200/algoreport-logs-$(date -d '30 days ago' +%Y.%m.%d)"
   ```

3. **디스크 공간 모니터링**:
   ```bash
   # Elasticsearch 데이터 크기 확인
   du -sh elasticsearch/data/
   ```

### 백업 (선택사항)

```bash
# 스냅샷 생성
curl -X PUT "localhost:9200/_snapshot/my_backup/snapshot_$(date +%Y%m%d)" \
  -H "Content-Type: application/json" \
  -d '{
    "indices": "algoreport-logs-*,algoreport-errors-*",
    "ignore_unavailable": true
  }'
```

## 🚨 운영 시 주의사항

1. **리소스 모니터링**: 32GB RAM 환경에서 현재 설정으로 약 10GB 사용
2. **디스크 공간**: 로그 증가에 따른 저장공간 확보 필요
3. **네트워크**: 5000, 5044, 5601, 9200, 9600 포트 충돌 주의
4. **보안**: 프로덕션 환경에서는 인증 활성화 필요

---

📝 **문서 버전**: v1.0  
📅 **최종 수정일**: 2025-07-28  
👤 **작성자**: 채기훈