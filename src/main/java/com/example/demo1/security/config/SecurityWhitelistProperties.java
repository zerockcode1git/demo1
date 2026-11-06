package com.example.demo1.security.config;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 없이 호출할 수 있는 요청 목록(화이트 리스트). application-local.yaml의 security.whitelist.
 * <p>
 * 항목 형식:
 * <ul>
 *     <li>{@code /swagger-ui/**}: 모든 HTTP 메서드</li>
 *     <li>{@code GET /api/products/**}: 지정한 메서드만</li>
 * </ul>
 * 경로는 PathPattern 문법을 쓴다. {@code /api/products/**}는 {@code /api/products}도 포함한다.
 */
@Validated
@ConfigurationProperties(prefix = "security")
public record SecurityWhitelistProperties(@NotNull List<String> whitelist) {

    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    /**
     * 목록을 하나의 RequestMatcher로 만든다. 형식이 잘못된 항목이 있으면 앱 시작 단계에서 실패한다.
     */
    public RequestMatcher toRequestMatcher() {
        if (whitelist.isEmpty()) {
            return request -> false;
        }
        PathPatternRequestMatcher.Builder builder = PathPatternRequestMatcher.withDefaults();
        List<RequestMatcher> matchers = whitelist.stream().map(entry -> toMatcher(builder, entry)).toList();
        return new OrRequestMatcher(matchers);
    }

    private static RequestMatcher toMatcher(PathPatternRequestMatcher.Builder builder, String entry) {
        String[] parts = entry.strip().split("\\s+");
        if (parts.length == 1 && parts[0].startsWith("/")) {
            return builder.matcher(parts[0]);
        }
        if (parts.length == 2 && METHODS.contains(parts[0]) && parts[1].startsWith("/")) {
            return builder.matcher(HttpMethod.valueOf(parts[0]), parts[1]);
        }
        throw new IllegalArgumentException(
                "security.whitelist 항목 형식이 올바르지 않습니다. '/경로' 또는 'GET /경로' 형식이어야 합니다: " + entry);
    }
}
