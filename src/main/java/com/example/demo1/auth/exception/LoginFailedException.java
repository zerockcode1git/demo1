package com.example.demo1.auth.exception;

/**
 * 이메일이 없거나 비밀번호가 틀렸을 때 발생한다.
 * 어느 쪽이 틀렸는지 알려 주면 가입된 이메일을 알아낼 수 있으므로 메시지를 하나로 통일한다.
 */
public class LoginFailedException extends RuntimeException {

    public LoginFailedException() {
        super("이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}
