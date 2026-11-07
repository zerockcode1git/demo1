package com.example.demo1.auth.exception;

/**
 * Refresh Token으로 토큰을 갱신할 수 없을 때 발생한다. 만료, 위조, 저장된 값과 불일치 등 원인과 관계없이
 * 클라이언트는 다시 로그인해야 한다.
 */
public class TokenRefreshFailedException extends RuntimeException {

    public TokenRefreshFailedException(String message) {
        super(message);
    }
}
