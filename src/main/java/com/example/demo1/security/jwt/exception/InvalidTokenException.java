package com.example.demo1.security.jwt.exception;

/**
 * 서명이 맞지 않거나, 형식이 잘못됐거나, 종류가 다른 토큰일 때 발생한다.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
