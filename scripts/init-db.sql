-- 알고리포트 초기 데이터베이스 설정
-- PostgreSQL 16용 초기화 스크립트

-- 데이터베이스가 이미 생성되어 있으므로 스키마만 생성
CREATE SCHEMA IF NOT EXISTS algoreport;

-- 기본 확장 설치
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 시간대 설정
SET timezone = 'UTC';

-- 성능 최적화를 위한 설정
ALTER SYSTEM SET shared_preload_libraries = 'pg_stat_statements';
ALTER SYSTEM SET log_statement = 'all';
ALTER SYSTEM SET log_min_duration_statement = 1000;

-- 설정 재로드
SELECT pg_reload_conf();

COMMENT ON SCHEMA algoreport IS '알고리포트 메인 스키마 - solved.ac 데이터 분석 플랫폼';