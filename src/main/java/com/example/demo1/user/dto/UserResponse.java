package com.example.demo1.user.dto;

import com.example.demo1.user.entity.User;
import com.example.demo1.user.entity.UserRole;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 비밀번호는 암호화된 값이라도 응답에 넣지 않는다.
 *
 * @param email 가입할 때 입력한 대소문자 그대로의 이메일
 * @param roles 권한 목록. ROLE_USER, ROLE_MANAGER, ROLE_ADMIN 순서로 정렬된다.
 */
public record UserResponse(
        String email,
        String nickname,
        List<UserRole> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getEmail(),
                user.getNickname(),
                user.getRoles().stream().sorted().toList(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
