package com.algoreport.config

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import redis.embedded.RedisServer
import java.io.IOException
import java.net.ServerSocket

/**
 * 테스트용 Embedded Redis 설정
 * 실제 Redis 인스턴스와 동일한 동작으로 캐시 테스트 수행
 */
@TestConfiguration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = ["test.embedded-redis.enabled"], 
    havingValue = "true", 
    matchIfMissing = false
)
class EmbeddedRedisConfig {
    
    private val logger = LoggerFactory.getLogger(EmbeddedRedisConfig::class.java)
    private var redisServer: RedisServer? = null
    private val redisPort: Int by lazy { findAvailablePort() }
    
    @PostConstruct
    fun startRedis() {
        try {
            redisServer = RedisServer.builder()
                .port(redisPort)
                .setting("maxmemory 128M")
                .build()
            
            redisServer?.start()
            logger.info("Embedded Redis started on port: {}", redisPort)
        } catch (e: Exception) {
            logger.error("Failed to start Embedded Redis", e)
            throw RuntimeException("Could not start embedded Redis server", e)
        }
    }
    
    @PreDestroy
    fun stopRedis() {
        try {
            redisServer?.stop()
            logger.info("Embedded Redis stopped")
        } catch (e: Exception) {
            logger.error("Failed to stop Embedded Redis", e)
        }
    }
    
    @Bean
    @Primary
    fun testRedisConnectionFactory(): RedisConnectionFactory {
        return LettuceConnectionFactory("localhost", redisPort)
    }
    
    @Bean
    @Primary
    fun testRedisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
        val template = RedisTemplate<String, String>()
        template.connectionFactory = connectionFactory
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = StringRedisSerializer()
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = StringRedisSerializer()
        template.afterPropertiesSet()
        return template
    }
    
    /**
     * 사용 가능한 포트 찾기
     */
    private fun findAvailablePort(): Int {
        return try {
            ServerSocket(0).use { socket ->
                socket.localPort
            }
        } catch (e: IOException) {
            6370 // 기본 포트 (Redis 기본 6379 피해서)
        }
    }
}