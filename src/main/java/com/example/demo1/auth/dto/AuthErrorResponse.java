package com.example.demo1.auth.dto;

import java.util.Map;

/**
 * @param status      HTTP 상태 코드
 * @param message     오류 설명
 * @param fieldErrors 필드별 검증 오류. 없으면 빈 값
 */
public record AuthErrorResponse(int status, String message, Map<String, String> fieldErrors) {

    public AuthErrorResponse(int status, String message) {
        this(status, message, Map.of());
    }
}
