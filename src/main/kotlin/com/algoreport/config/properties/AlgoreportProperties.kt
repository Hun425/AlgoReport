package com.algoreport.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * AlgoReport 애플리케이션 설정 Properties
 * 
 * 하드코딩된 설정값들을 외부화하여 유지보수성과 환경별 설정 관리를 개선한다.
 */
@Configuration
@ConfigurationProperties(prefix = "algoreport")
data class AlgoreportProperties(
    val analysis: AnalysisProperties = AnalysisProperties(),
    val external: ExternalProperties = ExternalProperties(),
    val performance: PerformanceProperties = PerformanceProperties()
) {
    
    /**
     * 분석 관련 설정
     */
    data class AnalysisProperties(
        /**
         * 강한 태그 스킬 임계값 (0.7 이상)
         */
        val strongTagThreshold: Double = 0.7,
        
        /**
         * 약한 태그 스킬 임계값 (0.5 미만)  
         */
        val weakTagThreshold: Double = 0.5,
        
        /**
         * 높은 스킬 임계값 (개인 대시보드용)
         */
        val highSkillThreshold: Double = 0.7,
        
        /**
         * 성공률 임계값 (초기 데이터 동기화용)
         */
        val successRateThreshold: Double = 0.7,
        
        /**
         * 최대 추천 문제 수
         */
        val maxRecommendations: Int = 5,
        
        /**
         * 배치 크기 (기본값)
         */
        val defaultBatchSize: Int = 100
    )
    
    /**
     * 외부 서비스 URL 설정
     */
    data class ExternalProperties(
        /**
         * solved.ac API 기본 URL
         */
        val solvedacApiBaseUrl: String = "https://solved.ac/api/v3",
        
        /**
         * 백준 온라인 저지 문제 URL 패턴
         */
        val baekjoonProblemBaseUrl: String = "https://www.acmicpc.net/problem/"
    )
    
    /**
     * 성능 최적화 관련 설정
     */
    data class PerformanceProperties(
        /**
         * SAGA 실패율 임계값
         */
        val sagaFailureRateThreshold: Double = 0.5,
        
        /**
         * 지수 백오프 기본 지연시간 (ms)
         */
        val baseDelayMs: Long = 500L,
        
        /**
         * 최대 재시도 횟수
         */
        val maxRetries: Int = 3,
        
        /**
         * 캐시 TTL (초)
         */
        val cacheTtlSeconds: Long = 3600L,
        
        /**
         * 성능 등급 임계값
         */
        val performanceGradeThreshold: Double = 0.70
    )
}