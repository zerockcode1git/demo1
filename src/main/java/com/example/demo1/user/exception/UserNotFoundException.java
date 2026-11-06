package com.example.demo1.user.exception;

/**
 * 요청한 이메일의 사용자가 없을 때 발생한다.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String email) {
        super("사용자를 찾을 수 없습니다. email=" + email);
    }
}
