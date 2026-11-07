package com.example.demo1.auth.repository;

import com.example.demo1.user.entity.User;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Refresh Token을 Redis에 저장한다.
 * <pre>
 * 키:   refresh-token:{소문자 이메일}   예: refresh-token:hong@example.com
 * 값:   Refresh Token 문자열
 * TTL:  Refresh Token 유효 시간. 시간이 지나면 Redis가 알아서 지운다.
 * </pre>
 * 사용자 한 명당 토큰을 하나만 둔다. 다시 로그인하면 이전 Refresh Token은 덮어써져 더 이상 쓸 수 없다.
 */
@Repository
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh-token:";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Spring Boot가 만드는 StringRedisTemplate도 RedisTemplate&lt;String, String&gt; 타입이라
     * 후보가 둘이 된다. RedisConfig에서 만든 빈을 쓰도록 이름으로 지정한다.
     */
    public RefreshTokenRepository(@Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 토큰을 저장한다. 같은 사용자의 토큰이 이미 있으면 덮어쓴다.
     *
     * @param ttl 저장 유지 시간. Refresh Token 유효 시간과 같게 준다.
     */
    public void save(String email, String refreshToken, Duration ttl) {
        redisTemplate.opsForValue().set(keyOf(email), refreshToken, ttl);
    }

    /**
     * 저장된 토큰이 expected와 같을 때만 newToken으로 바꾸고 TTL을 다시 건다. 비교와 교체를 Redis가 한 번에 처리한다.
     * <pre>
     * SET refresh-token:{이메일} {newToken} IFEQ {expected} GET PX {ttl}
     * </pre>
     * IFEQ는 Redis 8.4부터 지원한다.
     * 같은 토큰으로 갱신 요청이 동시에 와도 둘 중 하나만 교체에 성공한다.
     *
     * @return 명령 실행 전에 저장돼 있던 토큰. expected와 같으면 교체된 것이고, 다르면 교체되지 않은 것이다.
     *         키가 없으면(로그아웃, TTL 지남) 비어 있다.
     */
    public Optional<String> replaceIfEquals(String email, String expected, String newToken, Duration ttl) {
        String previous = redisTemplate.opsForValue()
                .setGet(keyOf(email), newToken, spec -> spec.ifEquals().value(expected).expire(ttl));
        return Optional.ofNullable(previous);
    }

    /**
     * 저장된 토큰이 token과 같을 때만 지운다(DELEX ... IFEQ).
     * 확인한 뒤 지우기 전에 사용자가 다시 로그인해 새 토큰이 저장됐다면, 그 토큰은 지우지 않는다.
     */
    public void deleteIfEquals(String email, String token) {
        redisTemplate.compareAndDelete(keyOf(email), token);
    }

    public void deleteByEmail(String email) {
        redisTemplate.delete(keyOf(email));
    }

    /**
     * 대소문자만 다른 이메일도 같은 키가 되도록 users.email_key와 같은 규칙으로 바꾼다.
     */
    private static String keyOf(String email) {
        return KEY_PREFIX + User.toEmailKey(email);
    }
}
