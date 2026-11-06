package com.example.demo1.security.jwt.dto;

import com.example.demo1.user.dto.UserResponse;
import com.example.demo1.user.entity.UserRole;
import java.util.List;

/**
 * JWT 페이로드에 담는 사용자 정보.
 *
 * @param email    사용자 이메일. 토큰의 subject(sub)로 들어간다.
 * @param nickname 닉네임
 * @param roles    권한 목록
 */
public record TokenPayload(String email, String nickname, List<UserRole> roles) {

    public TokenPayload {
        roles = List.copyOf(roles);
    }

    public static TokenPayload from(UserResponse user) {
        return new TokenPayload(user.email(), user.nickname(), user.roles());
    }
}
