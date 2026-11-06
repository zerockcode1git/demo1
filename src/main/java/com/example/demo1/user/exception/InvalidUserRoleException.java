package com.example.demo1.user.exception;

/**
 * 권한이 하나도 없는 상태로 만들려 할 때 발생한다.
 */
public class InvalidUserRoleException extends RuntimeException {

    public InvalidUserRoleException(String message) {
        super(message);
    }
}
