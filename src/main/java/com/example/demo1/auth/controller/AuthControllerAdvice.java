package com.example.demo1.auth.controller;

import com.example.demo1.auth.dto.AuthErrorResponse;
import com.example.demo1.auth.exception.LoginFailedException;
import com.example.demo1.auth.exception.TokenRefreshFailedException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthControllerAdvice {

    @ExceptionHandler(LoginFailedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public AuthErrorResponse handleLoginFailed(LoginFailedException exception) {
        return new AuthErrorResponse(HttpStatus.UNAUTHORIZED.value(), exception.getMessage());
    }

    @ExceptionHandler(TokenRefreshFailedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public AuthErrorResponse handleTokenRefreshFailed(TokenRefreshFailedException exception) {
        return new AuthErrorResponse(HttpStatus.UNAUTHORIZED.value(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AuthErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return new AuthErrorResponse(HttpStatus.BAD_REQUEST.value(), "입력값을 확인해 주세요.", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AuthErrorResponse handleInvalidFormat(HttpMessageNotReadableException exception) {
        return new AuthErrorResponse(HttpStatus.BAD_REQUEST.value(), "요청 값의 형식이 올바르지 않습니다.");
    }
}
