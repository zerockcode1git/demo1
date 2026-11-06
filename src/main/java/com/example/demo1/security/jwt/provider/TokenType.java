package com.example.demo1.security.jwt.provider;

import java.util.Locale;

/**
 * 토큰 종류. 페이로드의 type 클레임에 소문자로 들어간다.
 * Refresh Token을 Access Token 자리에 쓰는 것처럼 종류를 바꿔 쓰지 못하게 검증할 때 쓴다.
 */
public enum TokenType {
    ACCESS,
    REFRESH;

    String claimValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
