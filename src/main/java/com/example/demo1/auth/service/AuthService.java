package com.example.demo1.auth.service;

import com.example.demo1.auth.dto.LoginRequest;
import com.example.demo1.auth.exception.LoginFailedException;
import com.example.demo1.security.jwt.dto.TokenPairResponse;
import com.example.demo1.security.jwt.dto.TokenPayload;
import com.example.demo1.security.jwt.provider.JwtProvider;
import com.example.demo1.user.dto.UserResponse;
import com.example.demo1.user.entity.User;
import com.example.demo1.user.repository.UserRepository;
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

    /**
     * 없는 이메일로 로그인할 때도 비밀번호 비교를 한 번 하기 위한 값.
     * 비교를 건너뛰면 응답이 눈에 띄게 빨라져, 응답 시간으로 가입된 이메일을 알아낼 수 있다.
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
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
        return jwtProvider.createTokenPair(TokenPayload.from(UserResponse.from(found.get())));
    }
}
