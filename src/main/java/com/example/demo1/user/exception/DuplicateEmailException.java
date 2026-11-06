package com.example.demo1.user.exception;

/**
 * 이미 가입된 이메일로 등록하려 할 때 발생한다.
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("이미 사용 중인 이메일입니다. email=" + email);
    }
}
