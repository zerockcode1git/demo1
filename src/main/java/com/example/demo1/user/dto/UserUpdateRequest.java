package com.example.demo1.user.dto;

import com.example.demo1.user.entity.UserRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * 이메일(아이디)은 바꿀 수 없다.
 *
 * @param password 새 비밀번호. 생략하면 기존 비밀번호를 유지한다. 8~64자
 * @param nickname 생략하거나 공백이면 이메일을 닉네임으로 쓴다.
 * @param roles    수정 후의 권한 전체. 하나 이상
 */
public record UserUpdateRequest(
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.") String password,
        @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.") String nickname,
        @NotEmpty(message = "권한을 하나 이상 입력해 주세요.")
        Set<@NotNull(message = "권한을 입력해 주세요.") UserRole> roles) {
}
