package com.example.demo1.user.dto;

import com.example.demo1.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * @param email    아이디로 쓰는 이메일. 입력한 대소문자 그대로 저장하고, 중복 확인과 조회는 대소문자를 구분하지 않는다.
 * @param password 원문 비밀번호. 8~64자. 저장할 때 BCrypt로 암호화한다.
 * @param nickname 생략하거나 공백이면 이메일을 닉네임으로 쓴다.
 * @param roles    권한 목록. 생략하거나 비어 있으면 ROLE_USER로 등록한다.
 */
public record UserCreateRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.") String email,
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.") String password,
        @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.") String nickname,
        Set<@NotNull(message = "권한을 입력해 주세요.") UserRole> roles) {

    public UserCreateRequest {
        roles = roles == null || roles.isEmpty() ? Set.of(UserRole.ROLE_USER) : roles;
    }
}
