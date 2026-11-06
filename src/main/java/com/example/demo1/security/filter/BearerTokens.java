package com.example.demo1.security.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * Authorization 헤더에서 Bearer 토큰을 꺼낸다.
 */
final class BearerTokens {

    private static final String PREFIX = "Bearer ";

    private BearerTokens() {
    }

    /**
     * @return "Bearer {토큰}" 형식이면 토큰, 헤더가 없거나 다른 형식(Basic 등)이거나 토큰이 비어 있으면 null
     */
    static String resolve(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return null;
        }
        String token = header.substring(PREFIX.length()).strip();
        return token.isEmpty() ? null : token;
    }
}
