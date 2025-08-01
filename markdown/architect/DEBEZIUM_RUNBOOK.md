# Debezium 운영 런북 (Runbook)

**목표**: 이 문서는 알고리포트 시스템의 핵심 구성요소인 Debezium CDC(Change Data Capture) 파이프라인에 장애가 발생했을 때, 신속하고 정확하게 대응하기 위한 절차를 정의합니다.

---

## 🚨 **장애 시나리오별 대응 절차**

### **시나리오 1: Debezium 커넥터 장애 (상태: FAILED)**

- **장애 현상:**
  - Kafka Connect UI 또는 API에서 특정 커넥터의 상태가 `FAILED`로 표시됩니다.
  - Outbox 테이블에 이벤트가 쌓이고 있지만, 해당 이벤트가 Kafka 토픽으로 발행되지 않습니다.
  - Kibana의 APM 대시보드에서 이벤트 처리량이 급감합니다.

- **원인 분석:**
  1.  **일시적인 네트워크 문제:** PostgreSQL 또는 Kafka와의 연결이 일시적으로 끊겼을 수 있습니다.
  2.  **데이터 스키마 불일치:** Outbox 테이블의 스키마가 변경되었지만, 커넥터 설정이 이를 따라가지 못했을 수 있습니다.
  3.  **WAL(Write-Ahead Log) 손상:** 드물지만 PostgreSQL의 WAL 파일에 문제가 생겼을 수 있습니다.
  4.  **Kafka Connect 리소스 부족:** Kafka Connect 클러스터의 메모리나 CPU가 부족할 수 있습니다.

- **복구 절차 (순서대로 진행):**

  1.  **[1단계: 원인 파악] 커넥터 로그 확인 (가장 먼저)**
      ```bash
      # Kafka Connect 컨테이너의 로그를 확인하여 정확한 에러 메시지를 찾습니다.
      docker logs <kafka-connect-container-name> | grep "outbox-connector"
      ```

  2.  **[2단계: 단순 재시작] 커넥터 재시작**
      - 대부분의 일시적인 문제는 재시작으로 해결됩니다.
      ```bash
      # Kafka Connect REST API를 통해 커넥터를 재시작합니다.
      curl -X POST http://localhost:8083/connectors/outbox-connector/restart
      ```
      - 재시작 후, 1분 내에 상태가 `RUNNING`으로 변경되는지 확인합니다.

  3.  **[3단계: 데이터 불일치 해결] 토픽 및 오프셋 초기화 (주의 필요!)**
      - **경고: 이 작업은 데이터 유실을 유발할 수 있으므로, 반드시 데이터 백업 및 동기화 상태를 확인한 후에 진행해야 합니다.**
      - 주로 스키마 변경이나 WAL 손상 시 사용합니다.
      - **절차:**
        a. **커넥터 삭제:**
           ```bash
           curl -X DELETE http://localhost:8083/connectors/outbox-connector
           ```
        b. **관련 내부 토픽 삭제:** Kafka에서 Debezium이 사용하는 내부 토픽(오프셋, 스키마 변경 등)을 삭제합니다.
           ```bash
           # Kafka 컨테이너 접속
           docker exec -it <kafka-container-name> bash
           # 토픽 삭제 (예시)
           kafka-topics --bootstrap-server localhost:9092 --delete --topic connect-configs
           kafka-topics --bootstrap-server localhost:9092 --delete --topic connect-offsets
           kafka-topics --bootstrap-server localhost:9092 --delete --topic connect-status
           ```
        c. **PostgreSQL 복제 슬롯(Replication Slot) 재설정 (필요 시):** Debezium이 사용하는 복제 슬롯을 정리하고 싶을 때 사용합니다.
           ```sql
           -- 복제 슬롯 확인
           SELECT * FROM pg_replication_slots;
           -- 복제 슬롯 삭제 (주의!)
           SELECT pg_drop_replication_slot('debezium_slot_name');
           ```
        d. **커넥터 재등록:** 초기 `outbox-connector.json` 설정으로 커넥터를 다시 생성합니다. Debezium은 WAL의 처음부터 다시 읽기 시작합니다 (초기 스냅샷).
           ```bash
           curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d @scripts/outbox-connector.json
           ```

--- 

### **시나리오 2: 신규 모듈 추가 또는 대규모 데이터 동기화**

- **상황:** 새로운 마이크로서비스(모듈)를 추가하여, 기존 테이블의 모든 데이터를 신규 서비스로 동기화해야 할 때.

- **절차:**

  1.  **[1단계: 신규 커넥터 설정 파일 작성]**
      - 기존 `outbox-connector.json`을 복사하여, 새로운 테이블을 가리키는 신규 커넥터 설정 파일(예: `new-module-snapshot-connector.json`)을 작성합니다.
      - `snapshot.mode`를 `initial`로 설정하여, 커넥터가 시작될 때 테이블의 전체 데이터를 스냅샷으로 찍도록 합니다.
      ```json
      {
        "name": "new-module-snapshot-connector",
        "config": {
          "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
          "database.hostname": "postgres",
          // ... (기존 설정과 동일)
          "table.include.list": "public.new_module_table",
          "snapshot.mode": "initial" // <-- 중요!
        }
      }
      ```

  2.  **[2단계: 스냅샷 커넥터 등록]**
      - 작성한 설정 파일로 새로운 커넥터를 등록합니다. Debezium은 즉시 `new_module_table`의 전체 데이터를 읽어 Kafka 토픽으로 발행합니다.

  3.  **[3단계: 데이터 동기화 확인]**
      - 신규 모듈이 Kafka 토픽의 데이터를 모두 소비하여 초기 동기화를 완료했는지 확인합니다.

  4.  **[4단계: 실시간 CDC 모드로 전환]**
      - 초기 동기화가 완료되면, 커넥터 설정을 변경하여 실시간 변경분만 감지하도록 전환합니다.
      - `snapshot.mode`를 `never` 또는 `initial_only`로 변경하거나, 해당 설정을 제거하고 커넥터를 업데이트합니다.
      - 또는, 초기 스냅샷용 커넥터를 삭제하고, 실시간 변경 감지용 커넥터를 새로 등록할 수도 있습니다.

--- 

### **모니터링 및 알림 설정**

- **Prometheus + Grafana**를 사용하여 다음 메트릭을 필수로 모니터링합니다.
  - `debezium_metrics_milli_seconds_behind_source`: Debezium이 실제 DB 변경사항을 얼마나 지연해서 따라가고 있는지 (가장 중요!). 이 값이 계속 증가하면 심각한 문제입니다.
  - `debezium_metrics_number_of_failed_records`: 실패한 레코드 수.
  - `kafka_connect_worker_connector_count{state="failed"}`: 실패한 커넥터의 수.

- **Alertmanager 규칙 예시:**
  ```yaml
  - alert: DebeziumConnectorFailed
    expr: kafka_connect_worker_connector_count{state="failed"} > 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "Debezium 커넥터 장애 발생 ({{ $labels.connector }})"
      description: "커넥터가 1분 이상 FAILED 상태입니다. 즉시 확인이 필요합니다."
  ```
