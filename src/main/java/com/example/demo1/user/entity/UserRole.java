package com.example.demo1.user.entity;

/**
 * 사용자 권한. DB에는 이름 그대로(ROLE_USER 등) 문자열로 저장한다.
 * Spring Security의 권한 이름 규칙(ROLE_ 접두사)과 맞춰 두었다.
 */
public enum UserRole {
    ROLE_USER,
    ROLE_MANAGER,
    ROLE_ADMIN
}
