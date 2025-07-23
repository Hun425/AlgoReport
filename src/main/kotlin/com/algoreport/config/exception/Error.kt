package com.algoreport.config.exception

import org.springframework.http.HttpStatus

/**
 * 시스템 전체에서 사용되는 에러 코드 정의
 * HTTP 상태 코드별로 분류하여 관리
 */
enum class Error(val status: HttpStatus, val code: String, val message: String) {
    
    // 400 BAD_REQUEST
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "E40001", "입력값이 올바르지 않습니다."),
    INVALID_SOLVEDAC_HANDLE(HttpStatus.BAD_REQUEST, "E40002", "solved.ac 핸들 형식이 올바르지 않습니다."),
    
    // 401 UNAUTHORIZED  
    UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "E40101", "인증이 필요합니다."),
    INVALID_JWT_TOKEN(HttpStatus.UNAUTHORIZED, "E40102", "유효하지 않은 JWT 토큰입니다."),
    
    // 403 FORBIDDEN
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "E40301", "접근 권한이 없습니다."),
    STUDY_GROUP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "E40302", "스터디 그룹 접근 권한이 없습니다."),
    
    // 404 NOT_FOUND
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "E40401", "해당 사용자를 찾을 수 없습니다."),
    STUDY_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "E40402", "해당 스터디 그룹을 찾을 수 없습니다."),
    SOLVEDAC_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "E40403", "solved.ac에서 해당 핸들을 찾을 수 없습니다."),
    
    // 409 CONFLICT
    ALREADY_EXISTS_USER(HttpStatus.CONFLICT, "E40901", "이미 존재하는 사용자입니다."),
    ALREADY_JOINED_STUDY(HttpStatus.CONFLICT, "E40902", "이미 참여한 스터디 그룹입니다."),
    SOLVEDAC_ALREADY_LINKED(HttpStatus.CONFLICT, "E40903", "이미 연동된 solved.ac 계정입니다."),
    
    // 500 INTERNAL_SERVER_ERROR
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E50001", "서버 내부 오류가 발생했습니다."),
    SOLVEDAC_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E50002", "solved.ac API 호출 중 오류가 발생했습니다."),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E50003", "데이터베이스 처리 중 오류가 발생했습니다."),
    
    // 503 SERVICE_UNAVAILABLE
    SOLVEDAC_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "E50301", "solved.ac API 서비스를 일시적으로 사용할 수 없습니다."),
}