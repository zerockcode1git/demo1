package com.example.demo1.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param refreshToken 로그인이나 이전 갱신에서 받은 Refresh Token
 */
public record RefreshRequest(@NotBlank(message = "Refresh Token을 입력해 주세요.") String refreshToken) {
}
