package com.example.demo1.security.jwt.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * jwt.* 설정. 서명 키는 application-local.yaml, 유효 시간은 application.yaml에 있다.
 *
 * @param secret               HMAC 서명 키. UTF-8로 32바이트(256비트) 이상이어야 한다. git에 올리지 않는다.
 * @param accessTokenValidity  Access Token 유효 시간. 예: 10m
 * @param refreshTokenValidity Refresh Token 유효 시간. 예: 60m
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration accessTokenValidity,
        @NotNull Duration refreshTokenValidity) {

    private static final int MIN_SECRET_BYTES = 32;

    /**
     * 키가 짧으면 jjwt가 토큰을 만들 때 예외를 던진다. 앱 시작 단계에서 미리 알린다.
     */
    @AssertTrue(message = "jwt.secret은 32바이트(256비트) 이상이어야 합니다.")
    public boolean isSecretLongEnough() {
        return secret == null || secret.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES;
    }

    @AssertTrue(message = "jwt.refresh-token-validity는 jwt.access-token-validity보다 길어야 합니다.")
    public boolean isRefreshLongerThanAccess() {
        return accessTokenValidity == null || refreshTokenValidity == null
                || refreshTokenValidity.compareTo(accessTokenValidity) > 0;
    }
}
