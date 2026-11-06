package com.example.demo1.security.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * application-local.yaml의 security.cors.* 설정.
 *
 * @param allowedOrigins 다른 출처에서 호출을 허용할 프런트엔드 주소. 예: http://localhost:5173
 *                       스킴과 포트까지 정확히 같아야 하며 끝에 /를 붙이지 않는다.
 * @param maxAge         브라우저가 사전 요청(preflight) 결과를 기억하는 시간. 생략하면 1시간
 */
@Validated
@ConfigurationProperties(prefix = "security.cors")
public record CorsProperties(
        @NotNull List<String> allowedOrigins,
        @DefaultValue("1h") Duration maxAge) {
}
