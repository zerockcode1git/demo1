package com.example.demo1.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 모든 요청에서 첫 번째로 실행되며, 요청을 막지 않는다.
 * <ul>
 *     <li>JWT가 있으면 그대로 통과시킨다. 검증은 다음 필터인 {@link AuthCheckFilter}가 한다.</li>
 *     <li>JWT가 없으면 GUEST(권한 ROLE_GUEST)로 SecurityContext에 넣고 통과시킨다.</li>
 * </ul>
 * GUEST는 익명 인증(AnonymousAuthenticationToken)이라, 로그인이 필요한 경로(authenticated)에서는 인증된 사용자로 보지 않는다.
 * 화이트 리스트 밖의 경로는 GUEST라도 {@link AuthCheckFilter}에서 401로 막힌다.
 */
public class JWTCheckFilter extends OncePerRequestFilter {

    public static final String GUEST = "GUEST";
    private static final String GUEST_KEY = "guest";
    private static final List<SimpleGrantedAuthority> GUEST_AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_GUEST"));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (BearerTokens.resolve(request) == null) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new AnonymousAuthenticationToken(GUEST_KEY, GUEST, GUEST_AUTHORITIES));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
