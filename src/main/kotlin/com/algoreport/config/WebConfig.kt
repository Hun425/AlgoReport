package com.algoreport.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

/**
 * Web 관련 설정
 * 
 * RestTemplate, WebClient 등 HTTP 클라이언트 Bean 설정
 */
@Configuration
class WebConfig {
    
    /**
     * RestTemplate Bean 등록
     * 
     * solved.ac API 호출을 위해 사용됩니다.
     */
    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }
}