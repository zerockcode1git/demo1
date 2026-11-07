package com.example.demo1.auth.service;

import com.example.demo1.auth.dto.LoginRequest;
import com.example.demo1.auth.dto.RefreshRequest;
import com.example.demo1.auth.exception.LoginFailedException;
import com.example.demo1.auth.exception.TokenRefreshFailedException;
import com.example.demo1.auth.repository.RefreshTokenRepository;
import com.example.demo1.security.jwt.dto.TokenPairResponse;
import com.example.demo1.security.jwt.dto.TokenPayload;
import com.example.demo1.security.jwt.exception.ExpiredTokenException;
import com.example.demo1.security.jwt.exception.InvalidTokenException;
import com.example.demo1.security.jwt.provider.JwtProvider;
import com.example.demo1.user.dto.UserResponse;
import com.example.demo1.user.entity.User;
import com.example.demo1.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * 없는 이메일로 로그인할 때도 비밀번호 비교를 한 번 하기 위한 값.
     * 비교를 건너뛰면 응답이 눈에 띄게 빨라져, 응답 시간으로 가입된 이메일을 알아낼 수 있다.
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider,
                       RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * 이메일과 비밀번호를 확인하고 Access Token과 Refresh Token을 발급한다.
     *
     * @throws LoginFailedException 이메일이 없거나 비밀번호가 틀린 경우
     */
    public TokenPairResponse login(LoginRequest request) {
        Optional<User> found = userRepository.findWithRolesByEmailKey(User.toEmailKey(request.email()));
        String passwordHash = found.map(User::getPassword).orElse(dummyPasswordHash);
        boolean matches = passwordEncoder.matches(request.password(), passwordHash);
        if (found.isEmpty() || !matches) {
            throw new LoginFailedException();
        }
        return issueTokens(found.get());
    }

    /**
     * Refresh Token을 확인하고 Access Token과 Refresh Token을 모두 새로 발급한다(Refresh Token Rotation).
     * 쓴 Refresh Token은 새 토큰으로 덮어써져 다시 쓸 수 없다.
     * <ol>
     *     <li>서명, 만료, 종류(refresh)를 검증하고 이메일을 꺼낸다.</li>
     *     <li>사용자 정보를 DB에서 다시 읽어 바뀐 닉네임과 권한을 새 토큰에 반영한다.</li>
     *     <li>새 토큰 쌍을 만들고, Redis에 저장된 토큰이 보낸 토큰과 같을 때만 새 Refresh Token으로 바꾼다.
     *         비교와 교체는 SET ... IFEQ 명령 하나로 처리해 동시에 온 요청 중 하나만 성공한다.</li>
     *     <li>다르면 이미 한 번 쓰인(탈취됐을 수 있는) 토큰이다. 저장된 토큰을 바로 지워 그 사용자의 갱신을 모두 막는다.</li>
     * </ol>
     *
     * @throws TokenRefreshFailedException 갱신할 수 없어 다시 로그인해야 하는 경우
     */
    public TokenPairResponse refresh(RefreshRequest request) {
        String refreshToken = request.refreshToken();
        TokenPayload payload;
        try {
            payload = jwtProvider.parseRefreshToken(refreshToken);
        } catch (ExpiredTokenException e) {
            throw new TokenRefreshFailedException("Refresh Token이 만료되었습니다. 다시 로그인해 주세요.");
        } catch (InvalidTokenException e) {
            throw new TokenRefreshFailedException("유효하지 않은 Refresh Token입니다. 다시 로그인해 주세요.");
        }

        String email = payload.email();
        User user = userRepository.findWithRolesByEmailKey(User.toEmailKey(email))
                .orElseThrow(() -> {
                    // 토큰 발급 뒤 탈퇴한 사용자
                    refreshTokenRepository.deleteByEmail(email);
                    return new TokenRefreshFailedException("사용자를 찾을 수 없습니다. 다시 로그인해 주세요.");
                });

        TokenPairResponse tokens = jwtProvider.createTokenPair(TokenPayload.from(UserResponse.from(user)));
        String previous = refreshTokenRepository.replaceIfEquals(
                        email, refreshToken, tokens.refreshToken(), Duration.ofSeconds(tokens.refreshTokenExpiresIn()))
                // 로그아웃했거나, 불일치로 이미 지워졌거나, TTL이 지난 경우. 키가 없으면 아무것도 저장되지 않는다.
                .orElseThrow(() -> new TokenRefreshFailedException("로그인 정보가 없습니다. 다시 로그인해 주세요."));
        if (!sameToken(previous, refreshToken)) {
            // 교체되지 않았다. 저장돼 있던 토큰을 지워 그 사용자의 갱신을 모두 막는다.
            refreshTokenRepository.deleteIfEquals(email, previous);
            throw new TokenRefreshFailedException("사용할 수 없는 Refresh Token입니다. 다시 로그인해 주세요.");
        }
        return tokens;
    }

    /**
     * 토큰 쌍을 만들고 Refresh Token을 Redis에 저장한다.
     * 토큰이 만료되는 시점에 Redis에서도 사라지도록 유효 시간을 TTL로 준다.
     */
    private TokenPairResponse issueTokens(User user) {
        TokenPayload payload = TokenPayload.from(UserResponse.from(user));
        TokenPairResponse tokens = jwtProvider.createTokenPair(payload);
        refreshTokenRepository.save(
                payload.email(), tokens.refreshToken(), Duration.ofSeconds(tokens.refreshTokenExpiresIn()));
        return tokens;
    }

    /**
     * 걸린 시간으로 몇 글자까지 맞았는지 알아낼 수 없도록, 길이와 관계없이 끝까지 비교한다.
     */
    private static boolean sameToken(String stored, String given) {
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}
