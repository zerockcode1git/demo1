package com.example.demo1.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 비밀번호 길이 같은 가입 규칙은 검사하지 않는다. 규칙을 알려 주지 않고, 맞지 않으면 로그인 실패로만 처리한다.
 *
 * @param email    가입한 이메일. 대소문자를 구분하지 않는다.
 * @param password 원문 비밀번호
 */
public record LoginRequest(
        @NotBlank(message = "이메일을 입력해 주세요.") String email,
        @NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
}
