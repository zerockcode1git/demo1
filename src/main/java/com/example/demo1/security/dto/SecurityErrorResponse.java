package com.example.demo1.security.dto;

/**
 * 필터에서 바로 내보내는 인증·권한 오류 응답. 필터의 예외는 @RestControllerAdvice까지 가지 않으므로 직접 쓴다.
 *
 * @param status  HTTP 상태 코드
 * @param code    클라이언트가 분기할 때 쓰는 오류 코드.
 *                TOKEN_MISSING, TOKEN_EXPIRED, TOKEN_INVALID, UNAUTHORIZED, FORBIDDEN
 * @param message 오류 설명
 */
public record SecurityErrorResponse(int status, String code, String message) {
}
