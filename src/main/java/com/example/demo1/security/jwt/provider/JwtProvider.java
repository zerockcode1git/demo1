package com.example.demo1.security.jwt.provider;

import com.example.demo1.security.jwt.config.JwtProperties;
import com.example.demo1.security.jwt.dto.TokenPairResponse;
import com.example.demo1.security.jwt.dto.TokenPayload;
import com.example.demo1.security.jwt.exception.ExpiredTokenException;
import com.example.demo1.security.jwt.exception.InvalidTokenException;
import com.example.demo1.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT를 만들고 검증한다. HMAC-SHA256(HS256)으로 서명한다.
 * <p>
 * 페이로드:
 * <pre>
 * {
 *   "sub": "Hong@Example.com",      // 이메일
 *   "nickname": "홍길동",
 *   "roles": ["ROLE_USER", "ROLE_ADMIN"],
 *   "type": "access",               // access 또는 refresh
 *   "iat": 1762500000,              // 발급 시각
 *   "exp": 1762500600               // 만료 시각
 * }
 * </pre>
 * 페이로드는 서명만 될 뿐 암호화되지 않아 누구나 읽을 수 있다. 비밀번호 같은 민감한 정보는 넣지 않는다.
 */
@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtProvider {

    private static final String NICKNAME = "nickname";
    private static final String ROLES = "roles";
    private static final String TYPE = "type";

    private final SecretKey key;
    private final Duration accessTokenValidity;
    private final Duration refreshTokenValidity;
    private final Clock clock;

    /**
     * 생성자가 둘이라 Spring이 어느 것을 쓸지 고르지 못하므로 이 생성자를 쓰도록 지정한다.
     */
    @Autowired
    public JwtProvider(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /**
     * 테스트에서 만료를 확인할 수 있도록 시계를 바꿔 넣을 수 있게 한다.
     */
    JwtProvider(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = properties.accessTokenValidity();
        this.refreshTokenValidity = properties.refreshTokenValidity();
        this.clock = clock;
    }

    /**
     * 같은 시각을 기준으로 Access Token과 Refresh Token을 함께 발급한다.
     */
    public TokenPairResponse createTokenPair(TokenPayload payload) {
        Instant now = clock.instant();
        return new TokenPairResponse(
                create(payload, TokenType.ACCESS, now),
                create(payload, TokenType.REFRESH, now),
                accessTokenValidity.toSeconds(),
                refreshTokenValidity.toSeconds());
    }

    public String createAccessToken(TokenPayload payload) {
        return create(payload, TokenType.ACCESS, clock.instant());
    }

    /**
     * Access Token을 검증하고 페이로드를 돌려준다.
     *
     * @throws ExpiredTokenException 만료된 토큰
     * @throws InvalidTokenException 서명이 틀리거나 형식이 잘못됐거나 Refresh Token인 경우
     */
    public TokenPayload parseAccessToken(String token) {
        return parse(token, TokenType.ACCESS);
    }

    /**
     * Refresh Token을 검증하고 페이로드를 돌려준다.
     *
     * @throws ExpiredTokenException 만료된 토큰
     * @throws InvalidTokenException 서명이 틀리거나 형식이 잘못됐거나 Access Token인 경우
     */
    public TokenPayload parseRefreshToken(String token) {
        return parse(token, TokenType.REFRESH);
    }

    private String create(TokenPayload payload, TokenType type, Instant now) {
        Duration validity = type == TokenType.ACCESS ? accessTokenValidity : refreshTokenValidity;
        return Jwts.builder()
                .subject(payload.email())
                .claim(NICKNAME, payload.nickname())
                .claim(ROLES, payload.roles().stream().map(Enum::name).toList())
                .claim(TYPE, type.claimValue())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    private TokenPayload parse(String token, TokenType expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            String name = expectedType == TokenType.ACCESS ? "Access Token" : "Refresh Token";
            throw new ExpiredTokenException(name + "이 만료되었습니다.", e);
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치, 형식 오류, 빈 문자열 등. 원인은 내부 정보라 메시지에 넣지 않는다.
            throw new InvalidTokenException("유효하지 않은 토큰입니다.", e);
        }

        if (!expectedType.claimValue().equals(claims.get(TYPE, String.class))) {
            throw new InvalidTokenException("토큰 종류가 올바르지 않습니다.");
        }
        return new TokenPayload(claims.getSubject(), claims.get(NICKNAME, String.class), rolesOf(claims));
    }

    private static List<UserRole> rolesOf(Claims claims) {
        Object value = claims.get(ROLES);
        if (!(value instanceof List<?> names)) {
            throw new InvalidTokenException("토큰에 권한 정보가 없습니다.");
        }
        try {
            return names.stream().map(name -> UserRole.valueOf(String.valueOf(name))).toList();
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("토큰의 권한 정보가 올바르지 않습니다.", e);
        }
    }
}
