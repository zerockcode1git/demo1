package com.example.demo1.auth.controller;

import com.example.demo1.auth.dto.LoginRequest;
import com.example.demo1.auth.dto.RefreshRequest;
import com.example.demo1.auth.service.AuthService;
import com.example.demo1.security.jwt.dto.TokenPairResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/auth/**는 화이트 리스트에 있어 JWT 없이 호출할 수 있다.
 */
@Tag(name = "인증", description = "로그인과 토큰 발급")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호가 맞으면 Access Token(10분)과 Refresh Token(60분)을 발급한다. "
                    + "API를 호출할 때는 Authorization: Bearer {accessToken} 헤더를 붙인다.")
    @PostMapping("/login")
    public TokenPairResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(
            summary = "토큰 갱신",
            description = "Access Token이 만료되면 Refresh Token을 보내 Access Token과 Refresh Token을 모두 새로 받는다. "
                    + "보낸 Refresh Token은 더 이상 쓸 수 없으므로 응답의 새 Refresh Token으로 바꿔 저장한다. "
                    + "Refresh Token이 만료됐거나 저장된 값과 다르면 401을 응답하며, 이때는 다시 로그인해야 한다.")
    @PostMapping("/refresh")
    public TokenPairResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }
}
