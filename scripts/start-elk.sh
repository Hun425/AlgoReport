#!/bin/bash

# 알고리포트 ELK Stack 시작 스크립트
# 32GB RAM 환경 최적화 버전

set -e

echo "🚀 알고리포트 ELK Stack 시작 중..."
echo "📊 Phase 1: 애플리케이션 로그 관리 모드"

# 필요한 디렉토리 생성
echo "📁 디렉토리 구조 생성 중..."
mkdir -p logs
mkdir -p logs/alerts
mkdir -p elasticsearch/data
mkdir -p logstash/data
mkdir -p logstash/patterns

# 권한 설정
echo "🔐 권한 설정 중..."
chmod 755 logs
chmod 755 logs/alerts
chmod 777 elasticsearch/data
chmod 777 logstash/data

# Docker 네트워크 확인 및 생성
echo "🌐 Docker 네트워크 확인 중..."
if ! docker network ls | grep -q "algoreport-elk"; then
    echo "🌐 algoreport-elk 네트워크 생성 중..."
    docker network create algoreport-elk
fi

# 시스템 설정 확인
echo "⚙️ 시스템 설정 확인 중..."
if [ "$(cat /proc/sys/vm/max_map_count)" -lt 262144 ]; then
    echo "⚠️  Elasticsearch를 위한 시스템 설정 조정이 필요합니다."
    echo "다음 명령어를 실행해주세요:"
    echo "sudo sysctl -w vm.max_map_count=262144"
    echo "echo 'vm.max_map_count=262144' | sudo tee -a /etc/sysctl.conf"
fi

# ELK Stack 시작
echo "🐳 Docker Compose로 ELK Stack 시작 중..."
docker-compose -f docker-compose.elk.yml up -d

# 서비스 상태 확인
echo "⏳ 서비스 시작 대기 중..."
echo "Elasticsearch 시작 대기 (약 30-60초)..."
timeout 120s bash -c 'until curl -f -s http://localhost:9200/_cluster/health; do sleep 5; done' || {
    echo "❌ Elasticsearch 시작 실패"
    docker-compose -f docker-compose.elk.yml logs elasticsearch
    exit 1
}

echo "Logstash 시작 대기 (약 30-45초)..."
timeout 90s bash -c 'until curl -f -s http://localhost:9600; do sleep 5; done' || {
    echo "❌ Logstash 시작 실패"
    docker-compose -f docker-compose.elk.yml logs logstash
    exit 1
}

echo "Kibana 시작 대기 (약 45-90초)..."
timeout 120s bash -c 'until curl -f -s http://localhost:5601/api/status; do sleep 5; done' || {
    echo "❌ Kibana 시작 실패"
    docker-compose -f docker-compose.elk.yml logs kibana
    exit 1
}

# 기본 인덱스 패턴 생성
echo "📊 기본 인덱스 패턴 생성 중..."
sleep 10

# Kibana 인덱스 패턴 생성 API 호출
curl -X POST "localhost:5601/api/saved_objects/index-pattern/algoreport-logs-*" \
    -H "Content-Type: application/json" \
    -H "kbn-xsrf: true" \
    -d '{
        "attributes": {
            "title": "algoreport-logs-*",
            "timeFieldName": "@timestamp"
        }
    }' || echo "⚠️ 인덱스 패턴 생성 실패 (수동으로 생성해주세요)"

curl -X POST "localhost:5601/api/saved_objects/index-pattern/algoreport-errors-*" \
    -H "Content-Type: application/json" \
    -H "kbn-xsrf: true" \
    -d '{
        "attributes": {
            "title": "algoreport-errors-*",
            "timeFieldName": "@timestamp"
        }
    }' || echo "⚠️ 에러 인덱스 패턴 생성 실패 (수동으로 생성해주세요)"

# 완료 메시지
echo ""
echo "✅ 알고리포트 ELK Stack이 성공적으로 시작되었습니다!"
echo ""
echo "📍 접속 정보:"
echo "  🔍 Elasticsearch: http://localhost:9200"
echo "  📊 Kibana:        http://localhost:5601"
echo "  🔧 Logstash API:  http://localhost:9600"
echo ""
echo "📋 기본 사용법:"
echo "  1. Spring Boot 애플리케이션을 logs/ 디렉토리에 로그 출력하도록 설정"
echo "  2. Kibana에서 'algoreport-logs-*' 인덱스 패턴으로 로그 확인"
echo "  3. 에러 로그는 'algoreport-errors-*' 인덱스에서 별도 관리"
echo ""
echo "🔄 Phase 2 확장:"
echo "  비즈니스 데이터 분석이 필요할 때 docker-compose.elk.yml에서"
echo "  주석 처리된 Kafka, Debezium, Schema Registry 서비스 활성화"
echo ""
echo "📝 로그 확인: docker-compose -f docker-compose.elk.yml logs -f [service-name]"
echo "🛑 중지: docker-compose -f docker-compose.elk.yml down"
echo ""