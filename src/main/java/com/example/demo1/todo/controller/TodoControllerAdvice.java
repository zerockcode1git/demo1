package com.example.demo1.todo.controller;

import com.example.demo1.todo.dto.TodoErrorResponse;
import com.example.demo1.todo.exception.TodoNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = TodoController.class)
public class TodoControllerAdvice {

    private static final String INVALID_FORMAT_MESSAGE = "요청 값의 형식이 올바르지 않습니다.";

    @ExceptionHandler(TodoNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public TodoErrorResponse handleNotFound(TodoNotFoundException exception) {
        return new TodoErrorResponse(HttpStatus.NOT_FOUND.value(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public TodoErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            // ?page=abc 같은 변환 실패는 기본 메시지에 내부 타입 정보가 들어 있어 노출하지 않는다.
            String message = error.isBindingFailure() ? INVALID_FORMAT_MESSAGE : error.getDefaultMessage();
            fieldErrors.putIfAbsent(error.getField(), message);
        }
        return new TodoErrorResponse(HttpStatus.BAD_REQUEST.value(), "입력값을 확인해 주세요.", fieldErrors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public TodoErrorResponse handleInvalidFormat(Exception exception) {
        return new TodoErrorResponse(HttpStatus.BAD_REQUEST.value(), INVALID_FORMAT_MESSAGE);
    }
}
