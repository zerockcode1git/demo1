package com.example.demo1.security.config;

import com.example.demo1.security.filter.AuthCheckFilter;
import com.example.demo1.security.filter.JWTCheckFilter;
import com.example.demo1.security.filter.SecurityErrorWriter;
import com.example.demo1.security.jwt.provider.JwtProvider;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * JWT 기반 인증 설정.
 * <ul>
 *     <li>HttpSession을 쓰지 않는다. 로그인 상태를 서버에 저장하지 않고 매 요청의 JWT로 판단한다.</li>
 *     <li>CORS: application-local.yaml의 security.cors.allowed-origins에 있는 프런트엔드만 호출할 수 있다.</li>
 * </ul>
 * <p>
 * 필터 순서: JWTCheckFilter → AuthCheckFilter → (Spring Security 권한 검사)
 * <p>
 * 두 필터는 @Component로 등록하지 않고 여기서 직접 만든다. @Component로 만들면 Spring Boot가
 * 일반 서블릿 필터로도 등록해 보안 필터 체인과 별도로 한 번 더 실행된다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({SecurityWhitelistProperties.class, CorsProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtProvider jwtProvider,
                                                   SecurityWhitelistProperties whitelistProperties,
                                                   JsonMapper jsonMapper) throws Exception {
        RequestMatcher whitelist = whitelistProperties.toRequestMatcher();
        SecurityErrorWriter errorWriter = new SecurityErrorWriter(jsonMapper);

        http
                // 쿠키 세션을 쓰지 않고 매 요청 헤더로 토큰을 보내므로 CSRF 보호가 필요 없다.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // CORS 처리는 보안 필터 체인 맨 앞쪽(CorsFilter)에서 한다. 사전 요청(OPTIONS)은 여기서 바로 응답하고,
                // 401·403 같은 오류 응답에도 CORS 헤더가 붙어 브라우저가 오류 내용을 읽을 수 있다.
                .cors(Customizer.withDefaults())
                // HttpSession을 만들지도, 쓰지도 않는다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 인증 정보(SecurityContext)는 요청 하나 동안만 요청 속성에 두고 세션에 저장하지 않는다.
                .securityContext(context -> context
                        .securityContextRepository(new RequestAttributeSecurityContextRepository()))
                // 로그인 후 원래 페이지로 돌려보내려고 요청을 세션에 저장하는 기능을 끈다.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                // URL 규칙은 화이트 리스트 허용과 나머지 로그인 필요만 둔다.
                // 기능별 권한은 컨트롤러 메서드에 @PreAuthorize로 지정한다. 예: TodoController의 등록·수정·삭제
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(whitelist).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        // 보통은 AuthCheckFilter가 먼저 401을 응답한다. 필터를 거치지 않은 예외 상황을 대비한다.
                        .authenticationEntryPoint((request, response, e) -> errorWriter.write(
                                response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."))
                        // @PreAuthorize("hasRole('ADMIN')") 등 권한 부족
                        .accessDeniedHandler((request, response, e) -> errorWriter.write(
                                response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "권한이 없습니다.")))
                .addFilterBefore(new JWTCheckFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new AuthCheckFilter(jwtProvider, whitelist, errorWriter), JWTCheckFilter.class);

        return http.build();
    }

    /**
     * .cors(Customizer.withDefaults())가 이 빈을 찾아 쓴다.
     * 토큰은 쿠키가 아니라 Authorization 헤더로 보내므로 자격 증명(쿠키) 전송은 허용하지 않는다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        // 브라우저 코드가 401 응답의 인증 방식 안내 헤더를 읽을 수 있게 한다.
        config.setExposedHeaders(List.of(HttpHeaders.WWW_AUTHENTICATE));
        config.setAllowCredentials(false);
        config.setMaxAge(properties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
