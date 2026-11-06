package com.example.demo1.security.filter;

import com.example.demo1.security.jwt.dto.TokenPayload;
import com.example.demo1.security.jwt.exception.ExpiredTokenException;
import com.example.demo1.security.jwt.exception.InvalidTokenException;
import com.example.demo1.security.jwt.provider.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT가 있어야만 통과할 수 있는 필터. 화이트 리스트 경로에서는 실행하지 않는다.
 * <ol>
 *     <li>JWT가 없으면 401(TOKEN_MISSING)</li>
 *     <li>만료됐으면 401(TOKEN_EXPIRED). 클라이언트는 Refresh Token으로 재발급을 시도한다.</li>
 *     <li>서명이 틀리거나 형식이 잘못됐거나 Refresh Token이면 401(TOKEN_INVALID)</li>
 *     <li>통과하면 토큰의 사용자 정보(TokenPayload)와 권한을 SecurityContext에 넣는다.</li>
 * </ol>
 * 컨트롤러에서는 {@code @AuthenticationPrincipal TokenPayload user}로 사용자 정보를 받을 수 있다.
 */
public class AuthCheckFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final RequestMatcher whitelist;
    private final SecurityErrorWriter errorWriter;

    public AuthCheckFilter(JwtProvider jwtProvider, RequestMatcher whitelist, SecurityErrorWriter errorWriter) {
        this.jwtProvider = jwtProvider;
        this.whitelist = whitelist;
        this.errorWriter = errorWriter;
    }

    /**
     * CORS 사전 요청(OPTIONS)은 토큰을 싣지 않는다. 보통은 앞의 CorsFilter가 먼저 응답하지만,
     * 여기서 401로 막히지 않도록 한 번 더 제외한다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return CorsUtils.isPreFlightRequest(request) || whitelist.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = BearerTokens.resolve(request);
        if (token == null) {
            errorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "TOKEN_MISSING", "로그인이 필요합니다.");
            return;
        }

        TokenPayload payload;
        try {
            payload = jwtProvider.parseAccessToken(token);
        } catch (ExpiredTokenException e) {
            errorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "TOKEN_EXPIRED", e.getMessage());
            return;
        } catch (InvalidTokenException e) {
            errorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "TOKEN_INVALID", e.getMessage());
            return;
        }

        // UserRole 이름이 ROLE_ 접두사를 가지고 있어 hasRole("ADMIN") 같은 검사와 바로 맞는다.
        List<SimpleGrantedAuthority> authorities = payload.roles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .toList();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(payload, null, authorities));
        SecurityContextHolder.setContext(context);

        chain.doFilter(request, response);
    }
}
