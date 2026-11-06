package com.example.demo1.security.jwt.exception;

/**
 * 유효 시간이 지난 토큰일 때 발생한다. 클라이언트가 Refresh Token으로 재발급을 시도할지 판단할 수 있도록
 * {@link InvalidTokenException}과 구분한다.
 */
public class ExpiredTokenException extends RuntimeException {

    public ExpiredTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
