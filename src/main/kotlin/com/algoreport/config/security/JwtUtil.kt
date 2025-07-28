package com.algoreport.config.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.Key
import java.util.*
import javax.crypto.spec.SecretKeySpec

/**
 * JWT 토큰 생성, 검증, 정보 추출을 담당하는 유틸리티 클래스
 */
@Component
class JwtUtil(
    @Value("\${app.jwt.secret:default-secret-key-for-jwt-token-generation-minimum-256-bits}")
    private val secretKey: String,
    
    @Value("\${app.jwt.expiration:3600000}")
    private val expirationMs: Long
) {
    
    private val key: Key by lazy {
        SecretKeySpec(secretKey.toByteArray(), SignatureAlgorithm.HS256.jcaName)
    }
    
    /**
     * Access Token 생성 (String userId)
     */
    fun generateAccessToken(userId: String, email: String): String {
        val now = Date()
        val expiryDate = Date(now.time + expirationMs)
        
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .claim("type", "access")
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }
    
    /**
     * Refresh Token 생성 (String userId)  
     */
    fun generateRefreshToken(userId: String): String {
        val now = Date()
        val expiryDate = Date(now.time + (expirationMs * 7)) // 7배 긴 만료시간
        
        return Jwts.builder()
            .subject(userId)
            .claim("type", "refresh")
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }
    
    /**
     * 사용자 ID를 기반으로 JWT 토큰 생성 (기존 메서드)
     */
    fun generateToken(userId: Long, additionalClaims: Map<String, Any> = emptyMap()): String {
        val now = Date()
        val expiryDate = Date(now.time + expirationMs)
        
        val builder = Jwts.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
        
        additionalClaims.forEach { (key, value) ->
            builder.claim(key, value)
        }
        
        return builder.compact()
    }
    
    /**
     * JWT 토큰 유효성 검증
     */
    fun validateToken(token: String?): Boolean {
        return try {
            if (token.isNullOrBlank()) return false
            
            val claims = Jwts.parser()
                .verifyWith(key as javax.crypto.SecretKey)
                .build()
                .parseSignedClaims(token)
            
            !isTokenExpired(claims.payload)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * JWT 토큰에서 사용자 ID 추출
     */
    fun getUserIdFromToken(token: String): Long {
        val claims = getClaimsFromToken(token)
        return claims.subject.toLong()
    }
    
    /**
     * JWT 토큰에서 특정 클레임 추출
     */
    fun getClaimFromToken(token: String, claimName: String): String? {
        val claims = getClaimsFromToken(token)
        return claims[claimName] as? String
    }
    
    /**
     * JWT 토큰에서 만료 시간 추출
     */
    fun getExpirationDateFromToken(token: String): Date {
        val claims = getClaimsFromToken(token)
        return claims.expiration
    }
    
    /**
     * JWT 토큰에서 Claims 추출
     */
    private fun getClaimsFromToken(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key as javax.crypto.SecretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
    
    /**
     * 토큰 만료 여부 확인
     */
    private fun isTokenExpired(claims: Claims): Boolean {
        return claims.expiration.before(Date())
    }
}