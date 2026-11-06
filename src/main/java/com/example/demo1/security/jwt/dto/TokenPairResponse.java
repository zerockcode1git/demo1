package com.example.demo1.security.jwt.dto;

/**
 * @param accessToken           API 호출에 쓰는 토큰
 * @param refreshToken          Access Token을 다시 발급받을 때 쓰는 토큰
 * @param accessTokenExpiresIn  Access Token 유효 시간(초)
 * @param refreshTokenExpiresIn Refresh Token 유효 시간(초)
 */
public record TokenPairResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn) {
}
